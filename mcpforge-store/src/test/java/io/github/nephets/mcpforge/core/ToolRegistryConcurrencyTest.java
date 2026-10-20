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

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 为什么测这个：{@link ToolRegistry} 在请求热路径上会被大量虚拟线程并发「读取」（getHandler/getSpec），
 * 而注册通常发生在上下文刷新期（单线程）。这里验证「单线程完成注册后、多线程并发只读」这一真实并发轮廓下， 读取不丢工具、不抛
 * ConcurrentModificationException、结果一致。写并发不在本类的契约内（注册非线程安全）。
 */
class ToolRegistryConcurrencyTest {

    private ToolSpec spec(String name) {
        return ToolSpec.of(name, "d", new ToolInputSchema());
    }

    private ToolHandler echo() {
        return params -> params;
    }

    @Test
    void concurrentReadsAfterSingleThreadPopulation() throws Exception {
        int n = 256;
        ToolRegistry registry = new ToolRegistry(n + 16);
        // 注册在单线程完成（真实轮廓：上下文刷新期）。
        for (int i = 0; i < n; i++) {
            registry.register(spec("t" + i), echo());
        }
        assertThat(registry.size()).isEqualTo(n);

        int readers = 16;
        int perReader = 200;
        ExecutorService pool = Executors.newFixedThreadPool(readers);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger mismatches = new AtomicInteger();
        for (int r = 0; r < readers; r++) {
            pool.submit(
                    () -> {
                        try {
                            start.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        for (int i = 0; i < perReader; i++) {
                            int idx = (i * 31) % n;
                            String name = "t" + idx;
                            ToolHandler h = registry.getHandler(name);
                            ToolSpec s = registry.getSpec(name);
                            if (h == null || s == null || !s.name().equals(name)) {
                                mismatches.incrementAndGet();
                            }
                        }
                    });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        // 并发只读不丢、不串、不抛异常。
        assertThat(mismatches.get()).isZero();
    }

    @Test
    void concurrentListSnapshotsStayConsistent() throws Exception {
        int n = 128;
        ToolRegistry registry = new ToolRegistry(n + 16);
        for (int i = 0; i < n; i++) {
            registry.register(spec("l" + i), echo());
        }
        int readers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(readers);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger bad = new AtomicInteger();
        for (int r = 0; r < readers; r++) {
            pool.submit(
                    () -> {
                        try {
                            start.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        for (int k = 0; k < 100; k++) {
                            List<ToolSpec> snapshot = registry.list();
                            if (snapshot.size() != n) {
                                bad.incrementAndGet();
                            }
                        }
                    });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        assertThat(bad.get()).isZero();
    }

    @Test
    void sequentialRegistrationIsDeterministicUnderManyTools() {
        // 不假设写并发安全：以单线程顺序注册验证大规模下的正确性，作为并发只读场景的前置不变量。
        int n = 300;
        ToolRegistry registry = new ToolRegistry(n + 16);
        for (int i = 0; i < n; i++) {
            registry.register(spec("s" + i), echo());
        }
        assertThat(registry.size()).isEqualTo(n);
        for (int i = 0; i < n; i++) {
            assertThat(registry.has("s" + i)).isTrue();
        }
    }
}
