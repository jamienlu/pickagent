# ADR-006：W2 Capstone 纵向切片与能力边界

- 状态：Accepted
- 日期：2026-09-04
- 关联：ADR-002、ADR-003、ADR-004、ADR-005

## Context

W2 已分别实现 strict tool schema、OpenAI SDK function-call 映射、供应商中立 ToolCall、ToolRegistry、ToolResult 映射、有界重试和进程内幂等。组件独立测试不能单独证明它们按同一责任边界连接，也容易让 Demo 退化为只验证字符串的展示程序。

需要一个最小、离线、确定性的纵向切片，复用已有组件并暴露可由行为测试检查的中间对象；它不是新的 Agent 框架，也不是完整 OpenAI adapter。

## Decision

新增 `Week2CapstoneDemo`，固定执行以下责任链：

```text
SDK call → OpenAiFunctionCallMapper → core ToolCall → ToolRegistry → handler
         → ToolResult → OpenAiFunctionCallOutputMapper → SDK output
```

同时：

1. `OpenAiFunctionToolMapper` 从同一个 `ToolDefinition` 生成 strict SDK schema，证明“告诉模型的契约”来自 Registry 使用的定义。
2. mapper 只解析 SDK 协议与 arguments JSON；Registry 负责工具白名单及运行时参数校验；handler 负责执行已验证操作。
3. handler 内使用应用生成的稳定 `operationKey` 和请求指纹调用 `IdempotentExecutor`。同一调用重放会再次经过 Registry/handler 边界，但底层副作用只发生一次。
4. `call_id` 只沿 SDK call、core ToolCall、ToolResult、SDK output 原样传播，不作为业务幂等键。
5. `RetryPolicy` 以固定 jitter 和 Retry-After 生成 typed decision，不发请求、不等待，也不自行重新执行工具。
6. Demo 返回 package-private evidence 供同包测试断言真实 SDK/core 对象；控制台 trace 只用于观察，不是唯一验收证据。

## Responsibility boundaries

- OpenAI mapper：SDK 类型与核心类型之间的协议转换和可表示性检查。
- ToolRegistry：工具 allowlist、精确参数集合、blank 值校验和分发。
- handler：一个已经通过 Registry 校验的工具操作。
- IdempotentExecutor：进程内串行重放的首次成功结果复用和指纹冲突拒绝。
- RetryPolicy：根据 typed failure 和预算计算停止或延迟，不保证副作用安全。
- 应用编排层：生成 operationKey、拥有总预算，并决定何时消费 retry decision。

## Consequences

收益：call_id、strict schema、Registry 校验、retry decision 和业务防重在一条可运行路径中可观察；测试直接验证对象和计数器，不依赖控制台文案；所有 fixture 都不读取 API Key 或访问网络。

代价：Demo 知道各适配器的装配顺序；内存幂等只能证明单进程串行重放；固定 fixture 不能代表供应商实际行为。

## Explicitly unsupported

- 并行 function calls、多调用编排和批量 tool outputs。
- reasoning items 的持久化、无损续接或供应商会话恢复。
- 真实网络、API Key、真实模型调用、供应商可用性与延迟测量。
- OpenAI SDK 自动重试与应用 RetryPolicy 的统一预算整合。
- 并发锁、数据库/Redis、跨进程或分布式幂等，以及 exactly-once 承诺。
- 真实计费验证、重试队列、补偿事务、failover 和自动工具循环扩展。

## Verification

- `Week2CapstoneDemoTest` 的 8 个行为测试覆盖 strict schema、call_id 往返、Retry-After + jitter、永久错误停止、同 key 重放、指纹冲突、失败不缓存和多调用拒绝。
- `Week2CapstoneDemo` 必须通过 `java -cp target/classes ...` 正常退出，并打印 `capstone.proof=PASS`。
- 默认 Maven 测试必须保持离线，不运行 integration profile。

## Source basis

- [OpenAI Function calling](https://developers.openai.com/api/docs/guides/function-calling)
- [OpenAI Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs)
- [OpenAI Error codes](https://developers.openai.com/api/docs/guides/error-codes)
- [OpenAI Rate limits](https://developers.openai.com/api/docs/guides/rate-limits)
