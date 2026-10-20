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

/**
 * 为什么测这个：本用例是 contract/store/core 三层拆分后的「端到端集成」验证——store 的 ToolRegistry 存工具、 contract 的
 * JsonRpcRequest/Response 承载消息、core 的 McpDispatcher 做分发，三者通过稳定全限定类名协作。 这里覆盖「JSON 字符串 → 请求对象 → 分发 →
 * 响应 → JSON 字符串」的完整链路，确保拆分没有破坏跨模块契约。
 */
class McpDispatcherIntegrationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private McpDispatcher dispatcherWithTools() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(
                ToolSpec.of("echo", "echo", new ToolInputSchema()),
                params -> mapper.createObjectNode().put("echo", params.path("message").asText()));
        registry.register(
                ToolSpec.of("add", "add", new ToolInputSchema()),
                params ->
                        mapper.createObjectNode()
                                .put("sum", params.get("a").asInt() + params.get("b").asInt()));
        registry.register(
                ToolSpec.of("fail", "fail", new ToolInputSchema()),
                params -> {
                    throw new McpError(McpError.INVALID_PARAMS, "bad");
                });
        return new McpDispatcher(registry, mapper);
    }

    @Test
    void jsonStringToResponsePipeline() throws Exception {
        McpDispatcher d = dispatcherWithTools();
        String json =
                "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"echo\",\"params\":{\"message\":\"hi\"}}";
        JsonRpcRequest req = mapper.treeToValue(mapper.readTree(json), JsonRpcRequest.class);
        JsonRpcResponse resp = d.dispatch(req);
        assertThat(resp.error()).isNull();
        assertThat(resp.result().get("echo").asText()).isEqualTo("hi");
        assertThat(mapper.writeValueAsString(resp)).contains("\"echo\":\"hi\"");
    }

    @Test
    void addToolPipelineComputesSum() {
        McpDispatcher d = dispatcherWithTools();
        JsonRpcResponse resp =
                d.dispatch(
                        JsonRpcRequest.of(
                                "r", "add", mapper.createObjectNode().put("a", 2).put("b", 5)));
        assertThat(resp.error()).isNull();
        assertThat(resp.result().get("sum").asInt()).isEqualTo(7);
    }

    @Test
    void unknownMethodPipelineReturnsMethodNotFound() {
        McpDispatcher d = dispatcherWithTools();
        JsonRpcResponse resp =
                d.dispatch(JsonRpcRequest.of("r", "ghost", mapper.createObjectNode()));
        assertThat(resp.error().getCode()).isEqualTo(McpError.METHOD_NOT_FOUND);
        assertThat(resp.result()).isNull();
    }

    @Test
    void handlerMcpErrorPipelinePreservesCode() {
        McpDispatcher d = dispatcherWithTools();
        JsonRpcResponse resp =
                d.dispatch(JsonRpcRequest.of("r", "fail", mapper.createObjectNode()));
        assertThat(resp.error().getCode()).isEqualTo(McpError.INVALID_PARAMS);
        assertThat(resp.error().getMessage()).isEqualTo("bad");
    }

    @Test
    void notificationPipelineInvokesHandler() {
        ToolRegistry registry = new ToolRegistry();
        boolean[] invoked = {false};
        registry.register(
                ToolSpec.of("evt", "evt", new ToolInputSchema()),
                params -> {
                    invoked[0] = true;
                    return mapper.createObjectNode();
                });
        McpDispatcher d = new McpDispatcher(registry, mapper);
        d.dispatchNotification(JsonRpcNotification.of("evt", mapper.createObjectNode()));
        assertThat(invoked[0]).isTrue();
    }

    @Test
    void mixedConcurrentPipelineStaysCorrect() throws Exception {
        McpDispatcher d = dispatcherWithTools();
        int threads = 8;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.atomic.AtomicInteger bad =
                new java.util.concurrent.atomic.AtomicInteger();
        for (int t = 0; t < threads; t++) {
            pool.submit(
                    () -> {
                        try {
                            start.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        for (int i = 0; i < 100; i++) {
                            JsonRpcResponse echo =
                                    d.dispatch(
                                            JsonRpcRequest.of(
                                                    "r",
                                                    "echo",
                                                    mapper.createObjectNode().put("message", "m")));
                            JsonRpcResponse add =
                                    d.dispatch(
                                            JsonRpcRequest.of(
                                                    "r",
                                                    "add",
                                                    mapper.createObjectNode()
                                                            .put("a", i)
                                                            .put("b", 1)));
                            if (echo.error() != null
                                    || add.error() != null
                                    || add.result().get("sum").asInt() != i + 1) {
                                bad.incrementAndGet();
                            }
                        }
                    });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        assertThat(bad.get()).isZero();
    }
}
