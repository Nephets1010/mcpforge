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

/** JSON-RPC 2.0 通知：没有 {@code id}，服务端不应回包。 */
public record JsonRpcNotification(
        @JsonProperty("jsonrpc") String jsonrpc,
        @JsonProperty("method") String method,
        @JsonProperty("params") JsonNode params) {

    public JsonRpcNotification {
        method = JsonRpcContracts.requireMethod(method);
        params = JsonRpcContracts.orMissing(params);
        jsonrpc = JsonRpcContracts.orProtocolVersion(jsonrpc);
    }

    public static JsonRpcNotification of(String method, JsonNode params) {
        return new JsonRpcNotification(JsonRpcContracts.PROTOCOL_VERSION, method, params);
    }
}
