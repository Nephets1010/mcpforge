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

/**
 * 为什么测这个：{@link ToolRegistry} 是运行时长生命周期容器，真实部署下工具数量可能远超默认值。这里用批量注册验证 容量与查找在较大规模下仍正确（不丢、不串、size
 * 准确），并验证「恰好等于大容量上限」这一边界。
 */
class ToolRegistryStressTest {

    private ToolSpec spec(String name) {
        return ToolSpec.of(name, "d", new ToolInputSchema());
    }

    private ToolHandler echo() {
        return params -> params;
    }

    @Test
    void registerManyToolsCountsCorrectly() {
        ToolRegistry registry = new ToolRegistry(4096);
        int n = 500;
        for (int i = 0; i < n; i++) {
            registry.register(spec("t" + i), echo());
        }
        assertThat(registry.size()).isEqualTo(n);
        assertThat(registry.list()).hasSize(n);
    }

    @Test
    void registerUpToLargeCapacitySucceeds() {
        int cap = 1024;
        ToolRegistry registry = new ToolRegistry(cap);
        for (int i = 0; i < cap; i++) {
            registry.register(spec("c" + i), echo());
        }
        assertThat(registry.size()).isEqualTo(cap);
        assertThat(registry.maxTools()).isEqualTo(cap);
    }

    @Test
    void getHandlerResolvesEveryRegistered() {
        ToolRegistry registry = new ToolRegistry(256);
        int n = 100;
        for (int i = 0; i < n; i++) {
            registry.register(spec("h" + i), echo());
        }
        for (int i = 0; i < n; i++) {
            assertThat(registry.getHandler("h" + i)).isNotNull();
            assertThat(registry.getSpec("h" + i).name()).isEqualTo("h" + i);
        }
        // 未注册名称始终返回 null，不会越界或抛异常。
        assertThat(registry.getHandler("missing")).isNull();
    }
}
