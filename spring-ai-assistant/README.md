# Spring AI Assistant

Java 21、Spring Boot 4.1.1、Spring AI BOM 2.0.0 的最小同步与 SSE ChatClient 纵向切片。工程有意保留 Spring AI 2.0.0；2.0.1 升级说明包含迁移影响，本次不做依赖升级。

## 组件职责

- Spring Boot / Spring AI 自动配置：读取供应商配置，创建 `ChatModel` 和 prototype `ChatClient.Builder`，负责底层对象装配，不决定应用提示词策略。
- `ChatModel`：模型能力的底层 Spring AI 抽象，接收 `Prompt`，提供同步 `ChatResponse` 或流式 `Flux<ChatResponse>`；生产环境由 OpenAI starter 实现，测试环境由确定性替身实现。
- `ChatClient`：由应用配置组合根使用 `builder.defaultSystem(...)` 构建，提供 fluent prompt API，调用 `ChatModel`，并把结果投影为 `String` 或 `Flux<String>`。

生命周期边界：自动配置的可变 `ChatClient.Builder` 是 prototype，每个注入点获得独立实例，避免一个客户端的默认值污染另一个客户端；本应用在配置类中只构建一个带默认 system prompt 的 `ChatClient` Bean，并由应用服务复用；生产 `ChatModel` 由供应商自动配置管理，测试 `ChatModel` 则由每个测试或测试上下文单独创建。

调用链：

```text
POST /api/assistant/chat
  → AssistantController
  → AssistantService
  → ChatClient.prompt().user(...).call().content()
  → ChatModel
```

`call()` 选择同步执行并向模型发送请求，随后可以选择响应投影；`content()` 是其中最窄的投影，只提取模型回复文本。同步链路会等待完整模型响应，因此 Servlet 请求线程可能阻塞。流式链路通过 `stream().content()` 返回惰性的 `Flux<String>`，生产代码不调用 `block()`、`collectList()` 或 `subscribe()`，因此分片、错误和取消信号可以沿 Reactor 链传播。

“配置一致”不等于“执行模型一致”：同步和流式请求共享同一个 `ChatClient` 默认 system prompt，但前者等待单个完整结果，后者依赖 Reactive 流的订阅、背压、取消和错误信号。

## 配置边界

仓库中的 `application.properties` 只保留环境变量占位符：

| 环境变量 | 配置归属 | 用途 |
| --- | --- | --- |
| `ASSISTANT_SYSTEM_PROMPT` | 应用策略 | 所有同步和流式请求共享的默认系统指令；未提供时使用仓库中的非敏感默认文本 |
| `OPENAI_MODEL` | provider | 部署选择的模型名称 |
| `OPENAI_API_KEY` | provider 凭据 | 从秘密管理系统注入的 API key |
| `OPENAI_BASE_URL` | transport/provider | OpenAI 或兼容服务的基础地址 |

system prompt 的组合优先级为：外部 `ASSISTANT_SYSTEM_PROMPT` 覆盖配置文件的非敏感默认值 → `AssistantChatConfiguration` 将绑定结果固化为 `ChatClient` 默认 system message → 请求级 `.system(...)` 如被显式使用则覆盖客户端默认值。当前应用服务只设置 `.user(...)`，因此同步和流式调用都继承同一个应用默认 system prompt。

PowerShell 在线启动示例：

```powershell
$env:ASSISTANT_SYSTEM_PROMPT='Answer accurately and concisely.'
$env:OPENAI_API_KEY='<secret-from-manager>'
$env:OPENAI_MODEL='<model-name>'
$env:OPENAI_BASE_URL='<provider-base-url>'
mvn spring-boot:run
```

只有三个变量均已由可信部署环境注入时才应在线启动。不要把真实值写入源码、配置文件、命令历史、日志或提交记录。

## HTTP 调用

同步调用：

```powershell
curl.exe -X POST http://localhost:8080/api/assistant/chat `
  -H 'Content-Type: application/json' `
  -d '{"message":"Explain virtual threads"}'
```

SSE 流式调用：

```powershell
curl.exe -N -X POST http://localhost:8080/api/assistant/stream `
  -H 'Content-Type: application/json' `
  -H 'Accept: text/event-stream' `
  -d '{"message":"Explain virtual threads in three parts"}'
```

空白输入映射为 HTTP 400；同步模型失败映射为 HTTP 502。SSE 一旦提交响应头和首个分片，HTTP 状态码已经发送，后续模型错误只能表现为流错误或连接终止，不能再改写成 HTTP 502。

## 离线测试

测试禁用 OpenAI ChatModel 自动配置，并注入 `DeterministicChatModel`。替身直接生成固定 `ChatResponse`/`Flux<ChatResponse>`，不读取 API key，不创建外部请求。

```powershell
Remove-Item Env:OPENAI_API_KEY -ErrorAction SilentlyContinue
Remove-Item Env:OPENAI_MODEL -ErrorAction SilentlyContinue
Remove-Item Env:OPENAI_BASE_URL -ErrorAction SilentlyContinue
mvn clean verify
```

测试覆盖：Controller → Service → ChatClient 的同步成功路径、准确 system/user message、400/502；SSE 的相同 system/user 策略、三个分片顺序、完成信号、错误信号、惰性订阅和取消传播；外部 system prompt 覆盖、空白配置失败，以及配置文件中不存在密钥。

## 源码树

```text
src/main/java/io/github/jamielu/assistant/
├── SpringAiAssistantApplication.java
├── config/
│   ├── AssistantPromptProperties.java
│   └── AssistantChatConfiguration.java
├── application/
│   ├── AssistantService.java
│   ├── ChatClientAssistantService.java
│   ├── InvalidAssistantInputException.java
│   └── AssistantModelException.java
└── web/
    ├── AssistantController.java
    ├── AssistantExceptionHandler.java
    ├── ChatRequest.java
    ├── ChatReply.java
    └── ApiError.java
```

## 官方资料

- [Spring AI Getting Started](https://docs.spring.io/spring-ai/reference/getting-started.html)
- [Spring AI Chat Client API](https://docs.spring.io/spring-ai/reference/api/chatclient.html)
- [Spring AI 2.0.1 Upgrade Notes](https://docs.spring.io/spring-ai/reference/upgrade-notes.html#_upgrading_to_2_0_1)
- [Spring AI OpenAI Chat](https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html)
- [OpenAI Production best practices](https://developers.openai.com/api/docs/guides/production-best-practices)
