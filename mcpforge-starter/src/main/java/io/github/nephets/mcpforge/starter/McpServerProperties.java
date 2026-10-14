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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MCP 服务端配置属性，前缀 {@code mcp.server}。
 *
 * <p>说明：当前 {@code enabled} 只被绑定、尚未接到 {@link McpAutoConfiguration} 的条件装配上 —— 把它接上去会让 {@code
 * mcp.server.enabled=false} 时容器里不再有 {@code ToolRegistry}/{@code McpServer} Bean，属于可观测的 Bean
 * 图变更，因此留待后续按破坏性变更单独处理。
 */
@ConfigurationProperties(prefix = "mcp.server")
public class McpServerProperties {

    /** 是否启用 MCP 服务端自动装配。默认 true。 */
    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
