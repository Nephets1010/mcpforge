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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 为什么测这个：{@link ContentBlock} 是 MCP 内容协议最底层的值对象，大量出现在 {@code tools/call} 的响应里。 它的序列化形态（{@code
 * type/text} 两个字段）必须稳定，否则客户端无法解析工具返回。这里验证工厂方法、相等语义与 JSON 往返。
 */
class ContentBlockSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void textFactorySetsTypeAndText() {
        ContentBlock block = ContentBlock.text("hello");
        assertThat(block.type()).isEqualTo("text");
        assertThat(block.text()).isEqualTo("hello");
    }

    @Test
    void textFactoryNeverReturnsNullText() {
        assertThat(ContentBlock.text("x").text()).isEqualTo("x");
    }

    @Test
    void equalityIsValueBased() {
        assertThat(ContentBlock.text("a")).isEqualTo(ContentBlock.text("a"));
        assertThat(ContentBlock.text("a")).isNotEqualTo(ContentBlock.text("b"));
    }

    @Test
    void serializationWritesTypeAndText() throws Exception {
        String json = mapper.writeValueAsString(ContentBlock.text("hi"));
        assertThat(json).contains("\"type\":\"text\"");
        assertThat(json).contains("\"text\":\"hi\"");
    }

    @Test
    void deserializationReadsFields() throws Exception {
        ContentBlock block =
                mapper.readValue("{\"type\":\"text\",\"text\":\"yo\"}", ContentBlock.class);
        assertThat(block.type()).isEqualTo("text");
        assertThat(block.text()).isEqualTo("yo");
    }

    @Test
    void nullTextStillCarriesTextType() {
        // 允许空文本（例如占位内容块），但 type 必须保持 "text"，否则违反 MCP 内容契约。
        ContentBlock block = new ContentBlock("text", null);
        assertThat(block.type()).isEqualTo("text");
        assertThat(block.text()).isNull();
    }
}
