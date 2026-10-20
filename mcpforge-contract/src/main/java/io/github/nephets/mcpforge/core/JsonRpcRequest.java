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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * JSON-RPC 2.0 请求对象。
 *
 * <p>{@code id} 为请求标识（可以是字符串或数字，通知类请求无 id）；{@code method} 为方法名； {@code params} 为参数（任意
 * JSON，缺省视为空节点）。
 */
public record JsonRpcRequest(
        @JsonProperty("jsonrpc") String jsonrpc,
        @JsonProperty("id") Object id,
        @JsonProperty("method") String method,
        @JsonProperty("params") JsonNode params) {

    public JsonRpcRequest {
        method = JsonRpcContracts.requireMethod(method);
        params = JsonRpcContracts.orMissing(params);
        jsonrpc = JsonRpcContracts.orProtocolVersion(jsonrpc);
    }

    public static JsonRpcRequest of(Object id, String method, JsonNode params) {
        return new JsonRpcRequest(JsonRpcContracts.PROTOCOL_VERSION, id, method, params);
    }
}
