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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 为什么测这个：{@link ToolRegistry} 的查找 API（getSpec/getHandler/has/list/size）是分发器与自动装配的热路径入口。
 * 验证查找语义、顺序保持（LinkedHashMap 插入序）、不可变快照，确保调用方拿到的视图稳定可预测。
 */
class ToolRegistryLookupTest {

    private ToolSpec spec(String name) {
        return ToolSpec.of(name, "d", new ToolInputSchema());
    }

    private ToolHandler echo() {
        return params -> params;
    }

    @Test
    void getSpecReturnsRegistered() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(spec("echo"), echo());
        assertThat(registry.getSpec("echo").name()).isEqualTo("echo");
    }

    @Test
    void getHandlerReturnsRegistered() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(spec("echo"), echo());
        assertThat(registry.getHandler("echo")).isNotNull();
    }

    @Test
    void getSpecMissingIsNull() {
        assertThat(new ToolRegistry().getSpec("nope")).isNull();
    }

    @Test
    void getHandlerMissingIsNull() {
        assertThat(new ToolRegistry().getHandler("nope")).isNull();
    }

    @Test
    void hasReflectsRegistration() {
        ToolRegistry registry = new ToolRegistry();
        assertThat(registry.has("x")).isFalse();
        registry.register(spec("x"), echo());
        assertThat(registry.has("x")).isTrue();
    }

    @Test
    void listPreservesInsertionOrder() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(spec("first"), echo());
        registry.register(spec("second"), echo());
        registry.register(spec("third"), echo());
        List<String> names = registry.list().stream().map(ToolSpec::name).toList();
        assertThat(names).containsExactly("first", "second", "third");
    }

    @Test
    void listIsImmutableSnapshot() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(spec("a"), echo());
        List<ToolSpec> snapshot = registry.list();
        registry.register(spec("b"), echo());
        // 快照不受后续注册影响：调用方基于 list() 遍历时不会因并发注册而 ConcurrentModification。
        assertThat(snapshot).hasSize(1);
    }

    @Test
    void sizeReflectsRegistrations() {
        ToolRegistry registry = new ToolRegistry(10);
        assertThat(registry.size()).isZero();
        registry.register(spec("a"), echo());
        registry.register(spec("b"), echo());
        assertThat(registry.size()).isEqualTo(2);
    }

    @Test
    void maxToolsReflectsConstructor() {
        assertThat(new ToolRegistry(7).maxTools()).isEqualTo(7);
        assertThat(new ToolRegistry().maxTools()).isEqualTo(ToolRegistry.DEFAULT_MAX_TOOLS);
    }
}
