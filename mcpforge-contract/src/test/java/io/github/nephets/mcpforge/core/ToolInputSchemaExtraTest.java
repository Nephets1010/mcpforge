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
 * 为什么测这个：{@link ToolInputSchema} 是流式 builder，重复声明同一属性应「覆盖」而非「重复」，否则 {@code tools/list} 会出现同名列、让客户端
 * schema 失效。这里验证属性覆盖、类型字段序列化、反序列化回填等契约层行为。
 */
class ToolInputSchemaExtraTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void propertyOverrideReplacesValue() {
        ObjectNode v1 = mapper.createObjectNode().put("type", "string");
        ObjectNode v2 = mapper.createObjectNode().put("type", "number");
        ToolInputSchema s = new ToolInputSchema().property("q", v1).property("q", v2);
        // 同名列只保留最后一次声明，避免重复键。
        assertThat(s.properties).hasSize(1);
        assertThat(s.properties.get("q")).isEqualTo(v2);
    }

    @Test
    void typeFieldSerializedAsObject() throws Exception {
        String json = mapper.writeValueAsString(new ToolInputSchema());
        assertThat(json).contains("\"type\":\"object\"");
    }

    @Test
    void deserializationReadsPropertiesAndRequired() throws Exception {
        String json =
                "{\"type\":\"object\",\"properties\":{\"q\":{\"type\":\"string\"}},\"required\":[\"q\"]}";
        ToolInputSchema s = mapper.readValue(json, ToolInputSchema.class);
        assertThat(s.properties).containsKey("q");
        assertThat(s.required).containsExactly("q");
    }

    @Test
    void emptySchemaHasEmptyCollections() {
        ToolInputSchema s = new ToolInputSchema();
        assertThat(s.properties).isEmpty();
        assertThat(s.required).isEmpty();
    }

    @Test
    void requiredPreservesOrderAndAllowsMultiple() {
        ToolInputSchema s = new ToolInputSchema().required("a", "b", "c");
        assertThat(s.required).containsExactly("a", "b", "c");
    }
}
