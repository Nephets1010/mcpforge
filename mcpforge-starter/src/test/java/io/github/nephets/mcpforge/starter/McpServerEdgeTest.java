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

import io.github.nephets.mcpforge.core.McpError;
import io.github.nephets.mcpforge.core.ToolInputSchema;
import io.github.nephets.mcpforge.core.ToolRegistry;
import io.github.nephets.mcpforge.core.ToolSpec;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 为什么测这个：{@link McpServer#handle(String)} 是单消息处理的唯一入口，其「请求/通知/非法/坏 JSON」四象限分支决定了
 * 调用方能否拿到可预测的结构化响应。这里补齐数值 id、空白 method、缺 method 通知、id 透传等边界，以及 start 循环的并发停止语义。
 */
class McpServerEdgeTest {

    private ToolRegistry registryWithEcho() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(ToolSpec.of("echo", "echo", new ToolInputSchema()), params -> params);
        return registry;
    }

    @Test
    void numericIdIsPreservedInResponse() {
        McpServer server = new McpServer(registryWithEcho());
        String resp =
                server.handle(
                        "{\"jsonrpc\":\"2.0\",\"id\":42,\"method\":\"echo\",\"params\":{\"a\":1}}");
        assertThat(resp).contains("\"id\":42");
    }

    @Test
    void blankMethodRequestReturnsInvalidRequest() {
        McpServer server = new McpServer(new ToolRegistry());
        String resp =
                server.handle("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"  \",\"params\":{}}");
        assertThat(resp).contains("\"code\":" + McpError.INVALID_REQUEST);
    }

    @Test
    void notificationWithoutMethodReturnsInvalidRequest() {
        // 没有 method 字段、也没有 id：落入「缺 method」分支，返回结构化 INVALID_REQUEST（而非 null）。
        McpServer server = new McpServer(new ToolRegistry());
        String resp = server.handle("{\"jsonrpc\":\"2.0\"}");
        assertThat(resp).contains("\"code\":" + McpError.INVALID_REQUEST);
    }

    @Test
    void notificationWithBlankMethodReturnsParseError() {
        // 通知（无 id）不经过请求路径的 hasNonBlankMethod 预判，而是直接 treeToValue(JsonRpcNotification)，
        // 而 JsonRpcNotification 紧凑构造器对空白 method 抛 IllegalArgumentException，被 Jackson 包成
        // JsonMappingException（JsonProcessingException 子类），落入 handle() 的解析异常分支 → 返回 PARSE_ERROR。
        // 这与请求路径（先判 method 再反序列化）返回 INVALID_REQUEST 不同，属于 Jackson 反序列化包裹导致的真实行为。
        McpServer server = new McpServer(new ToolRegistry());
        String resp = server.handle("{\"jsonrpc\":\"2.0\",\"method\":\"  \"}");
        assertThat(resp).contains("\"code\":" + McpError.PARSE_ERROR);
    }

    @Test
    void responseEchoesRequestIdExactly() {
        McpServer server = new McpServer(registryWithEcho());
        String resp =
                server.handle(
                        "{\"jsonrpc\":\"2.0\",\"id\":\"demo-xyz\",\"method\":\"echo\",\"params\":{\"v\":1}}");
        assertThat(resp).contains("\"id\":\"demo-xyz\"");
        assertThat(resp).contains("\"result\":{\"v\":1}");
    }

    @Test
    void emptyObjectParamsHandled() {
        McpServer server = new McpServer(registryWithEcho());
        String resp =
                server.handle(
                        "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"echo\",\"params\":{}}");
        assertThat(resp).contains("\"result\":{}");
    }

    @Test
    void startLoopTerminatesAndStopIsSafe() throws Exception {
        // start() 在通道关闭（receive 返回 null）后正常退出并复位 running；随后再调用 stop() 应为安全幂等操作。
        McpServer server = new McpServer(registryWithEcho());
        Queue<String> incoming = new ArrayDeque<>();
        incoming.add("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"echo\",\"params\":{\"v\":1}}");
        List<String> sent = new ArrayList<>();
        AtomicBoolean exited = new AtomicBoolean(false);
        Thread loop =
                new Thread(
                        () -> {
                            try {
                                server.start(new McpServerTest.InMemoryTransport(incoming, sent));
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                            exited.set(true);
                        });
        loop.start();
        // 给循环处理那条消息的时间（此后 receive 返回 null，循环自然退出）。
        Thread.sleep(200);
        server.stop();
        loop.join(TimeUnit.SECONDS.toMillis(10));
        assertThat(exited).isTrue();
        assertThat(server.isRunning()).isFalse();
        assertThat(sent).isNotEmpty();
    }
}
