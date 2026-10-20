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
import com.fasterxml.jackson.databind.node.MissingNode;

/**
 * JSON-RPC 2.0 消息契约的共享校验与缺省值逻辑（包私有）。
 *
 * <p>{@link JsonRpcRequest} 与 {@link JsonRpcNotification} 在「{@code method} 必填非空」「{@code params}
 * 缺省为空 节点」「协议版本缺省为 {@code 2.0}」这三条规则上完全一致。集中在此处是为了避免两处实现各自演化出不同口径 —— 例如只有请求校验 {@code method}
 * 而通知不校验，那么一条 {@code method} 为空的通知会一路走到分发器， 表现为 {@code getHandler(null)} 返回 null 被静默忽略，而不是可定位的
 * INVALID_REQUEST。
 */
final class JsonRpcContracts {

    /** JSON-RPC 2.0 协议版本字符串。 */
    static final String PROTOCOL_VERSION = "2.0";

    private JsonRpcContracts() {}

    /**
     * 校验 {@code method} 非空。
     *
     * @throws IllegalArgumentException method 为 null 或全空白时抛出
     */
    static String requireMethod(String method) {
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("JSON-RPC 消息必须包含非空的 method");
        }
        return method;
    }

    /** 把缺失的 {@code params} 归一为 {@link MissingNode}（保持 {@code params} 永不为 null 的不变式）。 */
    static JsonNode orMissing(JsonNode params) {
        return params == null ? MissingNode.getInstance() : params;
    }

    /** 把缺失的 {@code jsonrpc} 归一为 {@link #PROTOCOL_VERSION}。 */
    static String orProtocolVersion(String jsonrpc) {
        return jsonrpc == null ? PROTOCOL_VERSION : jsonrpc;
    }
}
