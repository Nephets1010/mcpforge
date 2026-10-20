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
 * 为什么测这个：{@link CallToolResult} 是 {@code tools/call} 的结构化结果，{@code isError} 标志决定了调用方如何解读内容块。
 * 验证工厂方法语义、多内容块、以及 {@code isError} 在序列化后的可见性。
 */
class CallToolResultSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void ofTextWrapsSingleContentBlockAndIsNotError() {
        CallToolResult result = CallToolResult.ofText("ok");
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).text()).isEqualTo("ok");
    }

    @Test
    void ofErrorMarksErrorFlag() {
        CallToolResult result = CallToolResult.ofError("boom");
        assertThat(result.isError()).isTrue();
        assertThat(result.content().get(0).text()).isEqualTo("boom");
    }

    @Test
    void multipleContentBlocksPreserved() {
        CallToolResult result =
                new CallToolResult(
                        java.util.List.of(ContentBlock.text("a"), ContentBlock.text("b")), false);
        assertThat(result.content()).hasSize(2);
        assertThat(result.content().stream().map(ContentBlock::text)).containsExactly("a", "b");
    }

    @Test
    void isErrorFlagSurvivesSerialization() throws Exception {
        String ok = mapper.writeValueAsString(CallToolResult.ofText("x"));
        assertThat(ok).contains("\"isError\":false");
        String err = mapper.writeValueAsString(CallToolResult.ofError("y"));
        assertThat(err).contains("\"isError\":true");
    }

    @Test
    void serializationRoundTrip() throws Exception {
        CallToolResult original = CallToolResult.ofText("round");
        CallToolResult back =
                mapper.readValue(mapper.writeValueAsString(original), CallToolResult.class);
        assertThat(back.isError()).isEqualTo(original.isError());
        assertThat(back.content()).hasSize(1);
        assertThat(back.content().get(0).text()).isEqualTo("round");
    }

    @Test
    void equalityIsValueBased() {
        assertThat(CallToolResult.ofText("z")).isEqualTo(CallToolResult.ofText("z"));
        assertThat(CallToolResult.ofText("z")).isNotEqualTo(CallToolResult.ofError("z"));
    }
}
