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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * MCP Tool 的输入 JSON Schema（仅覆盖 {@code object} 类型的最小子集）。
 *
 * <p>用流式 builder 累积属性与必填项：{@code new ToolInputSchema().property("q", ...).required("q")}。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ToolInputSchema {

    @JsonProperty("type")
    public String type = "object";

    @JsonProperty("properties")
    public Map<String, JsonNode> properties = new LinkedHashMap<>();

    @JsonProperty("required")
    public List<String> required = new ArrayList<>();

    public ToolInputSchema() {}

    /**
     * 声明一个属性。
     *
     * <p>名称与 schema 均拒绝 null：{@code properties} 是 {@link LinkedHashMap}，允许 null 键，一旦写入 null 键，序列化出的
     * {@code tools/list} 结果里会出现名为 {@code "null"} 的属性，客户端侧表现为难以定位的契约损坏； 此处提前失败，把问题暴露在注册阶段。
     */
    public ToolInputSchema property(String name, JsonNode schema) {
        properties.put(
                Objects.requireNonNull(name, "属性名不能为 null"),
                Objects.requireNonNull(schema, "属性 schema 不能为 null"));
        return this;
    }

    /**
     * 追加必填属性名。
     *
     * <p>逐个校验元素非空：{@code required} 里的空串/null 会让下游把所有请求都判为缺参， 而错误提示只会显示一个空白名称。
     */
    public ToolInputSchema required(String... names) {
        Objects.requireNonNull(names, "必填属性名数组不能为 null");
        for (String n : names) {
            if (n == null || n.isBlank()) {
                throw new IllegalArgumentException("必填属性名不能为空");
            }
            required.add(n);
        }
        return this;
    }
}
