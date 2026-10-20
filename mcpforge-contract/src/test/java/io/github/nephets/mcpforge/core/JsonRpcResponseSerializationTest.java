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

/**
 * 为什么测这个：{@link JsonRpcResponse} 是响应协议对象，成功与失败互斥（result 有值则 error 为 null，反之亦然）。 验证工厂方法、缺省 jsonrpc
 * 与序列化形态，确保客户端能正确区分成功/失败响应。
 */
class JsonRpcResponseSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void ofSetsJsonrpcAndResult() {
        ObjectNode result = mapper.createObjectNode().put("v", 1);
        JsonRpcResponse resp = JsonRpcResponse.of("r", result);
        assertThat(resp.jsonrpc()).isEqualTo("2.0");
        assertThat(resp.result()).isEqualTo(result);
        assertThat(resp.error()).isNull();
    }

    @Test
    void ofErrorHasErrorAndNullResult() {
        JsonRpcResponse resp =
                JsonRpcResponse.ofError("r", new McpError(McpError.INVALID_PARAMS, "bad"));
        assertThat(resp.error()).isNotNull();
        assertThat(resp.error().getCode()).isEqualTo(McpError.INVALID_PARAMS);
        assertThat(resp.result()).isNull();
    }

    @Test
    void successResponseHasNullError() {
        JsonRpcResponse resp = JsonRpcResponse.of("r", mapper.createObjectNode());
        assertThat(resp.error()).isNull();
    }

    @Test
    void jsonrpcDefaultsWhenNullInConstructor() {
        JsonRpcResponse resp = new JsonRpcResponse(null, "r", mapper.createObjectNode(), null);
        assertThat(resp.jsonrpc()).isEqualTo("2.0");
    }

    @Test
    void serializationRoundTripSuccess() throws Exception {
        ObjectNode result = mapper.createObjectNode().put("x", 7);
        JsonRpcResponse resp = JsonRpcResponse.of("r", result);
        JsonRpcResponse back =
                mapper.readValue(mapper.writeValueAsString(resp), JsonRpcResponse.class);
        assertThat(back.id()).isEqualTo("r");
        assertThat(back.result().get("x").asInt()).isEqualTo(7);
    }

    @Test
    void serializationRoundTripError() throws Exception {
        JsonRpcResponse resp = JsonRpcResponse.ofError("r", new McpError(-1, "e"));
        String json = mapper.writeValueAsString(resp);
        assertThat(json).contains("\"error\"");
        JsonRpcResponse back = mapper.readValue(json, JsonRpcResponse.class);
        assertThat(back.error().getCode()).isEqualTo(-1);
    }
}
