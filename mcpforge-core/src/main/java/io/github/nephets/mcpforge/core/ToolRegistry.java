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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 工具注册表：维护工具元数据与处理器的映射。
 *
 * <p>同一个名称只能注册一次，重复注册抛 {@link IllegalStateException}；处理器与元数据必须成对提供。
 *
 * <p>注册表是长生命周期容器，因此带容量上限 {@link #DEFAULT_MAX_TOOLS}：达到上限时拒绝注册并抛 {@link
 * IllegalStateException}，而不是静默丢弃已有工具。之所以选择「拒绝」而非「LRU 淘汰」，是因为工具是显式注册的对外契约，
 * 静默淘汰会让某个方法突然从工具清单里消失、调用方只能收到 METHOD_NOT_FOUND，排查成本远高于注册时的一次显式失败。
 */
public final class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    /** 默认容量上限：单个注册表最多容纳的工具数量。 */
    public static final int DEFAULT_MAX_TOOLS = 1024;

    private final Map<String, ToolSpec> specs = new LinkedHashMap<>();
    private final Map<String, ToolHandler> handlers = new LinkedHashMap<>();

    private final int maxTools;

    public ToolRegistry() {
        this(DEFAULT_MAX_TOOLS);
    }

    /**
     * @param maxTools 容量上限，必须为正数
     * @throws IllegalArgumentException maxTools 小于 1 时抛出
     */
    public ToolRegistry(int maxTools) {
        if (maxTools < 1) {
            throw new IllegalArgumentException("maxTools 必须为正数，实际值: " + maxTools);
        }
        this.maxTools = maxTools;
    }

    public void register(ToolSpec spec, ToolHandler handler) {
        Objects.requireNonNull(spec, "ToolSpec 不能为 null");
        Objects.requireNonNull(handler, "ToolHandler 不能为 null");
        // 先查重再判满：重复注册已有工具不消耗配额，否则「满了之后连重复注册」都会报一个
        // 与真实原因（重名）不符的错误，误导排查方向。
        if (specs.containsKey(spec.name())) {
            IllegalStateException ex = new IllegalStateException("工具已注册: " + spec.name());
            log.error("工具重复注册被拒绝：{}", spec.name(), ex);
            throw ex;
        }
        if (specs.size() >= maxTools) {
            IllegalStateException ex =
                    new IllegalStateException("工具数量已达上限 " + maxTools + "，无法注册: " + spec.name());
            log.error("工具数量已达上限 {}，拒绝注册 {}（当前 {} 个）", maxTools, spec.name(), specs.size(), ex);
            throw ex;
        }
        specs.put(spec.name(), spec);
        handlers.put(spec.name(), handler);
        log.info("已注册工具：{}（当前共 {}/{} 个）", spec.name(), specs.size(), maxTools);
    }

    public ToolSpec getSpec(String name) {
        return specs.get(name);
    }

    public ToolHandler getHandler(String name) {
        return handlers.get(name);
    }

    public boolean has(String name) {
        return specs.containsKey(name);
    }

    public List<ToolSpec> list() {
        return List.copyOf(specs.values());
    }

    public int size() {
        return specs.size();
    }

    /** 当前容量上限，便于运维侧观测剩余配额。 */
    public int maxTools() {
        return maxTools;
    }
}
