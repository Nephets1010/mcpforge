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

class JsonRpcNotificationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void ofSetsProtocolVersionAndFields() {
        ObjectNode params = mapper.createObjectNode().put("e", "v");
        JsonRpcNotification n = JsonRpcNotification.of("notify.me", params);
        assertThat(n.jsonrpc()).isEqualTo("2.0");
        assertThat(n.method()).isEqualTo("notify.me");
        assertThat(n.params()).isEqualTo(params);
    }

    @Test
    void nullJsonrpcDefaultsTo20() {
        JsonRpcNotification n = new JsonRpcNotification(null, "m", mapper.createObjectNode());
        assertThat(n.jsonrpc()).isEqualTo("2.0");
    }

    @Test
    void nullMethodRejected() {
        assertThatThrownBy(() -> new JsonRpcNotification("2.0", null, mapper.createObjectNode()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blankMethodRejected() {
        assertThatThrownBy(() -> new JsonRpcNotification("2.0", "\t", mapper.createObjectNode()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullParamsBecomeMissingNode() {
        JsonRpcNotification n = new JsonRpcNotification("2.0", "m", null);
        assertThat(n.params()).isEqualTo(MissingNode.getInstance());
    }

    @Test
    void providedParamsPreserved() {
        ObjectNode params = mapper.createObjectNode().put("a", 2);
        JsonRpcNotification n = new JsonRpcNotification("2.0", "m", params);
        assertThat(n.params().get("a").asInt()).isEqualTo(2);
    }

    @Test
    void serializationUsesJsonPropertyNames() throws Exception {
        ObjectNode params = mapper.createObjectNode().put("q", "x");
        String json = mapper.writeValueAsString(JsonRpcNotification.of("evt", params));
        assertThat(json).contains("\"jsonrpc\":\"2.0\"");
        assertThat(json).contains("\"method\":\"evt\"");
        assertThat(json).contains("\"params\":{\"q\":\"x\"}");
    }

    @Test
    void deserializationReadsFields() throws Exception {
        String json = "{\"jsonrpc\":\"2.0\",\"method\":\"log\",\"params\":{\"m\":1}}";
        JsonRpcNotification n = mapper.readValue(json, JsonRpcNotification.class);
        assertThat(n.method()).isEqualTo("log");
        assertThat(n.params().get("m").asInt()).isEqualTo(1);
    }

    @Test
    void roundTripPreservesValues() throws Exception {
        ObjectNode params = mapper.createObjectNode().put("k", "v");
        JsonRpcNotification original = JsonRpcNotification.of("ping", params);
        JsonRpcNotification round =
                mapper.readValue(mapper.writeValueAsString(original), JsonRpcNotification.class);
        assertThat(round).isEqualTo(original);
    }

    @Test
    void equalityIsValueBased() {
        ObjectNode p = mapper.createObjectNode();
        assertThat(JsonRpcNotification.of("a", p)).isEqualTo(JsonRpcNotification.of("a", p));
        assertThat(JsonRpcNotification.of("a", p)).isNotEqualTo(JsonRpcNotification.of("b", p));
    }
}
