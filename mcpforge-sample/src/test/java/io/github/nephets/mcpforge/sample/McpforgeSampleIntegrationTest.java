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

import io.github.nephets.mcpforge.core.McpError;
import io.github.nephets.mcpforge.core.ToolInputSchema;
import io.github.nephets.mcpforge.core.ToolRegistry;
import io.github.nephets.mcpforge.core.ToolSpec;
import io.github.nephets.mcpforge.starter.McpServer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 为什么测这个：示例应用要证明「容器启动后仍能向自动装配的 ToolRegistry 动态注册工具并立即对外可用」。 这里在上下文加载后注册一个 add 工具，验证
 * store/core/starter 三层协作的端到端链路（注册→分发→回包）。
 */
@SpringBootTest
class McpforgeSampleIntegrationTest {

    @Autowired private McpServer mcpServer;
    @Autowired private ToolRegistry toolRegistry;

    @Test
    void dynamicToolRegistrationIsDispatchable() {
        toolRegistry.register(
                ToolSpec.of(
                        "add",
                        "两数相加",
                        new ToolInputSchema()
                                .property("a", new ObjectMapper().createObjectNode())
                                .property("b", new ObjectMapper().createObjectNode())
                                .required("a", "b")),
                params ->
                        new ObjectMapper()
                                .createObjectNode()
                                .put("sum", params.get("a").asInt() + params.get("b").asInt()));

        String resp =
                mcpServer.handle(
                        "{\"jsonrpc\":\"2.0\",\"id\":\"add-1\",\"method\":\"add\",\"params\":{\"a\":3,\"b\":4}}");
        assertThat(resp).contains("\"sum\":7");
    }

    @Test
    void knownAndUnknownToolsCoexist() {
        // 既有 echo（应用启动时注册）与未知方法应在同一次分发中各自得到正确结果。
        String ok =
                mcpServer.handle(
                        "{\"jsonrpc\":\"2.0\",\"id\":\"e\",\"method\":\"echo\",\"params\":{\"message\":\"x\"}}");
        assertThat(ok).contains("\"echo\":\"x\"");
        String missing =
                mcpServer.handle(
                        "{\"jsonrpc\":\"2.0\",\"id\":\"m\",\"method\":\"nope\",\"params\":{}}");
        assertThat(missing).contains("\"code\":" + McpError.METHOD_NOT_FOUND);
    }

    @Test
    void registeredToolAppearsInRegistryList() {
        assertThat(toolRegistry.has("echo")).isTrue();
        assertThat(toolRegistry.getSpec("echo").name()).isEqualTo("echo");
    }
}
