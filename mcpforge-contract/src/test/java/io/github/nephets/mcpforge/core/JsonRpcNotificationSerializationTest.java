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
 * 为什么测这个：{@link JsonRpcNotification} 与请求的唯一区别是无 {@code id}、服务端不应回包。其归一化逻辑与请求一致， 同样要求 method
 * 非空。这里验证缺省值、归一化与序列化形态。
 */
class JsonRpcNotificationSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void ofSetsProtocolVersion() {
        JsonRpcNotification n = JsonRpcNotification.of("m", mapper.createObjectNode());
        assertThat(n.jsonrpc()).isEqualTo("2.0");
    }

    @Test
    void nullParamsNormalizedToMissingNode() {
        JsonRpcNotification n = JsonRpcNotification.of("m", null);
        assertThat(n.params()).isNotNull();
        assertThat(n.params().isMissingNode()).isTrue();
    }

    @Test
    void methodIsRequired() {
        assertThatThrownBy(() -> new JsonRpcNotification("2.0", "  ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("method");
    }

    @Test
    void jsonrpcDefaultsWhenNull() {
        JsonRpcNotification n = new JsonRpcNotification(null, "m", mapper.createObjectNode());
        assertThat(n.jsonrpc()).isEqualTo("2.0");
    }

    @Test
    void serializationRoundTrip() throws Exception {
        ObjectNode params = mapper.createObjectNode().put("k", "v");
        JsonRpcNotification n = JsonRpcNotification.of("evt", params);
        JsonRpcNotification back =
                mapper.readValue(mapper.writeValueAsString(n), JsonRpcNotification.class);
        assertThat(back.method()).isEqualTo("evt");
        assertThat(back.params().get("k").asText()).isEqualTo("v");
    }
}
