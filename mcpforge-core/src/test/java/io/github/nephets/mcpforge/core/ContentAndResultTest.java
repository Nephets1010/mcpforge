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

class ContentAndResultTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void textBlockHasTextType() {
        ContentBlock block = ContentBlock.text("hello");
        assertThat(block.type()).isEqualTo("text");
        assertThat(block.text()).isEqualTo("hello");
    }

    @Test
    void textBlockEquality() {
        assertThat(ContentBlock.text("a")).isEqualTo(ContentBlock.text("a"));
        assertThat(ContentBlock.text("a")).isNotEqualTo(ContentBlock.text("b"));
    }

    @Test
    void ofTextBuildsSingleBlockNoError() {
        CallToolResult result = CallToolResult.ofText("done");
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).text()).isEqualTo("done");
    }

    @Test
    void ofErrorMarksErrorFlag() {
        CallToolResult result = CallToolResult.ofError("boom");
        assertThat(result.isError()).isTrue();
        assertThat(result.content().get(0).text()).isEqualTo("boom");
    }

    @Test
    void errorResultWithMultipleBlocks() {
        CallToolResult result =
                new CallToolResult(
                        java.util.List.of(ContentBlock.text("a"), ContentBlock.text("b")), true);
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).hasSize(2);
    }

    @Test
    void successResultSerializesIsErrorFalse() throws Exception {
        String json = mapper.writeValueAsString(CallToolResult.ofText("ok"));
        assertThat(json).contains("\"isError\":false");
        assertThat(json).contains("\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]");
    }

    @Test
    void errorResultSerializesIsErrorTrue() throws Exception {
        String json = mapper.writeValueAsString(CallToolResult.ofError("fail"));
        assertThat(json).contains("\"isError\":true");
    }

    @Test
    void deserializationReadsErrorFlag() throws Exception {
        CallToolResult r =
                mapper.readValue(
                        "{\"content\":[{\"type\":\"text\",\"text\":\"x\"}],\"isError\":true}",
                        CallToolResult.class);
        assertThat(r.isError()).isTrue();
        assertThat(r.content().get(0).text()).isEqualTo("x");
    }

    @Test
    void resultEqualityIsValueBased() {
        assertThat(CallToolResult.ofText("a")).isEqualTo(CallToolResult.ofText("a"));
        assertThat(CallToolResult.ofText("a")).isNotEqualTo(CallToolResult.ofText("b"));
        assertThat(CallToolResult.ofText("a")).isNotEqualTo(CallToolResult.ofError("a"));
    }
}
