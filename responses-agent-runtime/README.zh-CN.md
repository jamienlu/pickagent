# Responses Agent Runtime

[English](README.md) | [简体中文](README.zh-CN.md)

这是一个使用 Java 21 实现的有界 Agent Runtime，并提供 OpenAI Responses API 协议适配层。工程将供应商中立的运行时类型与 OpenAI SDK 对象隔离，在产生副作用前校验完整工具调用批次，按照确定顺序执行调用，并保留续接请求所需的协议条目。

## 已包含能力

- 带显式状态转换和步骤上限的供应商中立 Agent 循环。
- OpenAI Responses API 严格函数工具 Schema 映射。
- 按原顺序映射一个或多个 `function_call` 输出条目。
- 保留 reasoning、助手消息、调用及匹配结果的协议账本。
- 使用 `previous_response_id` 的阻塞式 Responses transport 边界和有状态 `AgentModelPort` adapter。
- 按每次运行创建模型的工厂，隔离不同 Runtime 运行的供应商会话状态。
- 包含无副作用批次预检的可信工具注册表。
- 采用 fail-closed 错误处理的确定性串行批次执行。
- 包含指数退避、`Retry-After`、抖动和总等待预算的纯重试决策。
- 用于确定性本地验证的进程内幂等语义。
- 离线 SDK fixture 和契约测试；默认构建不需要 API Key。

## 包边界

```text
io.github.jamielu.agent.api
    只存放供应商中立的 record 和生命周期类型

io.github.jamielu.agent.runtime
    Agent 循环、模型端口、状态转换和停止预算

io.github.jamielu.agent.tool
    工具白名单、契约校验、预检和分发

io.github.jamielu.agent.openai
    OpenAI SDK 映射、实时 transport 桥接、有状态模型 adapter
    和 Responses 协议续接账本

io.github.jamielu.agent.reliability
    重试和幂等策略

io.github.jamielu.agent.example
    可复现的离线入口和 fixture adapter
```

Runtime 和 API 包不导入 OpenAI SDK 类型。依赖方向从 adapter 指向供应商中立 API，而不是从 Runtime 指向具体供应商。

## Agent 循环与 Responses adapters

`AgentRuntime` 实现完整的供应商中立循环：它可以反复请求模型决策，每一步最多执行一次已验证的工具调用，并在得到最终回答或命中明确的预算/安全条件时停止。

`OpenAiResponsesModel` 通过可注入的 `OpenAiResponsesTransport` 把该循环连接到 `responses.create`。第一轮发送用户文本，后续轮次通过上一响应 ID 发送最新且匹配的 `function_call_output`。每一轮都重复传入 model、instructions 和工具；`store=true` 启用服务端续接，`parallel_tool_calls=false` 使供应商每次决策符合核心端口零或一次调用的契约。应使用 `AgentRuntime.withModelFactory` 为每次运行创建一个 adapter。

`OpenAiResponseReplay` 继续作为聚焦协议的离线 adapter，验证一个 Responses 工具调用批次以及紧随其后的续接轮次：

```text
response.output
    -> 校验完整协议历史
    -> 映射全部函数调用
    -> 预检完整调用批次
    -> 按响应顺序串行执行已准备调用
    -> 追加匹配的 function_call_output 条目
    -> 调用注入的下一轮 fixture
    -> 要求该轮包含最终助手回答
```

对于 `reasoning, C1, C2` 批次，续接输入为 `reasoning, C1, C2, O1, O2`。第二个调用无效时，第一个 handler 也不会运行。执行期失败会停止后续调用，且不会进入下一模型轮次；已经完成的外部副作用不会回滚。

## 构建与运行

```powershell
$env:JAVA_HOME = 'C:\sdk\Java\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn "-P!jdk-17" clean test
mvn "-P!jdk-17" exec:java
```

演示完全离线运行。当协议顺序、调用关联和工具执行不变量全部成立时，会输出 `ledger.proof=PASS`。

## 当前范围

- 函数参数目前仅支持必填字符串字段。
- 多个调用会先统一预检，再串行执行；尚未实现线程池并行。
- 阻塞式 SDK transport 和多步 Runtime adapter 已实现，但默认 Demo 与测试套件使用 SDK fixture，绝不会发送真实 OpenAI 请求。
- 实时 adapter 刻意禁用并行工具调用；`OpenAiResponseReplay` 继续离线覆盖确定性的多调用批次。
- 尚无带凭据的实时兼容性、模型行为、延迟或成本证据。
- 幂等存储仅限当前进程，不提供崩溃恢复或分布式 exactly-once 语义。
- 应用必须在执行敏感 handler 前增加授权和人工审批。

## 架构决策

- [文档索引](docs/README.zh-CN.md)
- [架构、类职责与调用链路](docs/architecture.zh-CN.md)
- [可持续迭代项目思维导图](docs/project-mind-map.zh-CN.md)
- [ADR-0001：Runtime 与包边界](docs/adr/0001-runtime-and-package-boundaries.zh-CN.md)
- [ADR-0002：Responses 协议账本与调用批次](docs/adr/0002-responses-protocol-ledger.zh-CN.md)
- [ADR-0003：工具契约与执行权限](docs/adr/0003-tool-contract-and-authority.zh-CN.md)
- [ADR-0004：重试与幂等策略](docs/adr/0004-retry-and-idempotency.zh-CN.md)
- [ADR-0005：响应终态与解码边界](docs/adr/0005-response-terminal-and-decoding.zh-CN.md)
- [ADR-0006：离线验证边界](docs/adr/0006-offline-verification-boundary.zh-CN.md)
- [ADR-0007：有状态 Responses 续接](docs/adr/0007-stateful-responses-continuation.zh-CN.md)

## 官方参考资料

- [OpenAI Function calling](https://developers.openai.com/api/docs/guides/function-calling)
- [OpenAI Reasoning models](https://developers.openai.com/api/docs/guides/reasoning)
- [OpenAI Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs)
- [OpenAI Error codes](https://developers.openai.com/api/docs/guides/error-codes)
- [OpenAI Rate limits](https://developers.openai.com/api/docs/guides/rate-limits)
