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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

/**
 * JSON-RPC 2.0 错误对象，同时是一个 {@link RuntimeException} —— 工具处理器可直接抛出它，由 {@link McpDispatcher}
 * 捕获并透传为结构化错误响应。
 *
 * <p>错误码遵循 JSON-RPC 2.0 保留区间：-32700 解析错误、-32600 无效请求、-32601 方法未找到、 -32602 无效参数、-32603 内部错误。
 */
@JsonIgnoreProperties({"localizedMessage", "cause", "stackTrace", "suppressed"})
public class McpError extends RuntimeException {

    /** 解析错误：服务端接收到无效的 JSON。 */
    public static final int PARSE_ERROR = -32700;

    /** 非法请求：JSON 不是合法的 JSON-RPC 请求对象。 */
    public static final int INVALID_REQUEST = -32600;

    /** 方法未找到：请求的 method 在分发器中无对应处理。 */
    public static final int METHOD_NOT_FOUND = -32601;

    /** 无效参数：method 存在但 params 不符合契约。 */
    public static final int INVALID_PARAMS = -32602;

    /** 内部错误：处理器执行期间抛出异常。 */
    public static final int INTERNAL_ERROR = -32603;

    private final int code;

    private final JsonNode data;

    @JsonCreator
    public McpError(
            @JsonProperty("code") int code,
            @JsonProperty("message") String message,
            @JsonProperty("data") JsonNode data) {
        super(message);
        this.code = code;
        this.data = data;
    }

    public McpError(int code, String message) {
        this(code, message, null);
    }

    public int getCode() {
        return code;
    }

    public JsonNode getData() {
        return data;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof McpError that)) {
            return false;
        }
        return code == that.code
                && Objects.equals(getMessage(), that.getMessage())
                && Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, getMessage(), data);
    }
}
