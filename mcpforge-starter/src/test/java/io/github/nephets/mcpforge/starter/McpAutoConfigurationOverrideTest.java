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
package io.github.nephets.mcpforge.starter;

import io.github.nephets.mcpforge.core.McpDispatcher;
import io.github.nephets.mcpforge.core.ToolRegistry;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 为什么测这个：{@link McpAutoConfiguration} 对三个 Bean 都加了 {@code @ConditionalOnMissingBean}，允许用户完全覆盖。
 * 已有测试覆盖了 ToolRegistry 的覆盖；这里补齐 McpServer / McpDispatcher 的覆盖语义，确保「自动装配不强行绑架 Bean 图」。
 */
class McpAutoConfigurationOverrideTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(McpAutoConfiguration.class));

    @Test
    void honorsUserProvidedMcpServer() {
        runner.withUserConfiguration(CustomServerConfig.class)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(McpServer.class);
                            assertThat(context.getBean(McpServer.class))
                                    .isSameAs(CustomServerConfig.SERVER);
                        });
    }

    @Test
    void honorsUserProvidedDispatcher() {
        runner.withUserConfiguration(CustomDispatcherConfig.class)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(McpDispatcher.class);
                            assertThat(context.getBean(McpDispatcher.class))
                                    .isSameAs(CustomDispatcherConfig.DISPATCHER);
                        });
    }

    @Test
    void stillRegistersRemainingBeansWhenOneOverridden() {
        // 用户只覆盖 ToolRegistry 时，McpServer 与 McpDispatcher 仍应由自动装配补齐（它们依赖覆盖后的 Registry）。
        runner.withUserConfiguration(McpAutoConfigurationTest.CustomRegistryConfig.class)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(McpServer.class);
                            assertThat(context).hasSingleBean(McpDispatcher.class);
                            assertThat(context.getBean(McpServer.class)).isNotNull();
                        });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomServerConfig {
        static final ToolRegistry REGISTRY = new ToolRegistry();
        static final McpServer SERVER = new McpServer(REGISTRY);

        @Bean
        ToolRegistry toolRegistry() {
            return REGISTRY;
        }

        @Bean
        McpServer mcpServer(ToolRegistry registry) {
            return SERVER;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomDispatcherConfig {
        static final ToolRegistry REGISTRY = new ToolRegistry();
        static final McpDispatcher DISPATCHER = new McpDispatcher(REGISTRY);

        @Bean
        ToolRegistry toolRegistry() {
            return REGISTRY;
        }

        @Bean
        McpDispatcher mcpDispatcher(ToolRegistry registry) {
            return DISPATCHER;
        }
    }
}
