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
- 跨轮模型调用、工具调用和单调时长预算；每个终态都携带不可变消耗快照。
- 包含无副作用批次预检的可信工具注册表。
- 采用 fail-closed 错误处理的确定性串行批次执行。
- 包含指数退避、`Retry-After`、抖动和总等待预算的纯重试决策。
- 用于确定性本地验证的进程内幂等语义。
- OpenAI SDK 异常类型化映射，以及由 SDK 独占的 transport 重试边界。
- 凭据、模型、超时、重试、输出上限、续接存储和 Runtime 预算均使用外置在线配置。
- 使用 Maven 标准源码布局：所有生产类位于 `src/main/java`，所有测试与测试 fixture 位于 `src/test/java`。
- offline、online 和 live 配置档只隔离执行方式；默认构建无需 API Key、网络或付费请求。
- 默认 181 个测试，强制 100% 行/分支覆盖率、架构与源码布局边界和零警告 Javadoc。

## 包边界

```text
io.github.jamielu.agent.api
    只存放供应商中立的 record 和生命周期类型

io.github.jamielu.agent.runtime
    Agent 循环、模型端口、状态转换、全局预算和消耗

io.github.jamielu.agent.tool
    工具白名单、契约校验、预检和分发

io.github.jamielu.agent.openai
    OpenAI SDK 映射、transport 边界、会话状态、响应解码、
    类型化错误映射和 Responses 协议续接账本

io.github.jamielu.agent.online
    外置配置校验、SDK 客户端创建、资源生命周期和显式在线 CLI

io.github.jamielu.agent.offline
    永不打开网络 transport 的确定性可执行证明

io.github.jamielu.agent.reliability
    重试和幂等策略

src/test/java/io/github/jamielu/agent/live
    默认 Surefire 不执行、仅显式启用的带凭据 smoke 测试
```

Runtime 和 API 包不导入 OpenAI SDK 类型。依赖方向从 adapter 指向供应商中立 API，而不是从 Runtime 指向具体供应商。

## Agent 循环与 Responses adapters

`AgentRuntime` 实现完整的供应商中立循环：它可以反复请求模型决策，每一步最多执行一次已验证的工具调用，并在得到最终回答或命中明确的预算/安全条件时停止。

`OpenAiResponsesModel` 协调 `OpenAiConversation`、`OpenAiResponseDecoder` 和可注入的 `OpenAiResponsesTransport`。第一轮发送用户文本，后续轮次通过上一响应 ID 发送最新且匹配的 `function_call_output`。model、instructions、工具、输出上限与存储选项保持一致；`parallel_tool_calls=false` 使供应商每次决策符合核心端口零或一次调用的契约。`OpenAiOnlineRunner` 为每次在线运行创建并关闭独立 SDK 会话。

`RunBudget` 在整个循环中限制模型调用数、应用工具调用数和单调总时长。预算在模型请求与工具副作用前检查。`Completed`、`Stopped`、`ModelFailed` 和 `ToolFailed` 都包含不可变 `RunUsage` 快照。

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
mvn "-P!jdk-17" clean verify
mvn "-P!jdk-17,offline" clean verify exec:java
mvn "-P!jdk-17,online" clean verify
mvn "-P!jdk-17,live" -DskipITs test-compile
```

离线 Demo 会输出 `ledger.proof=PASS`、两次模型调用和一次工具调用。online 验证与 live 编译不会发送请求。任何明确审批的在线运行前都应阅读[配置说明](docs/configuration.zh-CN.md)。

## 当前范围

- 函数参数目前仅支持必填字符串字段。
- 多个调用会先统一预检，再串行执行；尚未实现线程池并行。
- 阻塞式 SDK transport 和多步 Runtime adapter 已实现，但默认构建与离线 Demo 绝不会发送 live 请求。
- 实时 adapter 刻意禁用并行工具调用；`OpenAiResponseReplay` 继续离线覆盖确定性的多调用批次。
- 带凭据 smoke 测试位于标准测试目录，但只在显式 `live` 配置档下执行；普通验收不会运行它，且它不能证明生产延迟、费用或模型行为。
- 幂等存储仅限当前进程，不提供崩溃恢复或分布式 exactly-once 语义。
- 应用必须在执行敏感 handler 前增加授权和人工审批。

## 架构决策

- [文档索引](docs/README.zh-CN.md)
- [架构、类职责与调用链路](docs/architecture.zh-CN.md)
- [可持续迭代项目思维导图](docs/project-mind-map.zh-CN.md)
- [配置说明](docs/configuration.zh-CN.md)
- [运维说明](docs/operations.zh-CN.md)
- [故障排查](docs/troubleshooting.zh-CN.md)
- [测试说明](docs/testing.zh-CN.md)
- [发布验收](docs/release-verification.zh-CN.md)
- [项目与 OpenAI 带答案回忆题](docs/project-recall-qa.zh-CN.md)
- [ADR-0001：Runtime 与包边界](docs/adr/0001-runtime-and-package-boundaries.zh-CN.md)
- [ADR-0002：Responses 协议账本与调用批次](docs/adr/0002-responses-protocol-ledger.zh-CN.md)
- [ADR-0003：工具契约与执行权限](docs/adr/0003-tool-contract-and-authority.zh-CN.md)
- [ADR-0004：重试与幂等策略](docs/adr/0004-retry-and-idempotency.zh-CN.md)
- [ADR-0005：响应终态与解码边界](docs/adr/0005-response-terminal-and-decoding.zh-CN.md)
- [ADR-0006：离线验证边界](docs/adr/0006-offline-verification-boundary.zh-CN.md)
- [ADR-0007：有状态 Responses 续接](docs/adr/0007-stateful-responses-continuation.zh-CN.md)
- [ADR-0008：全局运行预算](docs/adr/0008-global-run-budget.zh-CN.md)
- [ADR-0009：配置档隔离与职责拆分](docs/adr/0009-profile-isolation-and-responsibility-split.zh-CN.md)
- [ADR-0010：Maven 标准源码布局](docs/adr/0010-standard-maven-source-layout.zh-CN.md)

## 官方参考资料

- [OpenAI Function calling](https://developers.openai.com/api/docs/guides/function-calling)
- [OpenAI Reasoning models](https://developers.openai.com/api/docs/guides/reasoning)
- [OpenAI Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs)
- [OpenAI Error codes](https://developers.openai.com/api/docs/guides/error-codes)
- [OpenAI Rate limits](https://developers.openai.com/api/docs/guides/rate-limits)
- [OpenAI Production best practices](https://developers.openai.com/api/docs/guides/production-best-practices)
