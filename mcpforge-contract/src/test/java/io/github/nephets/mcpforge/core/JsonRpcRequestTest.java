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
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonRpcRequestTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void ofSetsProtocolVersionAndFields() {
        ObjectNode params = mapper.createObjectNode().put("q", "x");
        JsonRpcRequest req = JsonRpcRequest.of("id-1", "do.work", params);
        assertThat(req.jsonrpc()).isEqualTo("2.0");
        assertThat(req.id()).isEqualTo("id-1");
        assertThat(req.method()).isEqualTo("do.work");
        assertThat(req.params()).isEqualTo(params);
    }

    @Test
    void nullJsonrpcDefaultsTo20() {
        JsonRpcRequest req = new JsonRpcRequest(null, 1, "m", mapper.createObjectNode());
        assertThat(req.jsonrpc()).isEqualTo("2.0");
    }

    @Test
    void explicitJsonrpcIsPreserved() {
        JsonRpcRequest req = new JsonRpcRequest("2.0", 1, "m", mapper.createObjectNode());
        assertThat(req.jsonrpc()).isEqualTo("2.0");
    }

    @Test
    void nullMethodRejected() {
        assertThatThrownBy(() -> new JsonRpcRequest("2.0", 1, null, mapper.createObjectNode()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blankMethodRejected() {
        assertThatThrownBy(() -> new JsonRpcRequest("2.0", 1, "   ", mapper.createObjectNode()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullParamsBecomeMissingNode() {
        JsonRpcRequest req = new JsonRpcRequest("2.0", 1, "m", null);
        assertThat(req.params()).isEqualTo(MissingNode.getInstance());
        assertThat(req.params().isMissingNode()).isTrue();
    }

    @Test
    void providedParamsArePreserved() {
        ObjectNode params = mapper.createObjectNode().put("a", 1);
        JsonRpcRequest req = new JsonRpcRequest("2.0", 1, "m", params);
        assertThat(req.params().get("a").asInt()).isEqualTo(1);
    }

    @Test
    void idCanBeNullForNotifications() {
        JsonRpcRequest req = new JsonRpcRequest("2.0", null, "m", mapper.createObjectNode());
        assertThat(req.id()).isNull();
    }

    @Test
    void idCanBeNumeric() {
        JsonRpcRequest req = new JsonRpcRequest("2.0", 99, "m", mapper.createObjectNode());
        assertThat(req.id()).isEqualTo(99);
    }

    @Test
    void serializationUsesJsonPropertyNames() throws Exception {
        ObjectNode params = mapper.createObjectNode().put("q", "x");
        String json = mapper.writeValueAsString(JsonRpcRequest.of("r1", "search", params));
        assertThat(json).contains("\"jsonrpc\":\"2.0\"");
        assertThat(json).contains("\"id\":\"r1\"");
        assertThat(json).contains("\"method\":\"search\"");
        assertThat(json).contains("\"params\":{\"q\":\"x\"}");
    }

    @Test
    void deserializationReadsAllFields() throws Exception {
        String json =
                "{\"jsonrpc\":\"2.0\",\"id\":\"abc\",\"method\":\"run\",\"params\":{\"n\":3}}";
        JsonRpcRequest req = mapper.readValue(json, JsonRpcRequest.class);
        assertThat(req.id()).isEqualTo("abc");
        assertThat(req.method()).isEqualTo("run");
        assertThat(req.params().get("n").asInt()).isEqualTo(3);
    }

    @Test
    void roundTripPreservesValues() throws Exception {
        ObjectNode params = mapper.createObjectNode().put("k", "v");
        JsonRpcRequest original = JsonRpcRequest.of(7, "ping", params);
        JsonRpcRequest round =
                mapper.readValue(mapper.writeValueAsString(original), JsonRpcRequest.class);
        assertThat(round).isEqualTo(original);
        assertThat(round.params().get("k").asText()).isEqualTo("v");
    }

    @Test
    void equalityIsValueBased() {
        ObjectNode p = mapper.createObjectNode();
        assertThat(JsonRpcRequest.of("a", "m", p)).isEqualTo(JsonRpcRequest.of("a", "m", p));
        assertThat(JsonRpcRequest.of("a", "m", p)).isNotEqualTo(JsonRpcRequest.of("b", "m", p));
    }

    @Test
    void toStringContainsMethod() {
        assertThat(JsonRpcRequest.of("x", "greet", mapper.createObjectNode()).toString())
                .contains("greet");
    }
}
