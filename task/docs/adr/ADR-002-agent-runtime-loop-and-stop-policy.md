# ADR-002：Runtime 拥有循环与停止策略

- 状态：Accepted
- 日期：2026-08-31
- 关联：ADR-001-provider-neutral-model-gateway.md

## Context

模型收到工具结果后仍可提出更多工具调用，不能假定两次模型请求就必然结束。应用需要可审核的自主行动预算、可关联的观察和可区分的失败结果；这些不应随供应商 SDK 的更换而改变。

官方五步流程的最后一步允许返回最终答案或更多调用：[OpenAI Function Calling](https://developers.openai.com/api/docs/guides/function-calling#the-tool-calling-flow)。ReAct 解释推理、行动与环境观察的交替关系，但不是具体网络协议：[论文摘要](https://arxiv.org/abs/2210.03629)。

## Decision

1. `AgentRuntime` 拥有循环、历史、逐步 trace、`maxSteps` 与停止策略。`AgentModelPort` 每次仅返回一个 `FinalAnswer` 或 `ToolCall`，model adapter 只负责单次协议转换，不执行工具、不隐式循环。
2. 一步是一次模型决策加至多一次串行工具执行。`maxSteps` 必须为正，包含最后答案那一步；工具返回不额外消耗一步。最后允许的一步若返回 FinalAnswer，仍算成功；若返回 ToolCall，则返回 `Stopped(MAX_STEPS)` 且不执行该工具，避免没有下一步消费结果的操作。因此本实现一次 run 最多调用模型 N 次、执行工具 N-1 次（N=maxSteps）。这不是 SDK 内部请求次数保证。
3. 用 sealed `AgentRuntime.Result` 表达三类结果：`Completed`、`Stopped`、`ToolFailed`。前者有 FinalAnswer；Stopped 用枚举区分未知工具、参数无效、步数耗尽、已有的重复 callId 检查；ToolFailed 保留原调用和 `ToolExecutionException` 原因链。失败不会被包装成正常答案或 transport failure。
4. `ToolHandler` 用 checked `ToolExecutionException` 显式声明可预期执行失败。工具 adapter 只将已识别的操作失败转成该异常，Runtime 捕获它并立即终止；未知 RuntimeException/Error 不被兜底捕获。这既满足工具异常有明确结果，也不掩盖编程错误。
5. Registry 校验工具 allowlist、精确参数字段和非空字符串，然后调用工具。ToolResult 的 callId 从原调用复制；上下文与 AgentStep 再次验证关联。工具 adapter 不能改写关联 ID，也不能控制下一次模型调用。
6. `AgentStep(number, decision, observation)` 与状态 trace、历史同时返回不可变快照。工具失败的当前步没有成功 observation；之前成功的观察保留。ToolFailed 独立承载当前失败，不将异常消息冒充工具成功结果。
7. Demo 使用 Replay model 和只读内存订单表，固定输出 step、decision、tool call、observation、final answer。默认测试不加载 API Key 或调用真实模型；不增加依赖或修改 pom。

当多个停止条件同时成立时，保留现有优先级：最终答案先判成功；重复 ID 先拒绝；预算耗尽先于 registry 分发停止。到达预算边界就不再检查或执行该待分发工具。

## 被否决方案

- 在 model adapter 内循环并执行工具：隐藏了调用数量、成本和停止策略，供应商替换会改变应用行为。
- 无上限地相信模型最终会回答：即便每轮调用都合法，累计代价和风险仍可能失控。
- 将任意 Exception/RuntimeException 都压成同一种失败：无法区分校验拒绝、工具执行故障与程序错误。
- 用 ReAct 文本格式代替正式协议：论文不规定供应商消息字段、call_id 关联、内容类型或生产错误语义。

## Consequences

收益：预算、故障归类和关联信息可通过离线行为测试审查；供应商只影响协议边界；上限命中与成功在类型和 trace 中可区分。

代价：最后一步的工具请求会被保守拒绝；调用方要明确处理三类结果。step 数不是 token、金额或墙钟时间的精确额度，单次模型/工具如果一直阻塞，maxSteps 本身无法打断它；生产环境仍需要独立的超时、取消、额度和权限策略，但本次不实现。

不实现重试、幂等设施、并行工具或持久化。已有单次 run 内的 callId 重复检查只是拒绝重复执行，不保证跨 run / 跨进程 exactly-once。trace 含示例输入输出，生产日志仍需脱敏及保留策略。

## 验收映射

| 要求 | 行为测试 |
| --- | --- |
| 首轮直接完成 | directFinalAnswerDoesNotExecuteAnyTool |
| 一次工具调用后完成 | toolCallThenResultThenFinalAnswerPreservesContextAndOriginalCallId |
| 原 callId 关联 | 上述完整回合测试 + exchangeRejectsMismatchedCallId + stepRejectsAnObservationWithTheWrongCallId |
| 未知工具停止 | unknownToolStopsBeforeExecutionOrAnotherModelInvocation |
| 工具异常为明确 tool failure | expectedToolExceptionBecomesTypedToolFailureAndStopsWithoutRetry |
| maxSteps 停止 | runtimeHonorsMaxStepsAndDoesNotExecuteUnconsumableToolCall + maxStepsOnePreventsAnyToolExecution |
| 最后一步仍可成功 | finalAnswerOnTheLastAllowedStepIsStillSuccess |
| 未知程序错误不误归类 | modelProgrammingErrorIsNotDisguisedAsExpectedStop + toolProgrammingErrorIsNotDisguisedAsValidationFailure |
| 可运行、完整 trace | AgentRuntimeDemoTest.demoPrintsCompleteDeterministicStepTrace |
