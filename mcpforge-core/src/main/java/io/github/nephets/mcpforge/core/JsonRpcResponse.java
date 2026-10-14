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

/** JSON-RPC 2.0 响应对象：成功时 {@code result} 有值、{@code error} 为 null；失败时相反。 */
public record JsonRpcResponse(
        @JsonProperty("jsonrpc") String jsonrpc,
        @JsonProperty("id") Object id,
        @JsonProperty("result") JsonNode result,
        @JsonProperty("error") McpError error) {

    public JsonRpcResponse {
        if (jsonrpc == null) {
            jsonrpc = "2.0";
        }
    }

    public static JsonRpcResponse of(Object id, JsonNode result) {
        return new JsonRpcResponse("2.0", id, result, null);
    }

    public static JsonRpcResponse ofError(Object id, McpError error) {
        return new JsonRpcResponse("2.0", id, null, error);
    }
}
