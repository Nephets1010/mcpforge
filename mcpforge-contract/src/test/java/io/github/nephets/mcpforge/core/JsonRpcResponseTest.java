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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonRpcResponseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void ofSetsProtocolVersionAndResult() {
        ObjectNode result = mapper.createObjectNode().put("ok", true);
        JsonRpcResponse resp = JsonRpcResponse.of("id-1", result);
        assertThat(resp.jsonrpc()).isEqualTo("2.0");
        assertThat(resp.id()).isEqualTo("id-1");
        assertThat(resp.result()).isEqualTo(result);
        assertThat(resp.error()).isNull();
    }

    @Test
    void ofErrorSetsErrorAndNullResult() {
        McpError error = new McpError(McpError.METHOD_NOT_FOUND, "nf");
        JsonRpcResponse resp = JsonRpcResponse.ofError("id-2", error);
        assertThat(resp.result()).isNull();
        assertThat(resp.error()).isEqualTo(error);
        assertThat(resp.error().getCode()).isEqualTo(McpError.METHOD_NOT_FOUND);
    }

    @Test
    void nullJsonrpcDefaultsTo20() {
        JsonRpcResponse resp = new JsonRpcResponse(null, 1, mapper.createObjectNode(), null);
        assertThat(resp.jsonrpc()).isEqualTo("2.0");
    }

    @Test
    void nullResultIsAllowed() {
        JsonRpcResponse resp = JsonRpcResponse.of("x", null);
        assertThat(resp.result()).isNull();
        assertThat(resp.error()).isNull();
    }

    @Test
    void nullErrorIsAllowed() {
        JsonRpcResponse resp = new JsonRpcResponse("2.0", "x", mapper.createObjectNode(), null);
        assertThat(resp.error()).isNull();
    }

    @Test
    void successSerializationOmitsError() throws Exception {
        ObjectNode result = mapper.createObjectNode().put("v", 1);
        String json = mapper.writeValueAsString(JsonRpcResponse.of("r1", result));
        assertThat(json).contains("\"result\":{\"v\":1}");
        assertThat(json).contains("\"id\":\"r1\"");
    }

    @Test
    void errorSerializationOmitsResult() throws Exception {
        McpError error = new McpError(McpError.INVALID_PARAMS, "bad");
        String json = mapper.writeValueAsString(JsonRpcResponse.ofError("r2", error));
        assertThat(json).contains("\"error\":");
        assertThat(json).contains("\"code\":-32602");
    }

    @Test
    void deserializationReadsResult() throws Exception {
        JsonRpcResponse resp =
                mapper.readValue(
                        "{\"jsonrpc\":\"2.0\",\"id\":5,\"result\":{\"n\":9}}",
                        JsonRpcResponse.class);
        assertThat(resp.id()).isEqualTo(5);
        assertThat(resp.result().get("n").asInt()).isEqualTo(9);
        assertThat(resp.error()).isNull();
    }

    @Test
    void deserializationReadsError() throws Exception {
        JsonRpcResponse resp =
                mapper.readValue(
                        "{\"jsonrpc\":\"2.0\",\"id\":5,\"error\":{\"code\":-1,\"message\":\"m\"}}",
                        JsonRpcResponse.class);
        assertThat(resp.result()).isNull();
        assertThat(resp.error().getCode()).isEqualTo(-1);
    }

    @Test
    void equalityIsValueBased() {
        ObjectNode r = mapper.createObjectNode();
        assertThat(JsonRpcResponse.of("a", r)).isEqualTo(JsonRpcResponse.of("a", r));
        assertThat(JsonRpcResponse.of("a", r)).isNotEqualTo(JsonRpcResponse.of("b", r));
    }

    @Test
    void roundTripSuccess() throws Exception {
        ObjectNode r = mapper.createObjectNode().put("x", 1);
        JsonRpcResponse original = JsonRpcResponse.of("z", r);
        JsonRpcResponse round =
                mapper.readValue(mapper.writeValueAsString(original), JsonRpcResponse.class);
        assertThat(round).isEqualTo(original);
    }
}
