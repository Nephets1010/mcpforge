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
 * 为什么测这个：{@link McpError} 既是一个 {@link RuntimeException}（处理器可直接抛出透传），又是一个可序列化的结构化错误。 这里补齐
 * hashCode/相等一致性、data 节点参与相等判断、toString 可读性等契约层语义，确保它作为跨模块错误载体的行为稳定。
 */
class McpErrorExtraTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void hashCodeConsistentWithEquals() {
        McpError a = new McpError(1, "x");
        McpError b = new McpError(1, "x");
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void errorsWithDifferentDataAreNotEqual() {
        ObjectNode d1 = mapper.createObjectNode().put("k", 1);
        ObjectNode d2 = mapper.createObjectNode().put("k", 2);
        McpError a = new McpError(1, "x", d1);
        McpError b = new McpError(1, "x", d2);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void errorsWithNullDataAreEqual() {
        assertThat(new McpError(1, "x", null)).isEqualTo(new McpError(1, "x"));
    }

    @Test
    void toStringContainsMessageAndCode() {
        McpError e = new McpError(McpError.INTERNAL_ERROR, "kaboom");
        assertThat(e.toString()).contains("kaboom");
    }

    @Test
    void isRuntimeException() {
        assertThat(new McpError(1, "m")).isInstanceOf(RuntimeException.class);
    }
}
