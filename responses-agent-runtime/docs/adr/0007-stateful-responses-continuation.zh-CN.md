# ADR-0007：使用 `previous_response_id` 进行有状态 Responses 续接

[English](0007-stateful-responses-continuation.md) | [简体中文](0007-stateful-responses-continuation.zh-CN.md)

- 状态：已接受
- 日期：2026-09-08

## 背景

`AgentRuntime` 消费供应商中立的 `AgentModelPort`，其决策要么是一次工具调用，要么是最终回答。现有 `OpenAiResponseReplay` 已证明可以手动保留 Responses 的异构输出，包括多调用批次，但它既不会通过 OpenAI SDK 客户端发送请求，也不支持 Runtime 任意多个步骤的循环。

生产桥接层需要在多个工具轮次间保持 Responses 对话，同时不能让 SDK 类型泄漏到核心包。它还必须防止不同或并发的 Runtime 运行意外共享同一个有状态供应商会话。

## 决策

1. `OpenAiResponsesTransport` 是最小的阻塞式 `responses.create` 边界。`fromClient(OpenAIClient)` 提供生产 SDK 桥接，测试则注入内存 transport。
2. `OpenAiResponsesModel` 实现 `AgentModelPort`，并拥有单次运行的供应商状态。
3. 首次请求发送用户文本。续接请求把最新工具结果作为一个保留原始 `call_id` 的 `function_call_output`，并把 `previous_response_id` 设置为紧邻的上一响应 ID。
4. 每个请求都重复传入 model、可选 instructions 和函数工具，并显式设置 `store=true` 与 `parallel_tool_calls=false`。
5. `parallel_tool_calls=false` 刻意把供应商每轮收窄为零或一个函数调用，以匹配当前核心端口。独立的 replay adapter 继续证明确定性的多调用批次语义。
6. `AgentRuntime.withModelFactory` 为每次 `run` 创建全新的模型 adapter。跨运行共享同一个 `OpenAiResponsesModel` 不在契约范围内。
7. 对空白响应 ID、多个或不支持的工具输出条目、不支持的终态条目、缺失最终文本和续接上下文错配，adapter 均采用 fail-closed。
8. 默认验证保持离线。SDK 响应 fixture 和捕获请求的 transport 在不使用凭据、网络和计费的情况下证明请求构造与 Runtime 接线。

## 后果

- 通用 Runtime 现在可以在有界步骤内驱动阻塞式 Responses API 会话。
- 核心 `api`、`runtime`、`tool` 和 `reliability` 包继续保持不依赖 OpenAI SDK 类型。
- 续接依赖供应商侧已存储的响应状态。显式 `store=true` 不适用于零数据保留部署；这类部署需要独立的手动 replay 或加密 reasoning 设计。
- 每次续接都会重新构建 instructions 和工具，不能假设它们自动继承。
- transport 边界已可继续组合重试、错误分类、追踪和显式启用的实时集成测试，但本 ADR 不代表这些能力已经完成。
- 离线测试通过只证明 SDK 对象构造和本地状态转换，不证明实时服务兼容性、账号权限、延迟或模型行为。

## 参考资料

- [OpenAI Function calling](https://developers.openai.com/api/docs/guides/function-calling)
- [OpenAI Responses API reference](https://developers.openai.com/api/reference/resources/responses/methods/create)

