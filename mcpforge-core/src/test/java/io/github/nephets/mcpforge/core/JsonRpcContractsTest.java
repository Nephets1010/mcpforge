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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 覆盖 JSON-RPC 共享契约 helper：请求与通知必须走同一套 method 校验与缺省值规则， 否则「method 为空的通知」会绕过校验、在下游变成静默忽略。 */
class JsonRpcContractsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void protocolVersionIs20() {
        assertThat(JsonRpcContracts.PROTOCOL_VERSION).isEqualTo("2.0");
    }

    @Test
    void requireMethodReturnsOriginalValue() {
        assertThat(JsonRpcContracts.requireMethod("a.b")).isEqualTo("a.b");
    }

    @Test
    void requireMethodRejectsNullAndBlank() {
        assertThatThrownBy(() -> JsonRpcContracts.requireMethod(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JsonRpcContracts.requireMethod(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JsonRpcContracts.requireMethod("\t "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void orMissingNormalizesNullToMissingNode() {
        assertThat(JsonRpcContracts.orMissing(null)).isEqualTo(MissingNode.getInstance());
    }

    @Test
    void orMissingKeepsProvidedNode() {
        JsonNode node = mapper.createObjectNode();
        assertThat(JsonRpcContracts.orMissing(node)).isSameAs(node);
    }

    @Test
    void orProtocolVersionDefaultsTo20() {
        assertThat(JsonRpcContracts.orProtocolVersion(null)).isEqualTo("2.0");
        assertThat(JsonRpcContracts.orProtocolVersion("1.0")).isEqualTo("1.0");
    }

    @Test
    void requestAndNotificationApplyTheSameMethodRule() {
        assertThatThrownBy(() -> JsonRpcRequest.of("id", "", mapper.createObjectNode()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JsonRpcNotification.of("", mapper.createObjectNode()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
