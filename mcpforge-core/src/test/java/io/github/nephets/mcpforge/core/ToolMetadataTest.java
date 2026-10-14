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

class ToolMetadataTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void toolSpecOfSetsFields() {
        ToolInputSchema schema = new ToolInputSchema().property("q", mapper.createObjectNode());
        ToolSpec spec = ToolSpec.of("search", "search docs", schema);
        assertThat(spec.name()).isEqualTo("search");
        assertThat(spec.description()).isEqualTo("search docs");
        assertThat(spec.inputSchema()).isEqualTo(schema);
    }

    @Test
    void emptyNameRejected() {
        assertThatThrownBy(() -> new ToolSpec("", "d", new ToolInputSchema()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blankNameRejected() {
        assertThatThrownBy(() -> new ToolSpec("  ", "d", new ToolInputSchema()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullNameRejected() {
        assertThatThrownBy(() -> new ToolSpec(null, "d", new ToolInputSchema()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullDescriptionIsAllowed() {
        ToolSpec spec = new ToolSpec("t", null, new ToolInputSchema());
        assertThat(spec.description()).isNull();
    }

    @Test
    void nullSchemaDefaultsToEmpty() {
        ToolSpec spec = new ToolSpec("t", "d", null);
        assertThat(spec.inputSchema()).isNotNull();
        assertThat(spec.inputSchema().type).isEqualTo("object");
    }

    @Test
    void toolSpecEquality() {
        ToolInputSchema s = new ToolInputSchema();
        assertThat(ToolSpec.of("a", "d", s)).isEqualTo(ToolSpec.of("a", "d", s));
        assertThat(ToolSpec.of("a", "d", s)).isNotEqualTo(ToolSpec.of("b", "d", s));
    }

    @Test
    void schemaDefaultsToObject() {
        ToolInputSchema schema = new ToolInputSchema();
        assertThat(schema.type).isEqualTo("object");
        assertThat(schema.properties).isEmpty();
        assertThat(schema.required).isEmpty();
    }

    @Test
    void propertyAddsAndReturnsSelf() {
        ObjectNode prop = mapper.createObjectNode().put("type", "string");
        ToolInputSchema schema = new ToolInputSchema().property("q", prop);
        assertThat(schema.properties).containsKey("q");
        assertThat(schema.properties.get("q")).isEqualTo(prop);
    }

    @Test
    void requiredAddsAndReturnsSelf() {
        ToolInputSchema schema = new ToolInputSchema().required("a", "b");
        assertThat(schema.required).containsExactly("a", "b");
    }

    @Test
    void propertyAndRequiredChain() {
        ObjectNode prop = mapper.createObjectNode();
        ToolInputSchema schema = new ToolInputSchema().property("name", prop).required("name");
        assertThat(schema.properties).containsKey("name");
        assertThat(schema.required).containsExactly("name");
    }

    @Test
    void schemaSerializationIncludesType() throws Exception {
        ToolInputSchema schema =
                new ToolInputSchema()
                        .property("q", mapper.createObjectNode().put("type", "string"));
        String json = mapper.writeValueAsString(schema);
        assertThat(json).contains("\"type\":\"object\"");
        assertThat(json).contains("\"properties\":{\"q\":{\"type\":\"string\"}}");
    }

    @Test
    void schemaRequiredSerializedEvenWhenEmpty() throws Exception {
        String json = mapper.writeValueAsString(new ToolInputSchema());
        assertThat(json).contains("\"required\":[]");
    }

    // ==== Schema 构建入口的参数校验（防止把 null/空白写进对外契约）====

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
    void requiredRejectsNullArray() {
        assertThatThrownBy(() -> new ToolInputSchema().required((String[]) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void requiredRejectsBlankName() {
        assertThatThrownBy(() -> new ToolInputSchema().required("ok", "  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new ToolInputSchema().required("ok").required).containsExactly("ok");
    }

    @Test
    void specSerializationUsesJsonPropertyNames() throws Exception {
        ToolInputSchema schema = new ToolInputSchema().property("q", mapper.createObjectNode());
        String json = mapper.writeValueAsString(ToolSpec.of("echo", "echo back", schema));
        assertThat(json).contains("\"name\":\"echo\"");
        assertThat(json).contains("\"description\":\"echo back\"");
        assertThat(json).contains("\"inputSchema\"");
    }
}
