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

class ToolRegistryTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private ToolSpec spec(String name) {
        return ToolSpec.of(name, "desc", new ToolInputSchema());
    }

    private ToolHandler echo() {
        return params -> params;
    }

    @Test
    void registerStoresSpecAndHandler() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(spec("echo"), echo());
        assertThat(registry.size()).isEqualTo(1);
        assertThat(registry.has("echo")).isTrue();
        assertThat(registry.getSpec("echo").name()).isEqualTo("echo");
        assertThat(registry.getHandler("echo")).isNotNull();
    }

    @Test
    void duplicateNameRejected() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(spec("dup"), echo());
        assertThatThrownBy(() -> registry.register(spec("dup"), echo()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dup");
    }

    @Test
    void nullSpecRejected() {
        ToolRegistry registry = new ToolRegistry();
        assertThatThrownBy(() -> registry.register(null, echo()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullHandlerRejected() {
        ToolRegistry registry = new ToolRegistry();
        assertThatThrownBy(() -> registry.register(spec("x"), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void getSpecMissingReturnsNull() {
        ToolRegistry registry = new ToolRegistry();
        assertThat(registry.getSpec("missing")).isNull();
    }

    @Test
    void getHandlerMissingReturnsNull() {
        ToolRegistry registry = new ToolRegistry();
        assertThat(registry.getHandler("missing")).isNull();
    }

    @Test
    void hasMissingReturnsFalse() {
        ToolRegistry registry = new ToolRegistry();
        assertThat(registry.has("missing")).isFalse();
    }

    @Test
    void listReturnsImmutableCopy() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(spec("a"), echo());
        registry.register(spec("b"), echo());
        var list = registry.list();
        assertThat(list).hasSize(2);
        assertThatThrownBy(() -> list.add(spec("c")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void listContainsRegisteredSpecs() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(spec("a"), echo());
        registry.register(spec("b"), echo());
        assertThat(registry.list().stream().map(ToolSpec::name)).containsExactly("a", "b");
    }

    @Test
    void multipleToolsDistinct() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(spec("one"), echo());
        registry.register(spec("two"), echo());
        registry.register(spec("three"), echo());
        assertThat(registry.size()).isEqualTo(3);
        assertThat(registry.has("two")).isTrue();
    }

    @Test
    void handlerInvokedReturnsExpected() throws Exception {
        ToolRegistry registry = new ToolRegistry();
        registry.register(
                spec("add"),
                params -> mapper.createObjectNode().put("sum", params.get("a").asInt() + 1));
        var result = registry.getHandler("add").handle(mapper.createObjectNode().put("a", 4));
        assertThat(result.get("sum").asInt()).isEqualTo(5);
    }

    @Test
    void reregisterDifferentNameSucceeds() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(spec("a"), echo());
        registry.register(spec("b"), echo());
        assertThat(registry.size()).isEqualTo(2);
    }

    @Test
    void emptyRegistrySizeZero() {
        assertThat(new ToolRegistry().size()).isZero();
        assertThat(new ToolRegistry().list()).isEmpty();
    }

    // ==== 容量上限（长生命周期容器必须有界）====

    @Test
    void capacityAllowsExactlyMaxTools() {
        ToolRegistry registry = new ToolRegistry(3);
        registry.register(spec("a"), echo());
        registry.register(spec("b"), echo());
        registry.register(spec("c"), echo());
        assertThat(registry.size()).isEqualTo(3);
        assertThat(registry.maxTools()).isEqualTo(3);
    }

    @Test
    void registrationBeyondCapacityRejected() {
        ToolRegistry registry = new ToolRegistry(2);
        registry.register(spec("a"), echo());
        registry.register(spec("b"), echo());
        assertThatThrownBy(() -> registry.register(spec("c"), echo()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("上限")
                .hasMessageContaining("c");
        // 拒绝后注册表内容不变：不能因为拒绝而破坏已注册的工具。
        assertThat(registry.size()).isEqualTo(2);
        assertThat(registry.has("c")).isFalse();
        assertThat(registry.getHandler("c")).isNull();
    }

    @Test
    void reregisteringExistingNameDoesNotConsumeQuota() {
        // 先查重再判满：重名注册的失败原因必须是「已注册」而不是「已达上限」，
        // 否则错误信息会把排查方向带偏。
        ToolRegistry registry = new ToolRegistry(1);
        registry.register(spec("a"), echo());
        assertThatThrownBy(() -> registry.register(spec("a"), echo()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已注册");
    }

    @Test
    void defaultCapacityIsPositiveAndExposed() {
        ToolRegistry registry = new ToolRegistry();
        assertThat(registry.maxTools()).isEqualTo(ToolRegistry.DEFAULT_MAX_TOOLS);
        assertThat(registry.maxTools()).isPositive();
    }

    @Test
    void nonPositiveCapacityRejected() {
        assertThatThrownBy(() -> new ToolRegistry(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxTools");
        assertThatThrownBy(() -> new ToolRegistry(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
