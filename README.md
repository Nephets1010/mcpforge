# mcpforge

MCP（Model Context Protocol）工具/服务锻造库：纯 Java 的 JSON-RPC 2.0 核心 + Spring Boot 自动装配，
把任意 Spring Bean 方法暴露为 MCP 工具。

## 模块

| 模块 | 职责 |
| --- | --- |
| `mcpforge-contract` | **协议契约层**：JSON-RPC 2.0 消息模型（`JsonRpcRequest`/`JsonRpcResponse`/`JsonRpcNotification`）、MCP 工具元数据（`ToolSpec`/`ToolInputSchema`）与内容块（`ContentBlock`/`CallToolResult`）、结构化错误（`McpError`）。纯 Java（仅 JDK 21 + Jackson），被 core/store/starter 共享，无 Spring 依赖。 |
| `mcpforge-store` | **工具存储层**：`ToolRegistry` 作为工具元数据与处理器的内存存储（注册表），提供注册、查重、容量上限与并发读取。依赖 contract，无 Spring 依赖。 |
| `mcpforge-core` | **核心引擎层**：`McpDispatcher` 请求分发器与 `Transport` 传输层抽象。依赖 contract（协议模型/错误）与 store（注册表），仅依赖 JDK 21、Jackson 与 slf4j。 |
| `mcpforge-starter` | **Spring Boot 自动装配层**：把 `ToolRegistry` / `McpServer` / `McpDispatcher` 暴露为 Bean（均 `@ConditionalOnMissingBean`，可被完全覆盖），应用只需注册工具即可对外提供 MCP 工具。 |
| `mcpforge-sample` | **示例应用**：注册一个 `echo` 工具并通过 `McpServer` 端到端分发，验证自动装配链路。 |

> 注意：拆分后所有类仍位于 `io.github.nephets.mcpforge.core` 包下，**全限定类名（包名 + 类名）完全不变**，
> 因此下游调用方（如 `new McpDispatcher(registry)`、`ToolSpec.of(...)`）的导入与代码无需任何改动。模块拆分只移动物理目录与 pom 依赖。

## 快速开始

```java
@SpringBootApplication
public class App {

  private static final Logger log = LoggerFactory.getLogger(App.class);

  public static void main(String[] args) {
    SpringApplication.run(App.class, args);
  }

  @Bean
  ApplicationRunner register(ToolRegistry registry, McpServer server) {
    return args -> {
      registry.register(
          ToolSpec.of("echo", "原样返回入参", new ToolInputSchema().required("message")),
          params -> params);
      String response = server.handle(
          "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"echo\",\"params\":{\"message\":\"hi\"}}");
      log.info("echo 工具调用结果：{}", response);
    };
  }
}
```

## 构建

```bash
./mvnw -B verify
```

## 架构约束

`mcpforge-core` 严禁依赖 `org.springframework.*`（CI 的 `architecture-boundaries` job 强制扫描）。

## 设计决策（why）

1. **为什么拆成 contract / store / core 三层而非一个大 core？**
   契约（消息模型、错误、工具元数据）被 store、core、starter 同时依赖；存储（注册表）被 core 的分发器与 starter 的自动装配共同依赖。
   把它们各自独立成模块，依赖方向是单向无环的 `contract ← store ← core ← starter`，任何一层都不必为了复用值对象而被迫引入无关的实现，也便于将来单独替换存储实现。

2. **为什么全限定类名在拆分后保持不变？**
   下游示例与调用方已大量直接 `import io.github.nephets.mcpforge.core.*`。保持包名与类名不变，模块拆分对使用方是零成本、零迁移的——只移动物理目录与 pom 依赖，不破坏任何公开 API。

3. **为什么 `ToolRegistry` 用「拒绝注册」而非「LRU 淘汰」来应对容量上限？**
   工具是显式注册的对外契约，静默淘汰会让某个方法突然从工具清单里消失、调用方只能收到 `METHOD_NOT_FOUND`，排查成本远高于注册时的一次显式 `IllegalStateException`。所以达到 `DEFAULT_MAX_TOOLS` 上限时拒绝并抛出，且拒绝后已注册内容不变。

4. **为什么 `JsonRpcRequest`/`JsonRpcNotification` 的 `method`/`params`/`jsonrpc` 归一化集中在 `JsonRpcContracts`？**
   `requireMethod`、`orMissing`、`orProtocolVersion` 三条规则（method 非空、params 缺省为 `MissingNode`、jsonrpc 缺省 `2.0`）在请求与通知上完全一致。集中实现避免两处各自演化出不同口径——否则一条 method 为空的消息会一路走到分发器被静默忽略，而非可定位的 `INVALID_REQUEST`。

5. **为什么 `McpError` 既是一个 `RuntimeException` 又是可序列化结构体？**
   工具处理器可直接 `throw new McpError(code, message)`，由 `McpDispatcher` 捕获并透传为结构化错误响应，省去每工具手写错误包装；同时它带 `code`/`message`/`data` 字段、可被 Jackson 序列化，保证错误以 JSON-RPC 标准形态回包。

6. **为什么 `McpDispatcher.dispatch` 把非 `McpError` 的异常统一翻译成 `INTERNAL_ERROR`？**
   处理器抛出的意外异常属于服务端内部错误，不应把原始堆栈/类名泄漏给调用方（可能暴露实现细节），也不应让单条坏消息导致整个连接/循环崩溃。翻译成 `INTERNAL_ERROR` 既隔离故障又保留可排查的错误码。

7. **为什么 `McpServer.start` 在每条消息处理入口 `handle` 内兜住所有异常？**
   `start` 的消息循环是长生命周期的，若让一条坏消息的异常逃逸，整个循环会中断、服务端静默失去响应。因此 `handle` 把所有异常（含 Jackson 解析异常、未预期运行时异常）都翻译成结构化错误 JSON，单条坏消息不影响后续消息。

8. **为什么 `Transport` 要求实现线程安全、而 `ToolRegistry` 不要求？**
   `Transport` 处在请求热路径的并发读写（多虚拟线程收发），契约层面强制线程安全（如内置测试中的 `InMemoryTransport` 用 `ConcurrentLinkedQueue` + `AtomicBoolean`）。
   `ToolRegistry` 的注册发生在上下文刷新期（单线程），运行期主要是只读的 `getHandler`/`getSpec`，因此契约只保证「单线程注册 + 并发只读」这一真实轮廓，不承诺写并发安全——避免为假想的并发写付出无谓的锁开销。

9. **为什么覆盖率门禁设为 BUNDLE LINE ≥ 0.60（core 历史更高 ≥ 0.75）而非 0？**
   没有门禁的覆盖率报告会因「无执行数据」而静默通过、比没有更危险；但定太高会逼出为达标而水出来的测试。0.60 是「纯逻辑模块确实被认真对待」与「不被凑数测试绑架」之间的务实折中，且 jacoco 的 destFile 强制落到 ASCII 路径避免中文路径下 `-javaagent` 注入失效。

## 测试

全仓 `@Test` / `@ParameterizedTest` 数量 ≥ 240，覆盖协议模型序列化、注册表容量与并发只读、分发器错误透传与并发分发、传输层契约、以及 starter 的 `handle` 四象限（请求 / 通知 / 非法 / 坏 JSON）与自动装配覆盖语义。
