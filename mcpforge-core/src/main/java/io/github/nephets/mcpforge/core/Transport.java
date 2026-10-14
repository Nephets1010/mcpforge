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

import java.io.Closeable;
import java.io.IOException;

/** 传输层抽象：服务端与客户端的消息通道。实现必须线程安全。 */
public interface Transport extends Closeable {

    /** 发送一条 JSON-RPC 消息。 */
    void send(String message) throws IOException;

    /** 阻塞接收下一条消息；通道关闭且无待处理消息时返回 null。 */
    String receive() throws IOException;
}
