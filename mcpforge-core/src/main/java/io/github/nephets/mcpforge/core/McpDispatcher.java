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
package io.github.nephets.mcpforge.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * 请求分发器：把 JSON-RPC 请求路由到注册表中的工具处理器，并把异常翻译成结构化 {@link McpError}。
 *
 * <ul>
 *   <li>未知方法 → {@link McpError#METHOD_NOT_FOUND}
 *   <li>处理器抛 {@link McpError} → 透传其 code/message
 *   <li>其它异常 → {@link McpError#INTERNAL_ERROR}
 * </ul>
 */
public final class McpDispatcher {

    private static final Logger log = LoggerFactory.getLogger(McpDispatcher.class);

    private final ToolRegistry registry;
    private final ObjectMapper mapper;

    public McpDispatcher(ToolRegistry registry) {
        this(registry, new ObjectMapper());
    }

    public McpDispatcher(ToolRegistry registry, ObjectMapper mapper) {
        this.registry = Objects.requireNonNull(registry, "ToolRegistry 不能为 null");
        this.mapper = Objects.requireNonNull(mapper, "ObjectMapper 不能为 null");
    }

    public JsonRpcResponse dispatch(JsonRpcRequest request) {
        if (request == null) {
            log.warn("收到空请求，返回 INVALID_REQUEST");
            return error(null, McpError.INVALID_REQUEST, "请求为空");
        }
        ToolHandler handler = registry.getHandler(request.method());
        if (handler == null) {
            log.debug("方法未注册：{}（id={}），返回 METHOD_NOT_FOUND", request.method(), request.id());
            return error(request.id(), McpError.METHOD_NOT_FOUND, "未知方法: " + request.method());
        }
        log.info("分发请求 method={} id={}", request.method(), request.id());
        try {
            JsonNode result = handler.handle(request.params());
            return JsonRpcResponse.of(request.id(), result);
        } catch (McpError e) {
            log.warn(
                    "处理器抛 McpError：method={} id={} code={} message={}",
                    request.method(),
                    request.id(),
                    e.getCode(),
                    e.getMessage());
            return error(request.id(), e.getCode(), e.getMessage());
        } catch (RuntimeException e) {
            log.error("处理器未预期异常：method=" + request.method() + " id=" + request.id(), e);
            return error(request.id(), McpError.INTERNAL_ERROR, e.getMessage());
        }
    }

    public void dispatchNotification(JsonRpcNotification notification) {
        if (notification == null) {
            log.debug("收到空通知，忽略");
            return;
        }
        ToolHandler handler = registry.getHandler(notification.method());
        if (handler == null) {
            log.debug("通知方法未注册：{}，忽略", notification.method());
            return;
        }
        log.info("分发通知 method={}", notification.method());
        try {
            handler.handle(notification.params());
        } catch (RuntimeException e) {
            // 通知无回包，处理失败静默丢弃，但记录日志以便排查。
            log.error("通知处理异常（已忽略）：method=" + notification.method(), e);
        }
    }

    private JsonRpcResponse error(Object id, int code, String message) {
        return JsonRpcResponse.ofError(id, new McpError(code, message));
    }

    public ObjectMapper mapper() {
        return mapper;
    }
}
