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
package io.github.nephets.mcpforge.sample;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.nephets.mcpforge.core.ToolInputSchema;
import io.github.nephets.mcpforge.core.ToolRegistry;
import io.github.nephets.mcpforge.core.ToolSpec;
import io.github.nephets.mcpforge.starter.McpServer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * 示例应用：注册一个 {@code echo} 工具（把入参原样返回），并通过 {@link McpServer#handle(String)} 做一次端到端调用， 验证「自动装配
 * ToolRegistry → 注册工具 → 分发」整条链路可用。
 */
@SpringBootApplication
public class McpforgeSampleApplication {

    private static final Logger log = LoggerFactory.getLogger(McpforgeSampleApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(McpforgeSampleApplication.class, args);
    }

    @Bean
    CommandLineRunner registerAndDemoTools(ToolRegistry registry, McpServer server) {
        return args -> {
            ToolSpec echoSpec =
                    ToolSpec.of(
                            "echo",
                            "原样返回入参",
                            new ToolInputSchema()
                                    .property("message", new ObjectMapper().createObjectNode())
                                    .required("message"));
            registry.register(
                    echoSpec,
                    params -> {
                        ObjectNode out = new ObjectMapper().createObjectNode();
                        out.put("echo", params.path("message").asText());
                        return out;
                    });
            log.info("已注册工具：{}", registry.list().stream().map(ToolSpec::name).toList());

            String request =
                    "{\"jsonrpc\":\"2.0\",\"id\":\"demo-1\",\"method\":\"echo\",\"params\":{\"message\":\"你好，MCP\"}}";
            String response = server.handle(request);
            log.info("echo 工具调用结果：{}", response);
        };
    }
}
