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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 为什么测这个：容量上限是「长生命周期容器必须有界」这一设计决策的核心（见 README 设计决策）。这里覆盖边界值： 恰好等于上限、超过上限被拒、容量
 * 1、非法容量、以及「重名不消耗配额」的查重优先逻辑。
 */
class ToolRegistryCapacityTest {

    private ToolSpec spec(String name) {
        return ToolSpec.of(name, "d", new ToolInputSchema());
    }

    private ToolHandler echo() {
        return params -> params;
    }

    @Test
    void exactlyAtCapacitySucceeds() {
        ToolRegistry registry = new ToolRegistry(3);
        registry.register(spec("a"), echo());
        registry.register(spec("b"), echo());
        registry.register(spec("c"), echo());
        assertThat(registry.size()).isEqualTo(3);
        assertThat(registry.maxTools()).isEqualTo(3);
    }

    @Test
    void oneCapacityAllowsSingleTool() {
        ToolRegistry registry = new ToolRegistry(1);
        registry.register(spec("only"), echo());
        assertThat(registry.has("only")).isTrue();
        assertThat(registry.maxTools()).isEqualTo(1);
    }

    @Test
    void beyondCapacityRejectedAndStateUnchanged() {
        ToolRegistry registry = new ToolRegistry(2);
        registry.register(spec("a"), echo());
        registry.register(spec("b"), echo());
        assertThatThrownBy(() -> registry.register(spec("c"), echo()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("上限");
        assertThat(registry.size()).isEqualTo(2);
        assertThat(registry.has("c")).isFalse();
    }

    @Test
    void zeroCapacityRejected() {
        assertThatThrownBy(() -> new ToolRegistry(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxTools");
    }

    @Test
    void negativeCapacityRejected() {
        assertThatThrownBy(() -> new ToolRegistry(-5)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reregisteringExistingNameDoesNotConsumeQuota() {
        // 查重先于判满：容量 1 时重名注册必须报「已注册」而非「已达上限」，错误信息才指向真实原因。
        ToolRegistry registry = new ToolRegistry(1);
        registry.register(spec("a"), echo());
        assertThatThrownBy(() -> registry.register(spec("a"), echo()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已注册");
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void defaultMaxCapacityIsPositiveAndExposed() {
        ToolRegistry registry = new ToolRegistry();
        assertThat(registry.maxTools()).isEqualTo(ToolRegistry.DEFAULT_MAX_TOOLS);
        assertThat(registry.maxTools()).isPositive();
    }
}
