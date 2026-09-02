# ADR-004：Responses Function Call Bridge 的能力边界

- 状态：Accepted
- 日期：2026-09-02
- 关联：ADR-002-agent-runtime-loop-and-stop-policy.md、ADR-003-strict-tool-schema-boundary.md

## Context

OpenAI Responses SDK 使用异构 `ResponseOutputItem` 表达 reasoning、message、function call 等输出。当前供应商中立核心只用 `AgentDecision.ToolCall(callId, toolName, Map<String,String>)` 表达一个串行调用。需要一个明确的桥接层，既保留关联字段，又不能把供应商能力静默压缩成看似成功的核心决策。

官方流程说明工具调用是多步对话，最终响应仍可能包含更多调用；响应也可能一次包含零个、一个或多个调用。对 reasoning 模型，随工具调用返回的 reasoning items 必须和工具结果一起续接。[OpenAI Function Calling](https://developers.openai.com/api/docs/guides/function-calling)

## Decision

1. `OpenAiFunctionCallMapper` 遍历异构 `ResponseOutputItem`，只收集 `functionCall()`。reasoning item 可以位于调用前，不会被当成“无调用”；但是 mapper 不把 reasoning 塞入核心不存在的字段。
2. 入站必须恰好有一个 function call。无调用与多个调用分别产生 typed mapping reason；多个调用 fail-fast，并在任何工具执行前停止，不静默选择第一个。
3. adapter 解析 `ResponseFunctionToolCall.arguments()`：必须是合法 JSON object，且每个值必须是字符串。`callId()` 与 `name()` 原样映射到核心。JSON 解析不执行工具，也不代替 Registry 的 allowlist、精确字段集合和 blank 校验。
4. `OpenAiFunctionCallOutputMapper` 将 `ToolResult.callId` 和 `output` 原样映射到 SDK 4.52.0 的 `ResponseInputItem.FunctionCallOutput`。关联依据是 `call_id`，不是 SDK output item 的 `id`。
5. `ToolRegistry` 仍拥有运行时参数校验和分发；应用层仍应在 handler 执行前拥有鉴权与业务审批。协议格式校验、运行时参数校验、执行授权是三个独立边界。

## 当前明确支持

- 单个串行 Responses function call 到核心 ToolCall 的字段桥接。
- JSON object 中的字符串参数。
- 核心 ToolResult 到 SDK function-call-output 的文本映射。
- 离线 Replay fixture 中 call_id 的完整往返与单次 handler 执行。

## 当前不能声称支持

- 多调用、并行调用或多个结果的编排；多个调用直接失败。
- 数字、布尔、null、数组、嵌套对象或可选参数。
- reasoning items 的持久化与下轮续接。现有核心无法无损保存 reasoning item，因此今天不是完整、真实的 `AgentModelPort` adapter。
- 最终文本、refusal、incomplete、provider failure 等完整 Responses 终态映射。
- 真实网络、模型版本、API Key、供应商实际 strict 遵循或服务端会话状态。

## 被否决方案

- 只取第一个 function call：会丢掉供应商返回的其余动作，产生不可审计行为。
- 把 reasoning item 丢失描述为“已支持 reasoning”：能跳过异构项不等于能续接供应商推理状态。
- 在 mapper 内查询 Registry 或执行工具：混合协议转换、应用策略与副作用，违反 ADR-002 的 Runtime 所有权。
- JSON 解析成功后直接执行：合法 JSON 不等于字段契约有效，更不等于已经授权。

## Consequences

收益：SDK、核心与执行边界的字段责任可逐项审查；不支持的供应商形态显式失败；call_id 往返有离线契约测试保护。

代价：即使 OpenAI 能返回多个调用或 reasoning item，当前桥接也会保守拒绝或无法续接。要实现真实 adapter，必须先在核心 capability 中明确表达多调用与供应商续接状态，不能在 adapter 中暗自循环。

## Verification

- `OpenAiFunctionCallMapperTest`：正常、reasoning 前置、畸形 JSON、数组根、非字符串值、零调用、多个调用。
- `OpenAiFunctionCallOutputMapperTest`：call_id、output、空字符串与 null 边界。
- `OpenAiToolBridgeContractTest`：SDK call 经 Registry 到 SDK output 的完整关联和一次执行。
- `OpenAiToolRoundTripDemoTest`：固定输出入站、核心结果、出站三处 call_id。
- 默认测试与 Demo 全程离线，不运行 integration profile。
