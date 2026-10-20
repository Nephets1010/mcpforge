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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 为什么测这个：{@link ToolSpec} 是工具在注册表中的唯一键（name），也是 {@code tools/list} 对外暴露的元数据。
 * 名称校验失败必须尽早（注册阶段）暴露，否则会生成一个无法被调用方寻址的「幽灵工具」。这里覆盖名称校验、inputSchema 缺省与相等语义。
 */
class ToolSpecEdgeTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void ofCreatesSpec() {
        ToolSpec spec = ToolSpec.of("calc", "calculator", new ToolInputSchema());
        assertThat(spec.name()).isEqualTo("calc");
        assertThat(spec.description()).isEqualTo("calculator");
        assertThat(spec.inputSchema()).isNotNull();
    }

    @Test
    void blankNameRejected() {
        assertThatThrownBy(() -> new ToolSpec("  ", "d", new ToolInputSchema()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("名称");
    }

    @Test
    void nullNameRejected() {
        assertThatThrownBy(() -> new ToolSpec(null, "d", new ToolInputSchema()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullInputSchemaDefaultsToEmptySchema() {
        // 允许省略输入 Schema：缺省为空 object，避免调用方每写一个工具都要 new 一个空 Schema。
        ToolSpec spec = new ToolSpec("x", "d", null);
        assertThat(spec.inputSchema()).isNotNull();
        assertThat(spec.inputSchema().properties).isEmpty();
    }

    @Test
    void descriptionCanBeNull() {
        ToolSpec spec = ToolSpec.of("x", null, new ToolInputSchema());
        assertThat(spec.description()).isNull();
    }

    @Test
    void equalityIsValueBased() {
        // 注意：ToolInputSchema 未重写 equals（按身份比较），因此相等要求引用同一 schema 实例，
        // 名称不同则必然不等。这里验证记录级别的相等语义。
        ToolInputSchema schema = new ToolInputSchema();
        ToolSpec a = ToolSpec.of("n", "d", schema);
        ToolSpec b = ToolSpec.of("n", "d", schema);
        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(ToolSpec.of("m", "d", schema));
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void serializationRoundTrip() throws Exception {
        ToolSpec spec = ToolSpec.of("echo", "echo tool", new ToolInputSchema());
        ToolSpec back = mapper.readValue(mapper.writeValueAsString(spec), ToolSpec.class);
        assertThat(back.name()).isEqualTo("echo");
        assertThat(back.description()).isEqualTo("echo tool");
    }
}
