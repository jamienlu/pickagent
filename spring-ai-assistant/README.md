# Spring AI Assistant

Java 21、Spring Boot 4.1.1、Spring AI BOM 2.0.0 的最小同步与 SSE ChatClient 纵向切片。

## 组件职责

- Spring Boot / Spring AI 自动配置：读取外部配置，创建供应商 `ChatModel` 和 prototype `ChatClient.Builder`，负责对象装配，不承载业务提示词。
- `ChatModel`：模型能力的底层 Spring AI 抽象，接收 `Prompt`，提供同步 `ChatResponse` 或流式 `Flux<ChatResponse>`；生产环境由 OpenAI starter 实现，测试环境由确定性替身实现。
- `ChatClient`：位于应用服务中的 fluent facade，负责构造 prompt、调用 `ChatModel`，并把结果投影为 `String` 或 `Flux<String>`。

调用链：

```text
POST /api/assistant/chat
  → AssistantController
  → AssistantService
  → ChatClient.prompt().user(...).call().content()
  → ChatModel
```

同步链路可能阻塞在 `content()` 触发的供应商请求：Servlet 请求线程会等待完整模型响应。流式链路通过 `stream().content()` 返回惰性的 `Flux<String>`，生产代码不调用 `block()` 或 `collectList()`，因此分片、错误和取消信号可以沿 Reactor 链传播。

## 配置边界

仓库中的 `application.properties` 只保留环境变量占位符：

| 环境变量 | 用途 |
| --- | --- |
| `OPENAI_API_KEY` | 从秘密管理系统注入的 API key |
| `OPENAI_MODEL` | 部署选择的模型名称 |
| `OPENAI_BASE_URL` | 部署选择的 OpenAI 或兼容服务基础地址 |

PowerShell 在线启动示例：

```powershell
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

空白输入映射为 HTTP 400；同步模型失败映射为 HTTP 502。SSE 响应提交之后发生的模型错误表现为流错误/连接终止，因为此时 HTTP 状态已经不能重写。

## 离线测试

测试禁用 OpenAI ChatModel 自动配置，并注入 `DeterministicChatModel`。替身直接生成固定 `ChatResponse`/`Flux<ChatResponse>`，不读取 API key，不创建外部请求。

```powershell
Remove-Item Env:OPENAI_API_KEY -ErrorAction SilentlyContinue
Remove-Item Env:OPENAI_MODEL -ErrorAction SilentlyContinue
Remove-Item Env:OPENAI_BASE_URL -ErrorAction SilentlyContinue
mvn clean verify
```

测试覆盖：Controller → Service → ChatClient 的同步成功路径、实际用户内容、400/502；SSE 三个分片的顺序和完成信号、错误信号、取消传播；以及配置文件中只存在环境变量占位符。

## 源码树

```text
src/main/java/com/pickagent/assistant/
├── SpringAiAssistantApplication.java
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
- [Spring AI OpenAI Chat](https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html)
- [OpenAI Production best practices](https://developers.openai.com/api/docs/guides/production-best-practices)

