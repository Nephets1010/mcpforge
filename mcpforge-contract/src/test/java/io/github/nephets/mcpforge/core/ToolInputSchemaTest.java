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
 * 为什么测这个：{@link ToolInputSchema} 是 {@code tools/list} 返回给客户端的 JSON Schema 子集。任何 null 键或空白必填项
 * 都会污染序列化结果、让客户端把所有请求判为缺参。这里验证流式 builder 的防御性校验与序列化形态。
 */
class ToolInputSchemaTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void defaultTypeIsObject() {
        assertThat(new ToolInputSchema().type).isEqualTo("object");
    }

    @Test
    void propertyAddsToPropertiesMap() {
        ObjectNode schema = mapper.createObjectNode().put("type", "string");
        ToolInputSchema s = new ToolInputSchema().property("q", schema);
        assertThat(s.properties).containsKey("q");
        assertThat(s.properties.get("q")).isEqualTo(schema);
    }

    @Test
    void propertyRejectsNullName() {
        assertThatThrownBy(() -> new ToolInputSchema().property(null, mapper.createObjectNode()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void propertyRejectsNullSchema() {
        assertThatThrownBy(() -> new ToolInputSchema().property("q", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void requiredAddsNames() {
        ToolInputSchema s = new ToolInputSchema().required("a", "b");
        assertThat(s.required).containsExactly("a", "b");
    }

    @Test
    void requiredRejectsNullElement() {
        assertThatThrownBy(() -> new ToolInputSchema().required("a", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必填");
    }

    @Test
    void requiredRejectsBlankElement() {
        assertThatThrownBy(() -> new ToolInputSchema().required("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fluentReturnsSameInstance() {
        ToolInputSchema s = new ToolInputSchema();
        assertThat(s.property("q", mapper.createObjectNode())).isSameAs(s);
        assertThat(s.required("q")).isSameAs(s);
    }

    @Test
    void requiredDefaultsEmpty() {
        assertThat(new ToolInputSchema().required).isEmpty();
    }

    @Test
    void serializationRoundTrip() throws Exception {
        ToolInputSchema s =
                new ToolInputSchema()
                        .property("q", mapper.createObjectNode().put("type", "string"));
        s.required("q");
        String json = mapper.writeValueAsString(s);
        assertThat(json).contains("\"type\":\"object\"");
        assertThat(json).contains("\"q\"");
        ToolInputSchema back = mapper.readValue(json, ToolInputSchema.class);
        assertThat(back.required).containsExactly("q");
    }
}
