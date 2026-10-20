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

import static org.assertj.core.api.Assertions.assertThat;

class McpDispatcherTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private ToolRegistry registryWithEcho() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(ToolSpec.of("echo", "echo", new ToolInputSchema()), params -> params);
        return registry;
    }

    @Test
    void nullRequestReturnsInvalidRequestError() {
        McpDispatcher d = new McpDispatcher(new ToolRegistry());
        JsonRpcResponse resp = d.dispatch(null);
        assertThat(resp.error().getCode()).isEqualTo(McpError.INVALID_REQUEST);
        assertThat(resp.id()).isNull();
    }

    @Test
    void knownMethodDispatchesResult() {
        McpDispatcher d = new McpDispatcher(registryWithEcho());
        ObjectNode params = mapper.createObjectNode().put("v", "hi");
        JsonRpcResponse resp = d.dispatch(JsonRpcRequest.of("r1", "echo", params));
        assertThat(resp.error()).isNull();
        assertThat(resp.id()).isEqualTo("r1");
        assertThat(resp.result().get("v").asText()).isEqualTo("hi");
    }

    @Test
    void unknownMethodReturnsMethodNotFound() {
        McpDispatcher d = new McpDispatcher(new ToolRegistry());
        JsonRpcResponse resp =
                d.dispatch(JsonRpcRequest.of("r2", "nope", mapper.createObjectNode()));
        assertThat(resp.error().getCode()).isEqualTo(McpError.METHOD_NOT_FOUND);
        assertThat(resp.id()).isEqualTo("r2");
    }

    @Test
    void handlerThrowingMcpErrorIsTransparent() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(
                ToolSpec.of("fail", "fail", new ToolInputSchema()),
                params -> {
                    throw new McpError(McpError.INVALID_PARAMS, "bad param");
                });
        McpDispatcher d = new McpDispatcher(registry);
        JsonRpcResponse resp =
                d.dispatch(JsonRpcRequest.of("r3", "fail", mapper.createObjectNode()));
        assertThat(resp.error().getCode()).isEqualTo(McpError.INVALID_PARAMS);
        assertThat(resp.error().getMessage()).isEqualTo("bad param");
    }

    @Test
    void handlerThrowingRuntimeExceptionReturnsInternalError() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(
                ToolSpec.of("boom", "boom", new ToolInputSchema()),
                params -> {
                    throw new IllegalStateException("kaboom");
                });
        McpDispatcher d = new McpDispatcher(registry);
        JsonRpcResponse resp =
                d.dispatch(JsonRpcRequest.of("r4", "boom", mapper.createObjectNode()));
        assertThat(resp.error().getCode()).isEqualTo(McpError.INTERNAL_ERROR);
        assertThat(resp.error().getMessage()).isEqualTo("kaboom");
    }

    @Test
    void dispatchNotificationNullIsNoOp() {
        McpDispatcher d = new McpDispatcher(new ToolRegistry());
        d.dispatchNotification(null);
    }

    @Test
    void dispatchNotificationUnknownMethodIsNoOp() {
        McpDispatcher d = new McpDispatcher(new ToolRegistry());
        d.dispatchNotification(JsonRpcNotification.of("unknown", mapper.createObjectNode()));
    }

    @Test
    void dispatchNotificationKnownMethodInvokesHandler() {
        ToolRegistry registry = new ToolRegistry();
        boolean[] invoked = {false};
        registry.register(
                ToolSpec.of("notify", "notify", new ToolInputSchema()),
                params -> {
                    invoked[0] = true;
                    return mapper.createObjectNode();
                });
        McpDispatcher d = new McpDispatcher(registry);
        d.dispatchNotification(JsonRpcNotification.of("notify", mapper.createObjectNode()));
        assertThat(invoked[0]).isTrue();
    }

    @Test
    void dispatchNotificationHandlerErrorSwallowed() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(
                ToolSpec.of("flaky", "flaky", new ToolInputSchema()),
                params -> {
                    throw new RuntimeException("ignore me");
                });
        McpDispatcher d = new McpDispatcher(registry);
        d.dispatchNotification(JsonRpcNotification.of("flaky", mapper.createObjectNode()));
    }

    @Test
    void dispatchPreservesIdAcrossMethods() {
        McpDispatcher d = new McpDispatcher(registryWithEcho());
        JsonRpcResponse resp =
                d.dispatch(JsonRpcRequest.of(123, "echo", mapper.createObjectNode().put("x", 1)));
        assertThat(resp.id()).isEqualTo(123);
        assertThat(resp.result().get("x").asInt()).isEqualTo(1);
    }

    @Test
    void dispatchErrorHasNullResult() {
        McpDispatcher d = new McpDispatcher(new ToolRegistry());
        JsonRpcResponse resp =
                d.dispatch(JsonRpcRequest.of("e", "missing", mapper.createObjectNode()));
        assertThat(resp.result()).isNull();
        assertThat(resp.error()).isNotNull();
    }

    @Test
    void customObjectMapperIsUsed() {
        ObjectMapper custom = new ObjectMapper();
        McpDispatcher d = new McpDispatcher(registryWithEcho(), custom);
        assertThat(d.mapper()).isSameAs(custom);
    }

    @Test
    void nullRegistryRejected() {
        assertThat(
                        org.assertj.core.api.Assertions.catchThrowable(
                                        () -> new McpDispatcher(null))
                                .getClass())
                .isEqualTo(NullPointerException.class);
    }

    @Test
    void nullMapperRejected() {
        assertThat(
                        org.assertj.core.api.Assertions.catchThrowable(
                                        () -> new McpDispatcher(new ToolRegistry(), null))
                                .getClass())
                .isEqualTo(NullPointerException.class);
    }

    @Test
    void methodNotFoundMessageIncludesMethodName() {
        McpDispatcher d = new McpDispatcher(new ToolRegistry());
        JsonRpcResponse resp =
                d.dispatch(JsonRpcRequest.of("r", "ghost", mapper.createObjectNode()));
        assertThat(resp.error().getMessage()).contains("ghost");
    }

    @Test
    void successResponseHasNoError() {
        McpDispatcher d = new McpDispatcher(registryWithEcho());
        JsonRpcResponse resp =
                d.dispatch(JsonRpcRequest.of("r", "echo", mapper.createObjectNode()));
        assertThat(resp.error()).isNull();
    }
}
