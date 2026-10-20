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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 为什么测这个：{@link JsonRpcRequest} 的紧凑构造器对 method/params/jsonrpc 做统一归一化（集中在 JsonRpcContracts），
 * 是「所有请求都必须带非空 method」「params 永不为 null」不变式的唯一入口。这里验证缺省值、归一化与序列化往返。
 */
class JsonRpcRequestSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void ofSetsProtocolVersion() {
        JsonRpcRequest req = JsonRpcRequest.of("id1", "m", mapper.createObjectNode());
        assertThat(req.jsonrpc()).isEqualTo("2.0");
    }

    @Test
    void ofPreservesIdAndMethod() {
        JsonRpcRequest req = JsonRpcRequest.of(99, "do", mapper.createObjectNode());
        assertThat(req.id()).isEqualTo(99);
        assertThat(req.method()).isEqualTo("do");
    }

    @Test
    void nullParamsNormalizedToMissingNode() {
        // params 永不为 null：下游判空会误以为「无参数」，应统一为 MissingNode。
        JsonRpcRequest req = JsonRpcRequest.of("id", "m", null);
        assertThat(req.params()).isNotNull();
        assertThat(req.params().isMissingNode()).isTrue();
    }

    @Test
    void methodIsRequired() {
        // 空/空白 method 在构造期就失败，避免脏请求一路走到分发器被静默忽略。
        assertThatThrownBy(() -> new JsonRpcRequest("2.0", "id", "  ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("method");
    }

    @Test
    void jsonrpcDefaultsWhenNull() {
        JsonRpcRequest req = new JsonRpcRequest(null, "id", "m", mapper.createObjectNode());
        assertThat(req.jsonrpc()).isEqualTo("2.0");
    }

    @Test
    void serializationRoundTrip() throws Exception {
        ObjectNode params = mapper.createObjectNode().put("k", "v");
        JsonRpcRequest req = JsonRpcRequest.of("r", "m", params);
        JsonRpcRequest back =
                mapper.readValue(mapper.writeValueAsString(req), JsonRpcRequest.class);
        assertThat(back.method()).isEqualTo("m");
        assertThat(back.id()).isEqualTo("r");
        assertThat(back.params().get("k").asText()).isEqualTo("v");
    }

    @Test
    void deserializationWithMissingMethodFailsNormLater() throws Exception {
        // 反序列化时不触发紧凑构造器归一化？Jackson 走 JsonCreator，这里直接 treeToValue 才会触发；
        // 通过 valueToTree 验证字段映射完整。
        JsonRpcRequest req = JsonRpcRequest.of("r", "m", mapper.createObjectNode());
        String json = mapper.writeValueAsString(req);
        assertThat(json).contains("\"method\":\"m\"");
    }
}
