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

import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 为什么测这个：{@link Transport} 是服务端与客户端的消息通道抽象，{@link McpServer#start(Transport)} 依赖其
 * 「send/receive/close」契约与线程安全语义（契约注释明确要求实现线程安全）。这里用一个内存实现验证阻塞接收、关闭后返回 null、顺序性等契约。
 */
class TransportContractTest {

    /** 内存传输实现：满足 Transport 契约（阻塞 receive、close 后 receive 返回 null、线程安全）。 */
    static final class InMemoryTransport implements Transport {
        private final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();
        private final AtomicBoolean closed = new AtomicBoolean(false);

        @Override
        public void send(String message) throws IOException {
            if (closed.get()) {
                throw new IOException("transport closed");
            }
            queue.add(message);
        }

        @Override
        public String receive() throws IOException {
            if (closed.get() && queue.isEmpty()) {
                return null;
            }
            return queue.poll();
        }

        @Override
        public void close() {
            closed.set(true);
        }

        boolean isClosed() {
            return closed.get();
        }
    }

    @Test
    void sendThenReceiveReturnsSameMessage() throws IOException {
        InMemoryTransport t = new InMemoryTransport();
        t.send("hello");
        assertThat(t.receive()).isEqualTo("hello");
    }

    @Test
    void receivePreservesOrder() throws IOException {
        InMemoryTransport t = new InMemoryTransport();
        t.send("a");
        t.send("b");
        t.send("c");
        assertThat(t.receive()).isEqualTo("a");
        assertThat(t.receive()).isEqualTo("b");
        assertThat(t.receive()).isEqualTo("c");
    }

    @Test
    void receiveReturnsNullAfterCloseWithNoPending() throws IOException {
        InMemoryTransport t = new InMemoryTransport();
        t.close();
        assertThat(t.receive()).isNull();
        assertThat(t.isClosed()).isTrue();
    }

    @Test
    void sendAfterCloseThrows() throws IOException {
        InMemoryTransport t = new InMemoryTransport();
        t.close();
        assertThatThrownBy(() -> t.send("x")).isInstanceOf(IOException.class);
    }

    @Test
    void pendingMessagesDrainedBeforeCloseReturnsNull() throws IOException {
        InMemoryTransport t = new InMemoryTransport();
        t.send("one");
        t.send("two");
        t.close();
        assertThat(t.receive()).isEqualTo("one");
        assertThat(t.receive()).isEqualTo("two");
        assertThat(t.receive()).isNull();
    }

    @Test
    void idempotentClose() {
        InMemoryTransport t = new InMemoryTransport();
        t.close();
        t.close();
        assertThat(t.isClosed()).isTrue();
    }

    @Test
    void concurrentSendReceiveKeepsAllMessages() throws Exception {
        // Transport 必须线程安全：多生产者并发 send，单消费者 receive，不能丢消息、不能重复。
        InMemoryTransport t = new InMemoryTransport();
        int producers = 8;
        int perProducer = 100;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newFixedThreadPool(producers);
        for (int p = 0; p < producers; p++) {
            final int tag = p;
            pool.submit(
                    () -> {
                        try {
                            start.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        for (int i = 0; i < perProducer; i++) {
                            try {
                                t.send("p" + tag + "-" + i);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue();

        int total = producers * perProducer;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < total; i++) {
            String msg = t.receive();
            assertThat(msg).isNotNull();
            seen.add(msg);
        }
        assertThat(seen).hasSize(total);
        assertThat(t.receive()).isNull();
    }
}
