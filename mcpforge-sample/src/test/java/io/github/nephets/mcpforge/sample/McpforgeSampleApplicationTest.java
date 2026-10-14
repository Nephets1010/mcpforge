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

import io.github.nephets.mcpforge.core.McpError;
import io.github.nephets.mcpforge.starter.McpServer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class McpforgeSampleApplicationTest {

    @Autowired private McpServer mcpServer;

    @Test
    void contextLoadsAndRegistersEchoTool() {
        assertThat(mcpServer).isNotNull();
        String resp =
                mcpServer.handle(
                        "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"echo\",\"params\":{\"message\":\"hi\"}}");
        assertThat(resp).contains("\"echo\":\"hi\"");
    }

    @Test
    void unknownMethodReturnsMethodNotFoundError() {
        String resp =
                mcpServer.handle(
                        "{\"jsonrpc\":\"2.0\",\"id\":\"2\",\"method\":\"ghost\",\"params\":{}}");
        assertThat(resp).contains("\"code\":" + McpError.METHOD_NOT_FOUND);
    }

    @Test
    void invalidJsonReturnsParseError() {
        String resp = mcpServer.handle("not json at all");
        assertThat(resp).contains("\"code\":" + McpError.PARSE_ERROR);
    }
}
