# Spring AI Assistant

Java 21、Spring Boot 4.1.1、Spring AI BOM 2.0.0 的最小同步与 SSE ChatClient 纵向切片。工程有意保留 Spring AI 2.0.0；2.0.1 升级说明包含迁移影响，本次不做依赖升级。

## 组件职责

- Spring Boot / Spring AI 自动配置：读取供应商配置，创建 `ChatModel` 和 prototype `ChatClient.Builder`，负责底层对象装配，不决定应用提示词策略。
- `ChatModel`：模型能力的底层 Spring AI 抽象，接收 `Prompt`，提供同步 `ChatResponse` 或流式 `Flux<ChatResponse>`；生产环境由 OpenAI starter 实现，测试环境由确定性替身实现。
- `ChatClient`：由应用配置组合根使用 `builder.defaultSystem(...)`、`builder.defaultOptions(...)` 和 `builder.defaultTools(...)` 构建，统一承载默认提示词、通用生成策略与显式工具白名单；同步链路读取完整 `ChatResponse`，流式链路仍只投影为 `Flux<String>`。

生命周期边界：自动配置的可变 `ChatClient.Builder` 是 prototype，每个注入点获得独立实例，避免一个客户端的默认值污染另一个客户端；本应用在配置类中只构建一个带默认 system prompt 的 `ChatClient` Bean，并由应用服务复用；生产 `ChatModel` 由供应商自动配置管理，测试 `ChatModel` 则由每个测试或测试上下文单独创建。

调用链：

```text
POST /api/assistant/chat
  → AssistantController
  → AssistantService
  → ChatClient.prompt().user(...).call().chatResponse()
  → ChatModel
```

`call()` 只选择同步调用模式，真正调用由后续 content()、chatResponse() 等终结操作触发，随后可以选择响应投影；`content()` 是其中最窄的投影，只提取模型回复文本。同步链路会等待完整模型响应，因此 Servlet 请求线程可能阻塞。流式链路通过 `Flux.defer` 将 Prompt 构造和 `stream().content()` 推迟到订阅时，生产代码不调用 `block()`、`collectList()` 或 `subscribe()`，因此分片、错误和取消信号可以沿 Reactor 链传播。

“配置一致”不等于“执行模型一致”：同步和流式请求共享同一个 `ChatClient` 默认 system prompt，但前者等待单个完整结果，后者依赖 Reactive 流的订阅、背压、取消和错误信号。

完整同步响应的映射链及依赖边界：

```text
ChatResponse (Spring AI)
  → SpringAiChatResponseMapper（允许依赖 Spring AI + 应用值对象）
  → AssistantAnswer / TokenUsage（只依赖 JDK）
  → ChatReply / TokenUsageReply（只依赖应用值对象，不依赖 Spring AI）
  → JSON（不暴露 Java 或供应商原生类型）
```

## 只读工具边界

当前唯一暴露给模型的工具是 `lookup_policy(topic)`，由 `AssistantPolicyTools.lookupPolicy` 使用 `@Tool` 和 `@ToolParam` 声明。配置组合根创建一个明确实例并调用 `defaultTools(policyTools)`；工具类不是组件，不通过组件扫描自动暴露其他方法。

| 工具 | 允许的 topic | 返回语义 |
| --- | --- | --- |
| `lookup_policy` | `stream-timeout` | 当前首个文本信号及相邻文本信号的最大等待时间 |
| `lookup_policy` | `max-output-tokens` | 当前同步与流式 Prompt 共用的 `maxTokens` |
| `lookup_policy` | `privacy` | 该查询本身仅在进程内只读执行，不访问网络、写文件或访问数据库 |

工具名称和白名单是稳定契约。空白 topic 在读取映射前失败；未知 topic 返回明确的白名单错误；模型请求未注册的工具名称时，Spring AI 工具管理器 fail-closed，不会退化到任意方法执行。工具描述只说明查询能力和输入，不承担鉴权或授权逻辑。

Spring AI 2.0 的调用链为：

```text
用户
  → ChatClient / ToolCallingAdvisor
  → 模型返回 lookup_policy 工具请求
  → ToolCallingManager 调用 AssistantPolicyTools
  → 工具结果追加为 ToolResponseMessage
  → 同一轮消息历史再次发送给模型
  → 模型最终回答
```

循环由 Spring AI 2.0 自动注册的 `ToolCallingAdvisor` 管理，应用没有自行实现第二套循环。本工程没有配置 Chat Memory，因此工具请求和结果只服务于当前调用，不声明跨请求记忆；也没有增加自定义 Advisor、写操作、在线查询或其他副作用工具。

## 配置边界

仓库中的 `application.properties` 只保留环境变量占位符：

| 环境变量 | 配置归属 | 用途 |
| --- | --- | --- |
| `ASSISTANT_SYSTEM_PROMPT` | 应用策略 | 所有同步和流式请求共享的默认系统指令；未提供时使用仓库中的非敏感默认文本 |
| `ASSISTANT_STREAM_SIGNAL_TIMEOUT` | 应用策略 | 文本流的逐信号超时，默认 `30s`；必须大于零 |
| `ASSISTANT_MAX_OUTPUT_TOKENS` | 应用策略 | 同步和流式请求共享的最大输出 Token 数，默认 `1024`；必须是正整数 |
| `OPENAI_MODEL` | provider | 部署选择的模型名称 |
| `OPENAI_API_KEY` | provider 凭据 | 从秘密管理系统注入的 API key |
| `OPENAI_BASE_URL` | transport/provider | OpenAI 或兼容服务的基础地址 |

system prompt 的组合优先级为：外部 `ASSISTANT_SYSTEM_PROMPT` 覆盖配置文件的非敏感默认值 → `AssistantChatConfiguration` 将绑定结果固化为 `ChatClient` 默认 system message → 请求级 `.system(...)` 如被显式使用则覆盖客户端默认值。当前应用服务只设置 `.user(...)`，因此同步和流式调用都继承同一个应用默认 system prompt。

最大输出 Token 的所有权属于应用生成策略：外部 `ASSISTANT_MAX_OUTPUT_TOKENS` 覆盖默认值 `1024` → `AssistantGenerationProperties` 绑定并校验正数 → 组合根用 `ChatOptions.builder().maxTokens(...)` 写入 `ChatClient.defaultOptions(...)` → 同步与流式 Prompt 继承同一个 `maxTokens`。Controller 和 Service 不绑定、复制或解释这个配置。本工程只治理 Spring AI 通用的 `maxTokens`，不增加 `temperature`，也不引入 Tool、Advisor 或 Memory；具体供应商如何表达或限制该选项仍由对应 `ChatModel` 适配器负责。

`assistant.stream.signal-timeout` 使用 Spring `Duration` 格式，例如 `750ms`、`5s` 或 `1m`。它是逐文本分片超时，而不是整条响应的总时长：订阅后等待首个 `onNext` 文本分片不能超过该值；每收到一个文本分片后，计时器重新开始，相邻两个 `onNext` 的间隔也不能超过该值。若 `onError` 或 `onComplete` 更早到达，流立即终止，不继续等待超时。超时产生的 `TimeoutException` 与其他模型错误统一包装为 `AssistantModelException`；不自动重试。

PowerShell 在线启动示例：

```powershell
$env:ASSISTANT_SYSTEM_PROMPT='Answer accurately and concisely.'
$env:ASSISTANT_STREAM_SIGNAL_TIMEOUT='30s'
$env:ASSISTANT_MAX_OUTPUT_TOKENS='1024'
$env:OPENAI_API_KEY='<secret-from-manager>'
$env:OPENAI_MODEL='<model-name>'
$env:OPENAI_BASE_URL='<provider-base-url>'
mvn spring-boot:run
```

只有 `OPENAI_API_KEY`、`OPENAI_MODEL`、`OPENAI_BASE_URL` 三个 OpenAI 变量均已由可信部署环境注入时才应在线启动。不要把真实值写入源码、配置文件、命令历史、日志或提交记录。

## HTTP 调用

同步调用：

```powershell
curl.exe -X POST http://localhost:8080/api/assistant/chat `
  -H 'Content-Type: application/json' `
  -d '{"message":"Explain virtual threads"}'
```

完整元数据响应示例：

```json
{
  "content": "Virtual threads are lightweight JVM-managed threads.",
  "responseId": "response-123",
  "model": "gpt-example",
  "usage": {
    "promptTokens": 12,
    "completionTokens": 7,
    "totalTokens": 19
  }
}
```

兼容策略：原有必填 `content` 字段保持不变；`responseId`、`model` 和 `usage` 是新增的可空字段，旧客户端应忽略未知字段。供应商未提供元数据时返回显式 `null`：

```json
{
  "content": "Answer without provider metadata.",
  "responseId": null,
  "model": null,
  "usage": null
}
```

`null` 表示未知。未知 token 绝不改写为 `0`；数字 `0` 只表示非空供应商 usage 明确报告了零。Spring AI 缺省的 `EmptyUsage` 虽然 getter 返回零，但映射边界会把整个对象识别为未知。

SSE 流式调用：

```powershell
curl.exe -N -X POST http://localhost:8080/api/assistant/stream `
  -H 'Content-Type: application/json' `
  -H 'Accept: text/event-stream' `
  -d '{"message":"Explain virtual threads in three parts"}'
```

空白输入映射为 HTTP 400；同步模型失败映射为 HTTP 502。SSE 在响应提交前发生的错误可以由 HTTP 异常处理器处理；一旦响应头或首个分片已经发送，后续超时或模型错误只能表现为流错误/连接终止，不能再改写成 HTTP 502。当前 SSE 契约仍然只传文本增量，不聚合 `ChatResponse`，因此不返回、也不声称拥有整次请求的最终 usage。

## 离线测试

测试禁用 OpenAI ChatModel 自动配置，并注入 `DeterministicChatModel`。替身直接生成固定 `ChatResponse`/`Flux<ChatResponse>`，不读取 API key，不创建外部请求。工具循环测试使用另一个脚本化离线 `ChatModel`：第一轮产生固定工具调用，第二轮严格校验工具结果消息后才返回最终回答。

```powershell
Remove-Item Env:OPENAI_API_KEY -ErrorAction SilentlyContinue
Remove-Item Env:OPENAI_MODEL -ErrorAction SilentlyContinue
Remove-Item Env:OPENAI_BASE_URL -ErrorAction SilentlyContinue
Remove-Item Env:ASSISTANT_SYSTEM_PROMPT -ErrorAction SilentlyContinue
Remove-Item Env:ASSISTANT_STREAM_SIGNAL_TIMEOUT -ErrorAction SilentlyContinue
Remove-Item Env:ASSISTANT_MAX_OUTPUT_TOKENS -ErrorAction SilentlyContinue
mvn clean verify
```

流式时间测试使用 `StepVerifier.withVirtualTime`，不会真实等待 30 秒。测试覆盖：Controller → Service → ChatClient 的同步成功路径、完整元数据映射、缺失元数据保持未知、准确 system/user message、400/502；SSE 的相同 system/user 策略、订阅前零模型调用、单次订阅一次调用、首分片超时、相邻分片超时、超时原因保留、三个分片顺序、完成信号、错误信号、惰性订阅和取消传播；最大输出 Token 的外部覆盖、正数校验及同步/流式 Prompt 一致性；只读工具白名单、输入失败边界、单次工具执行、两轮 Prompt 历史和未注册工具拒绝；流超时外部覆盖、非法超时启动失败，以及配置文件中不存在密钥。

## 源码树

```text
src/main/java/io/github/jamielu/assistant/
├── SpringAiAssistantApplication.java
├── config/
│   ├── AssistantPromptProperties.java
│   ├── AssistantStreamProperties.java
│   ├── AssistantGenerationProperties.java
│   └── AssistantChatConfiguration.java
├── application/
│   ├── AssistantService.java
│   ├── ChatClientAssistantService.java
│   ├── AssistantAnswer.java
│   ├── TokenUsage.java
│   ├── InvalidAssistantInputException.java
│   └── AssistantModelException.java
├── integration/springai/
│   └── SpringAiChatResponseMapper.java
├── tools/
│   └── AssistantPolicyTools.java
└── web/
    ├── AssistantController.java
    ├── AssistantExceptionHandler.java
    ├── ChatRequest.java
    ├── ChatReply.java
    ├── TokenUsageReply.java
    └── ApiError.java
```

## 官方资料

- [Spring AI Getting Started](https://docs.spring.io/spring-ai/reference/getting-started.html)
- [Spring AI Chat Client API](https://docs.spring.io/spring-ai/reference/api/chatclient.html)
- [Spring AI Chat Model API](https://docs.spring.io/spring-ai/reference/api/chatmodel.html)
- [Reactor Flux timeout Javadoc](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html#timeout(java.time.Duration))
- [Reactor virtual-time testing](https://projectreactor.io/docs/core/release/reference/testing.html#_manipulating_time)
- [Spring Boot Externalized Configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html)
- [Spring AI ChatResponse Javadoc](https://docs.spring.io/spring-ai/docs/current/api/org/springframework/ai/chat/model/ChatResponse.html)
- [Spring AI ChatResponseMetadata Javadoc](https://docs.spring.io/spring-ai/docs/current/api/org/springframework/ai/chat/metadata/ChatResponseMetadata.html)
- [Spring AI Usage Javadoc](https://docs.spring.io/spring-ai/docs/current/api/org/springframework/ai/chat/metadata/Usage.html)
- [Spring AI 2.0.1 Upgrade Notes](https://docs.spring.io/spring-ai/reference/upgrade-notes.html#_upgrading_to_2_0_1)
- [Spring AI OpenAI Chat](https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html)
- [Spring AI Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html)
- [Spring AI Advisors](https://docs.spring.io/spring-ai/reference/api/advisors.html)
- [Spring AI Chat Memory](https://docs.spring.io/spring-ai/reference/api/chat-memory.html)
- [OpenAI Production best practices](https://developers.openai.com/api/docs/guides/production-best-practices)
