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

import com.fasterxml.jackson.annotation.JsonProperty;

/** MCP Tool 的元数据：名称、描述与输入 Schema。名称是注册表中的唯一键。 */
public record ToolSpec(
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("inputSchema") ToolInputSchema inputSchema) {

    public ToolSpec {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tool 名称不能为空");
        }
        if (inputSchema == null) {
            inputSchema = new ToolInputSchema();
        }
    }

    public static ToolSpec of(String name, String description, ToolInputSchema inputSchema) {
        return new ToolSpec(name, description, inputSchema);
    }
}
