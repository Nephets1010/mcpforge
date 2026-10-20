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

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.nephets.mcpforge.core.McpError;
import io.github.nephets.mcpforge.core.ToolInputSchema;
import io.github.nephets.mcpforge.core.ToolRegistry;
import io.github.nephets.mcpforge.core.ToolSpec;
import io.github.nephets.mcpforge.core.Transport;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpServerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private ToolRegistry registryWithEcho() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(ToolSpec.of("echo", "echo", new ToolInputSchema()), params -> params);
        return registry;
    }

    @Test
    void handleRequestReturnsResult() {
        McpServer server = new McpServer(registryWithEcho());
        String resp =
                server.handle(
                        "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"echo\",\"params\":{\"a\":1}}");
        assertThat(resp).contains("\"result\":{\"a\":1}");
    }

    @Test
    void handleUnknownMethodReturnsMethodNotFound() {
        McpServer server = new McpServer(new ToolRegistry());
        String resp =
                server.handle("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"x\",\"params\":{}}");
        assertThat(resp).contains("\"code\":" + McpError.METHOD_NOT_FOUND);
    }

    @Test
    void handleInvalidJsonReturnsParseError() {
        McpServer server = new McpServer(new ToolRegistry());
        String resp = server.handle("{not valid json");
        assertThat(resp).contains("\"code\":" + McpError.PARSE_ERROR);
    }

    @Test
    void handleNotificationReturnsNull() {
        McpServer server = new McpServer(new ToolRegistry());
        String resp = server.handle("{\"jsonrpc\":\"2.0\",\"method\":\"note\",\"params\":{}}");
        assertThat(resp).isNull();
    }

    @Test
    void handleMissingMethodReturnsInvalidRequest() {
        McpServer server = new McpServer(new ToolRegistry());
        String resp = server.handle("{\"jsonrpc\":\"2.0\",\"id\":\"1\"}");
        assertThat(resp).contains("\"code\":" + McpError.INVALID_REQUEST);
    }

    @Test
    void handleScalarJsonReturnsInvalidRequest() {
        McpServer server = new McpServer(new ToolRegistry());
        String resp = server.handle("42");
        assertThat(resp).contains("\"code\":" + McpError.INVALID_REQUEST);
    }

    @Test
    void nullJsonRejected() {
        McpServer server = new McpServer(new ToolRegistry());
        assertThatThrownBy(() -> server.handle(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void notificationInvokesRegisteredHandler() {
        ToolRegistry registry = new ToolRegistry();
        boolean[] invoked = {false};
        registry.register(
                ToolSpec.of("n", "n", new ToolInputSchema()),
                params -> {
                    invoked[0] = true;
                    return mapper.createObjectNode();
                });
        McpServer server = new McpServer(registry);
        server.handle("{\"jsonrpc\":\"2.0\",\"method\":\"n\",\"params\":{}}");
        assertThat(invoked[0]).isTrue();
    }

    @Test
    void startLoopDispatchesAllMessagesThenStops() throws IOException {
        ToolRegistry registry = registryWithEcho();
        registry.register(
                ToolSpec.of("add", "add", new ToolInputSchema()),
                params ->
                        mapper.createObjectNode()
                                .put("sum", params.get("a").asInt() + params.get("b").asInt()));
        McpServer server = new McpServer(registry);
        Queue<String> incoming = new ArrayDeque<>();
        incoming.add("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"echo\",\"params\":{\"v\":7}}");
        incoming.add(
                "{\"jsonrpc\":\"2.0\",\"id\":\"2\",\"method\":\"add\",\"params\":{\"a\":2,\"b\":3}}");
        List<String> sent = new ArrayList<>();
        InMemoryTransport transport = new InMemoryTransport(incoming, sent);
        server.start(transport);
        assertThat(server.isRunning()).isFalse();
        assertThat(sent).hasSize(2);
        assertThat(sent.get(0)).contains("\"result\":{\"v\":7}");
        assertThat(sent.get(1)).contains("\"result\":{\"sum\":5}");
    }

    @Test
    void startLoopWithImmediateCloseReturns() throws IOException {
        McpServer server = new McpServer(registryWithEcho());
        Queue<String> incoming = new ArrayDeque<>();
        List<String> sent = new ArrayList<>();
        server.start(new InMemoryTransport(incoming, sent));
        assertThat(server.isRunning()).isFalse();
        assertThat(sent).isEmpty();
    }

    @Test
    void isNotRunningBeforeStart() {
        McpServer server = new McpServer(registryWithEcho());
        assertThat(server.isRunning()).isFalse();
    }

    @Test
    void stopDoesNotThrow() {
        McpServer server = new McpServer(registryWithEcho());
        server.stop();
        assertThat(server.isRunning()).isFalse();
    }

    @Test
    void nullRegistryRejected() {
        assertThatThrownBy(() -> new McpServer(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullTransportRejectedOnStart() {
        McpServer server = new McpServer(registryWithEcho());
        assertThatThrownBy(() -> server.start(null)).isInstanceOf(NullPointerException.class);
    }

    // ==== 中断标志恢复 / 传输异常不得破坏状态机 ====

    @Test
    void startRestoresInterruptFlagWhenReceiveIsInterrupted() {
        McpServer server = new McpServer(registryWithEcho());
        List<String> sent = new ArrayList<>();
        InterruptingTransport transport = new InterruptingTransport(sent);
        try {
            assertThatThrownBy(() -> server.start(transport))
                    .isInstanceOf(InterruptedIOException.class);
            // 关键：异常照常向上抛，但中断标志必须被恢复，否则上层调度器收不到取消信号。
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            // 测试用例跑在共享线程上，必须清掉标志，避免污染同 JVM 内的后续用例。
            Thread.interrupted();
        }
        assertThat(server.isRunning()).isFalse();
        assertThat(sent).isEmpty();
    }

    @Test
    void startResetsRunningFlagWhenTransportFails() {
        McpServer server = new McpServer(registryWithEcho());
        IOException boom = new IOException("boom");
        assertThatThrownBy(() -> server.start(new FailingTransport(boom))).isSameAs(boom);
        // 抛出后服务端必须回到「未运行」，否则无法再次 start()。
        assertThat(server.isRunning()).isFalse();
    }

    static final class InterruptingTransport implements Transport {

        private final List<String> sent;

        InterruptingTransport(List<String> sent) {
            this.sent = sent;
        }

        @Override
        public void send(String message) {
            sent.add(message);
        }

        @Override
        public String receive() throws IOException {
            // 真实通道（如 InterruptibleChannel）在中断时会先清标志再抛此异常。
            Thread.interrupted();
            throw new InterruptedIOException("interrupted while receiving");
        }

        @Override
        public void close() {}
    }

    static final class FailingTransport implements Transport {

        private final IOException failure;

        FailingTransport(IOException failure) {
            this.failure = failure;
        }

        @Override
        public void send(String message) {}

        @Override
        public String receive() throws IOException {
            throw failure;
        }

        @Override
        public void close() {}
    }

    static final class InMemoryTransport implements Transport {

        private final Queue<String> incoming;

        private final List<String> sent;

        InMemoryTransport(Queue<String> incoming, List<String> sent) {
            this.incoming = incoming;
            this.sent = sent;
        }

        @Override
        public void send(String message) {
            sent.add(message);
        }

        @Override
        public String receive() {
            return incoming.poll();
        }

        @Override
        public void close() {}
    }
}
