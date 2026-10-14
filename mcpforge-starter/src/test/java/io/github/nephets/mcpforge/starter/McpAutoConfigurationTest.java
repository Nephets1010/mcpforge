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

class McpAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(McpAutoConfiguration.class));

    @Test
    void registersToolRegistryServerAndDispatcher() {
        runner.run(
                context -> {
                    assertThat(context).hasSingleBean(ToolRegistry.class);
                    assertThat(context).hasSingleBean(McpServer.class);
                    assertThat(context).hasSingleBean(McpDispatcher.class);
                });
    }

    @Test
    void honorsUserProvidedToolRegistry() {
        runner.withUserConfiguration(CustomRegistryConfig.class)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(ToolRegistry.class);
                            assertThat(context.getBean(ToolRegistry.class))
                                    .isSameAs(CustomRegistryConfig.REGISTRY);
                        });
    }

    @Test
    void propertiesAreBound() {
        runner.withPropertyValues("mcp.server.enabled=false")
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(McpServerProperties.class);
                            assertThat(context.getBean(McpServerProperties.class).isEnabled())
                                    .isFalse();
                        });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomRegistryConfig {

        static final ToolRegistry REGISTRY = new ToolRegistry();

        @Bean
        ToolRegistry toolRegistry() {
            return REGISTRY;
        }
    }
}
