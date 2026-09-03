# ADR-005：有界重试与业务幂等的分层边界

- 状态：Accepted
- 日期：2026-09-03
- 关联：ADR-002-agent-runtime-loop-and-stop-policy.md、ADR-004-responses-function-call-bridge.md

## Context

一次模型或工具请求可能因临时限流、过载、超时或连接中断而失败。客户端没有收到响应，并不能证明服务端没有处理请求。若 SDK、应用服务和工具 adapter 各自独立重试，尝试次数会相乘；若重试的是扣款、发消息或创建订单等写操作，还可能重复产生业务副作用。

OpenAI 官方限流文档说明：官方 SDK 会自动重试符合条件的限流错误并遵守存在的 `Retry-After`；增加应用级重试时，必须把 SDK 已执行的尝试计入统一的次数和总时间预算。[OpenAI Rate limits](https://developers.openai.com/api/docs/guides/rate-limits)

因此，需要把“传输恢复”“应用是否继续”和“副作用去重”作为三个不同问题处理。

## Decision

### 1. SDK 重试边界

- SDK 只处理它明确识别的临时传输或供应商故障，并负责协议级 `Retry-After` 等元数据。
- SDK 内部尝试次数必须可配置、可观测，并计入应用的统一预算。
- SDK 不决定业务操作是否安全，也不创建业务幂等键。

### 2. 应用重试边界

- 应用 adapter 先把 HTTP 状态、供应商错误码和 transport exception 映射成 `FailureKind`。
- `RetryPolicy` 根据失败分类、已尝试次数、累计等待和可选 `Retry-After`，只返回 `RetryAfter(delay)` 或 `Stop(reason)`。
- 应用编排层拥有总尝试次数、总时间预算和停止策略；`RetryPolicy` 不执行网络调用，也不 `sleep`。
- 账单/额度、认证和非法请求不因原样重试而恢复，因此直接停止。

### 3. 工具幂等边界

- 有副作用的工具必须由应用生成稳定 `operationKey`，并以规范化业务请求生成稳定指纹。
- `IdempotentExecutor` 对同 key、同指纹返回首次成功结果，不再次调用 handler；同 key、不同指纹明确拒绝。
- 当前约定只缓存成功返回。异常不缓存，允许再次尝试；因此异常可能发生在“外部副作用已提交、结果尚未保存”的工具，生产环境仍需事务、持久化唯一约束或供应商侧幂等键。
- 重试策略只能限制重试，不能证明副作用只发生一次；这一安全属性属于幂等执行边界。

## `call_id` 不等于业务幂等键

`call_id` 是模型供应商在一次 function-call 协议交换中生成的关联标识，用于把 `function_call_output` 送回对应的 `function_call`。它不由业务应用控制，也不保证在网络重试、消息重投或新的模型回合中保持不变。

`operationKey` 由应用为一个逻辑业务操作生成，在 SDK 重试、应用重试和消息重放之间保持稳定。它用于副作用去重、冲突检测和业务审计。因此不能把 `call_id` 直接当作扣款、下单或发送通知的幂等键。

## Consequences

收益：每一层的重试所有权可审计；失败分类与等待计算可以离线测试；业务重放不会因为模型协议标识变化而重复执行已经记录的副作用。

代价：调用方必须传播 `operationKey` 和请求指纹，并协调 SDK 与应用层的统一预算。进程内存储只能提供有限证明，不能冒充生产级 exactly-once。

## 今日非目标

- 不实现并发锁、single-flight 或相同 key 的并发等待。
- 不实现 Redis、数据库唯一约束、事务性 inbox/outbox 或跨进程恢复。
- 不实现 SDK 配置修改、真实 HTTP、真实 OpenAI 调用或 API Key 读取。
- 不实现真实时间等待、调度器、重试队列或死信队列。
- 不实现工具自动重试、补偿事务、跨供应商 failover 或 exactly-once 承诺。
- 不改变 Agent Runtime 的工具循环，不实现并行工具调用。

## Verification

- `RetryAndIdempotencyDemo`：第一次在副作用发生前模拟临时失败，打印有界重试决策；第二次成功后重放同一 operationKey，最终副作用计数保持为 1。
- `RetryAndIdempotencyDemoTest`：锁定完整 trace 和 `proof=PASS`。
- `RetryPolicyTest`：覆盖临时 429/503、超时、Retry-After、指数退避、jitter、次数与总等待预算以及永久故障。
- `IdempotentExecutorTest`：覆盖同 key 同请求、同 key 不同请求、不同 key、失败后再试和已提交结果重放。
- 所有验证均为离线、确定性执行，不运行 integration profile。
