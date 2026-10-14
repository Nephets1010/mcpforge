# mcpforge

MCP（Model Context Protocol）工具/服务锻造库：纯 Java 的 JSON-RPC 2.0 核心 + Spring Boot 自动装配，
把任意 Spring Bean 方法暴露为 MCP 工具。

## 模块

| 模块 | 说明 |
| --- | --- |
| `mcpforge-core` | 零 Spring 核心：JSON-RPC 2.0 协议模型、MCP Tool 元数据与注册表、Tool 分发器。仅依赖 JDK 21 与 Jackson。 |
| `mcpforge-starter` | Spring Boot 自动装配：把 `ToolRegistry` / `McpServer` 暴露为 Bean，应用只需注册工具即可对外提供 MCP 工具。 |
| `mcpforge-sample` | 示例应用：注册一个 `echo` 工具并通过 `McpServer` 端到端分发，验证自动装配链路。 |

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
