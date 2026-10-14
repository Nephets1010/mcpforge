/*
 * Copyright 2026 The mcpforge Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.nephets.mcpforge.starter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.nephets.mcpforge.core.JsonRpcNotification;
import io.github.nephets.mcpforge.core.JsonRpcRequest;
import io.github.nephets.mcpforge.core.JsonRpcResponse;
import io.github.nephets.mcpforge.core.McpDispatcher;
import io.github.nephets.mcpforge.core.McpError;
import io.github.nephets.mcpforge.core.ToolRegistry;
import io.github.nephets.mcpforge.core.Transport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Objects;

/**
 * MCP 服务端：在 {@link Transport} 之上跑请求/通知循环，把 JSON-RPC 消息路由到 {@link McpDispatcher}。
 *
 * <p>{@link #handle(String)} 是无状态的单消息处理入口，便于单元测试与嵌入任意传输； {@link #start(Transport)}
 * 是阻塞的循环版，逐条接收并回包，直到通道关闭或 {@link #stop()} 被调用。
 */
public final class McpServer {

    private static final Logger log = LoggerFactory.getLogger(McpServer.class);

    private final ObjectMapper mapper;

    private final McpDispatcher dispatcher;

    private volatile boolean running = false;

    public McpServer(ToolRegistry registry) {
        this(registry, new ObjectMapper());
    }

    public McpServer(ToolRegistry registry, ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "ObjectMapper 不能为 null");
        this.dispatcher =
                new McpDispatcher(
                        Objects.requireNonNull(registry, "ToolRegistry 不能为 null"), mapper);
    }

    /**
     * 处理一条 JSON-RPC 消息并返回响应 JSON。
     *
     * <ul>
     *   <li>含 {@code id} → 视为请求，分发并返回响应
     *   <li>仅含 {@code method}（通知）→ 分发但不回包，返回 {@code null}
     *   <li>两者皆无或结构非法 → 返回 {@link McpError#INVALID_REQUEST} 错误 JSON
     *   <li>JSON 语法错误 → 返回 {@link McpError#PARSE_ERROR} 错误 JSON
     * </ul>
     *
     * <p>所有异常都在本方法内被翻译成结构化错误 JSON，不会向外逃逸：调用方（尤其是 {@link #start(Transport)}
     * 的消息循环）因此不必为单条坏消息写防御代码，也不会因为一条坏消息而丢失整个连接。
     */
    public String handle(String json) {
        Objects.requireNonNull(json, "json 不能为 null");
        log.debug("收到消息，长度={}", json.length());
        try {
            JsonNode node = mapper.readTree(json);
            if (node.has("id")) {
                if (!hasNonBlankMethod(node)) {
                    return errorJson(null, McpError.INVALID_REQUEST, "JSON-RPC 请求缺少合法的 method");
                }
                JsonRpcRequest request = mapper.treeToValue(node, JsonRpcRequest.class);
                return mapper.writeValueAsString(dispatcher.dispatch(request));
            }
            if (node.has("method")) {
                JsonRpcNotification notification =
                        mapper.treeToValue(node, JsonRpcNotification.class);
                dispatcher.dispatchNotification(notification);
                return null;
            }
            return errorJson(null, McpError.INVALID_REQUEST, "JSON-RPC 消息缺少 method 字段");
        } catch (JsonProcessingException e) {
            log.error("消息 JSON 解析失败", e);
            return errorJson(null, McpError.PARSE_ERROR, e.getMessage());
        } catch (RuntimeException e) {
            // 兜底降级：handle() 是 start() 消息循环里唯一的处理入口，若让异常逃逸，
            // 一条坏消息就会打断整个循环、让服务端静默失去响应能力（调用方只看到连接断开）。
            // 因此在此止住，降级为结构化错误响应（沿用原错误码 INVALID_REQUEST，避免改变
            // 既有调用方依赖的错误码语义），同时保留完整堆栈供排查。
            // 正常情况下 Jackson 的解析异常已在上面的 JsonProcessingException 分支处理，
            // 本分支只覆盖真正未预期的运行时故障（如自定义 ObjectMapper 的钩子抛错）。
            log.error("处理消息时发生未预期异常", e);
            return errorJson(null, McpError.INVALID_REQUEST, e.getMessage());
        }
    }

    private boolean hasNonBlankMethod(JsonNode node) {
        JsonNode method = node.get("method");
        return method != null && !method.isNull() && !method.asText().isBlank();
    }

    /**
     * 阻塞循环：逐条接收消息、分发、回包，直到通道关闭或 {@link #stop()}。
     *
     * <p>{@link Transport} 由调用方创建并持有，本方法不负责关闭它 —— 循环结束时通道可能仍可复用 （例如 {@code receive()} 返回 null
     * 仅表示对端半关闭），提前 close 会剥夺调用方继续写入的机会。 关闭责任留给创建方，用 try-with-resources 包住本方法即可。
     *
     * @throws IOException 传输层读写失败；抛出前会先复位 {@code running} 并记录日志，服务端可再次 {@code start()}
     */
    public void start(Transport transport) throws IOException {
        Objects.requireNonNull(transport, "Transport 不能为 null");
        running = true;
        log.info("McpServer 启动，开始阻塞接收消息");
        try {
            while (running) {
                String message = transport.receive();
                if (message == null) {
                    // 通道已关闭且无待处理消息，正常退出。
                    log.info("传输通道已关闭，消息循环退出");
                    break;
                }
                String response = handle(message);
                if (response != null) {
                    transport.send(response);
                }
            }
        } catch (InterruptedIOException e) {
            // 阻塞接收被中断时，底层通道会先清除线程的中断标志再抛异常。若不在此恢复，
            // 上层调度器（线程池 / 虚拟线程）将观察不到中断，任务无法被及时取消，
            // 表现为「stop() 之后线程迟迟不退出」。
            Thread.currentThread().interrupt();
            log.warn("消息循环被中断，准备退出");
            throw e;
        } catch (IOException e) {
            log.error("传输层异常，消息循环退出", e);
            throw e;
        } finally {
            running = false;
            log.info("McpServer 停止，消息循环已退出");
        }
    }

    public void stop() {
        if (!running) {
            log.debug("stop() 调用时服务端未运行，忽略");
            return;
        }
        log.info("请求停止 McpServer");
        running = false;
    }

    public boolean isRunning() {
        return running;
    }

    private String errorJson(Object id, int code, String message) {
        try {
            return mapper.writeValueAsString(
                    JsonRpcResponse.ofError(id, new McpError(code, message)));
        } catch (JsonProcessingException e) {
            // 主路径（Jackson 序列化错误对象）失败属于极罕见情况。回退时用 Jackson 单独序列化
            // message / id，保证正确转义（避免 message 含引号时产生非法 JSON 或 JSON 注入），
            // 并保留 id（原手工拼接会丢弃 id，且未转义 message）。
            try {
                String idJson = id == null ? "null" : mapper.writeValueAsString(id);
                String msgJson = mapper.writeValueAsString(message == null ? "" : message);
                return "{\"jsonrpc\":\"2.0\",\"id\":"
                        + idJson
                        + ",\"error\":{\"code\":"
                        + code
                        + ",\"message\":"
                        + msgJson
                        + "}}";
            } catch (JsonProcessingException e2) {
                log.error("序列化 JSON-RPC 错误响应失败（code={}）", code, e2);
                throw new IllegalStateException("无法序列化 JSON-RPC 错误响应", e2);
            }
        }
    }
}
