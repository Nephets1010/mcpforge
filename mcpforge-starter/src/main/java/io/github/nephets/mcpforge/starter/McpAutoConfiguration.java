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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP 服务端自动装配。
 *
 * <p>向容器贡献 {@link ToolRegistry} 与 {@link McpServer} 两个 Bean；应用只需把工具通过 {@link ToolRegistry#register}
 * 注册进去，即可对外提供 MCP 工具。两个 Bean 均带 {@link ConditionalOnMissingBean}，允许调用方完全覆盖。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(McpServerProperties.class)
public class McpAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(McpAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public ToolRegistry toolRegistry() {
        log.info("自动装配 ToolRegistry");
        return new ToolRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public McpServer mcpServer(ToolRegistry registry) {
        log.info("自动装配 McpServer");
        return new McpServer(registry);
    }

    @Bean
    @ConditionalOnMissingBean
    public McpDispatcher mcpDispatcher(ToolRegistry registry) {
        log.info("自动装配 McpDispatcher");
        return new McpDispatcher(registry);
    }
}
