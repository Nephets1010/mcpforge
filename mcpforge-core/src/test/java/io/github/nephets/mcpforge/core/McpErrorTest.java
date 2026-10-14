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

class McpErrorTest {

    @Test
    void parseErrorConstant() {
        assertThat(McpError.PARSE_ERROR).isEqualTo(-32700);
    }

    @Test
    void invalidRequestConstant() {
        assertThat(McpError.INVALID_REQUEST).isEqualTo(-32600);
    }

    @Test
    void methodNotFoundConstant() {
        assertThat(McpError.METHOD_NOT_FOUND).isEqualTo(-32601);
    }

    @Test
    void invalidParamsConstant() {
        assertThat(McpError.INVALID_PARAMS).isEqualTo(-32602);
    }

    @Test
    void internalErrorConstant() {
        assertThat(McpError.INTERNAL_ERROR).isEqualTo(-32603);
    }

    @Test
    void constructorStoresCodeAndMessage() {
        McpError error = new McpError(42, "boom");
        assertThat(error.getCode()).isEqualTo(42);
        assertThat(error.getMessage()).isEqualTo("boom");
        assertThat(error.getData()).isNull();
    }

    @Test
    void constructorWithDataStoresData() {
        ObjectMapper mapper = new ObjectMapper();
        var data = mapper.createObjectNode().put("field", "q");
        McpError error = new McpError(7, "bad", data);
        assertThat(error.getData()).isEqualTo(data);
        assertThat(error.getData().get("field").asText()).isEqualTo("q");
    }

    @Test
    void nullMessageIsAllowed() {
        McpError error = new McpError(1, null);
        assertThat(error.getMessage()).isNull();
        assertThat(error.getCode()).isEqualTo(1);
    }

    @Test
    void equalityIsValueBased() {
        assertThat(new McpError(1, "a")).isEqualTo(new McpError(1, "a"));
        assertThat(new McpError(1, "a")).isNotEqualTo(new McpError(2, "a"));
        assertThat(new McpError(1, "a")).isNotEqualTo(new McpError(1, "b"));
    }

    @Test
    void serializationIncludesCodeAndMessage() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(new McpError(-32601, "nf"));
        assertThat(json).contains("\"code\":-32601");
        assertThat(json).contains("\"message\":\"nf\"");
    }

    @Test
    void deserializationReadsFields() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        McpError error = mapper.readValue("{\"code\":-1,\"message\":\"m\"}", McpError.class);
        assertThat(error.getCode()).isEqualTo(-1);
        assertThat(error.getMessage()).isEqualTo("m");
        assertThat(error.getData()).isNull();
    }

    @Test
    void deserializationReadsDataNode() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        McpError error =
                mapper.readValue(
                        "{\"code\":-2,\"message\":\"m\",\"data\":{\"k\":5}}", McpError.class);
        assertThat(error.getData().get("k").asInt()).isEqualTo(5);
    }
}
