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
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 为什么测这个：{@link McpDispatcher#dispatch(JsonRpcRequest)} 在请求热路径上会被大量虚拟线程并发调用，
 * 且分发器是无状态、共享单例。这里验证并发分发下结果正确路由、异常被隔离（一个工具抛错不影响其它工具）、无数据串扰。
 */
class McpDispatcherConcurrencyTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void concurrentDispatchRoutesCorrectly() throws Exception {
        ToolRegistry registry = new ToolRegistry();
        for (int i = 0; i < 16; i++) {
            final int id = i;
            registry.register(
                    ToolSpec.of("tool" + id, "t", new ToolInputSchema()),
                    params -> mapper.createObjectNode().put("id", id));
        }
        McpDispatcher dispatcher = new McpDispatcher(registry);

        int threads = 16;
        int perThread = 200;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger mismatches = new AtomicInteger();
        for (int t = 0; t < threads; t++) {
            pool.submit(
                    () -> {
                        try {
                            start.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        for (int i = 0; i < perThread; i++) {
                            int target = i % 16;
                            ObjectNode params = mapper.createObjectNode();
                            JsonRpcResponse resp =
                                    dispatcher.dispatch(
                                            JsonRpcRequest.of("r", "tool" + target, params));
                            if (resp.error() != null || resp.result().get("id").asInt() != target) {
                                mismatches.incrementAndGet();
                            }
                        }
                    });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(mismatches.get()).isZero();
    }

    @Test
    void concurrentDispatchWithErrorsStaysIsolated() throws Exception {
        ToolRegistry registry = new ToolRegistry();
        registry.register(
                ToolSpec.of("bad", "b", new ToolInputSchema()),
                params -> {
                    throw new McpError(McpError.INVALID_PARAMS, "nope");
                });
        registry.register(ToolSpec.of("good", "g", new ToolInputSchema()), params -> params);
        McpDispatcher dispatcher = new McpDispatcher(registry);

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger goodOk = new AtomicInteger();
        AtomicInteger badErr = new AtomicInteger();
        for (int t = 0; t < threads; t++) {
            pool.submit(
                    () -> {
                        try {
                            start.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        for (int i = 0; i < 100; i++) {
                            JsonRpcResponse good =
                                    dispatcher.dispatch(
                                            JsonRpcRequest.of(
                                                    "r", "good", mapper.createObjectNode()));
                            if (good.error() == null) {
                                goodOk.incrementAndGet();
                            }
                            JsonRpcResponse bad =
                                    dispatcher.dispatch(
                                            JsonRpcRequest.of(
                                                    "r", "bad", mapper.createObjectNode()));
                            if (bad.error() != null
                                    && bad.error().getCode() == McpError.INVALID_PARAMS) {
                                badErr.incrementAndGet();
                            }
                        }
                    });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        // 一个工具抛错不能拖垮另一个：good 始终成功，bad 始终返回结构化错误。
        assertThat(goodOk.get()).isEqualTo(threads * 100);
        assertThat(badErr.get()).isEqualTo(threads * 100);
    }

    @Test
    void concurrentNotificationDispatchNoInterference() throws Exception {
        ToolRegistry registry = new ToolRegistry();
        AtomicInteger count = new AtomicInteger();
        registry.register(
                ToolSpec.of("evt", "e", new ToolInputSchema()),
                params -> {
                    count.incrementAndGet();
                    return mapper.createObjectNode();
                });
        McpDispatcher dispatcher = new McpDispatcher(registry);

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        for (int t = 0; t < threads; t++) {
            pool.submit(
                    () -> {
                        try {
                            start.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        for (int i = 0; i < 50; i++) {
                            dispatcher.dispatchNotification(
                                    JsonRpcNotification.of("evt", mapper.createObjectNode()));
                        }
                    });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(count.get()).isEqualTo(threads * 50);
    }
}
