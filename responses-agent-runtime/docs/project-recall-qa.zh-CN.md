# Responses Agent Runtime 项目回忆题（含答案）

[English](project-recall-qa.md) | [简体中文](project-recall-qa.zh-CN.md)

本文用于项目结课后的主动回忆，不引入新的学习范围。建议先遮住答案独立作答，再根据代码、测试和官方资料校正。

事实依据：本仓库当前实现，以及 [OpenAI Function calling](https://developers.openai.com/api/docs/guides/function-calling)。

## 一、目标与架构边界

### 1. 这个工程解决的核心问题是什么？

答：提供一个 Java 21 的有界 Agent Runtime，把模型决策、工具校验与执行、Responses 协议适配、预算和失败终态组合成可运行、可测试的工程。

### 2. 为什么核心 Runtime 必须保持供应商中立？

答：这样核心循环只依赖自身端口和值对象，不受 OpenAI SDK 类型和版本变化影响，也便于替换模型供应商及进行纯离线测试。

### 3. `api`、`runtime`、`tool`、`openai`、`online`、`reliability` 六个包各负责什么？

答：`api` 放中立数据类型，`runtime` 编排循环和预算，`tool` 负责白名单与执行，`openai` 负责 Responses 协议适配，`online` 负责配置与生产组合，`reliability` 放重试和幂等策略。

### 4. `AgentRuntime` 的单一职责是什么？

答：驱动“模型决策—工具执行—模型续接”的有界生命周期，并把所有退出路径归一化为明确终态。

### 5. `AgentModelPort` 为什么设计成窄接口？

答：Runtime 每一步只需要根据 `AgentContext` 获得一个 `AgentDecision`；窄接口避免把 SDK 请求、连接和协议细节泄漏进核心循环。

### 6. `OpenAiResponsesModel` 承担什么角色？

答：它是 `AgentModelPort` 的 OpenAI adapter，协调请求会话、transport 和响应解码，但不执行应用工具。

### 7. `OpenAiResponsesTransport` 为什么要单独存在？

答：它把阻塞式 `responses.create` 限制在一个可注入边界，使契约测试能够捕获请求而不访问网络，并集中映射 SDK 异常。

### 8. `OpenAiConversation` 保存哪些状态？

答：初始输入和工具快照、上一响应 ID、待回传的 `call_id`，以及作出上次决策时的历史长度。

### 9. `OpenAiResponseDecoder` 为什么不能只读取固定数组位置？

答：Responses 的 `output` 是异构条目数组，可能含 reasoning、message 或 function call；固定位置会把合法响应误判或解码错误。

### 10. ArchUnit 在工程中保护什么？

答：它强制核心包不得依赖 OpenAI、online、offline 或 example 包，并防止 OpenAI adapter 反向依赖组合入口。

## 二、Runtime、预算与终态

### 11. Runtime 的主要状态轨迹有哪些？

答：`START`、`MODEL`、`TOOL`、`FINAL` 和 `STOP`；具体路径取决于最终回答、预算停止或失败。

### 12. Runtime 有哪四类终态？

答：`Completed`、`Stopped`、`ModelFailed` 和 `ToolFailed`。

### 13. 为什么终态使用 sealed interface？

答：它把可能的结果集合封闭起来，使调用方可以穷尽处理成功、主动停止、模型失败和工具失败。

### 14. `RunBudget` 当前限制哪些资源？

答：跨轮模型调用数、应用工具调用数和单次运行的单调总时长。

### 15. 为什么模型调用预算必须在请求前检查？

答：请求一旦发出就可能产生费用和外部状态；事后检查无法阻止超预算调用。

### 16. 为什么工具预算必须在 handler 副作用前检查？

答：预算的意义是阻止额外副作用，而不是在副作用已经发生后仅记录超限。

### 17. 截止时间为什么使用单调时钟？

答：单调时钟不受系统时间校准或时区变化影响，更适合计算一次运行的持续时间。

### 18. `RunUsage` 为什么出现在每一种终态中？

答：无论成功、停止还是失败，调用方都能审计实际模型调用数、工具调用数和耗时。

### 19. 为什么每次 `run` 要从工厂取得独立模型实例？

答：`OpenAiResponsesModel` 保存会话续接状态；共享实例会让并发或连续运行互相污染 `previous_response_id` 和待处理调用。

### 20. Runtime 为什么还要检查重复 `call_id`？

答：同一次运行中重复执行相同调用可能重复产生副作用；Runtime 在执行前 fail-closed。

## 三、工具契约、协议账本与可靠性

### 21. `ToolRegistry` 为什么同时是白名单？

答：模型只能请求工具，不能取得执行权限；只有本地显式注册的工具和 handler 才允许执行。

### 22. 什么叫无副作用预检？

答：在调用 handler 前完成工具存在性、参数名称、必填值和调用关联校验，预检阶段不得触发业务操作。

### 23. 为什么多调用批次要整体预检后再执行第一个调用？

答：若后续调用非法，提前执行前面的调用会留下本可避免的部分副作用。

### 24. 当前 `ToolDefinition` 支持的参数边界是什么？

答：当前只支持有序的必填字符串字段；复杂类型属于明确的后续扩展范围。

### 25. OpenAI 工具 Schema 为什么使用严格模式？

答：严格模式让模型生成的参数遵守 JSON Schema；本项目同时设置全部字段必填及 `additionalProperties=false`。

### 26. `call_id` 的真正用途是什么？

答：它把某个 `function_call_output` 精确关联到模型产生的特定函数调用，是协议关联键，不是业务幂等键。

### 27. `OpenAiResponseLedger` 解决什么问题？

答：它验证并保留 Responses 输出中的 reasoning、消息和函数调用条目，再按正确关系追加工具结果。

### 28. 为什么必须保留协议条目的原始顺序？

答：顺序是模型上下文的一部分；打乱 reasoning、调用和结果可能改变续接语义或破坏协议关联。

### 29. 第二个调用预检失败时为什么第一个 handler 也不能执行？

答：完整批次尚未通过安全校验，此时执行第一个调用会产生不必要且可能无法回滚的副作用。

### 30. 执行期第二个调用失败时，第一个调用会回滚吗？

答：不会。工程保证停止后续执行，但不承诺回滚已经完成的外部副作用；事务补偿属于应用责任。

### 31. 幂等操作键与请求指纹分别有什么作用？

答：操作键标识同一业务操作，请求指纹证明重放请求内容一致；同键不同指纹必须拒绝。

### 32. `RetryPolicy` 为什么设计为纯决策而不直接 sleep？

答：纯策略更易确定性测试，也把调度、线程和取消职责留给实际 transport 或执行器。

## 四、OpenAI Responses 与函数调用

### 33. Tool、tool call 和 tool call output 有什么区别？

答：Tool 是应用声明的能力；tool call 是模型提出的调用请求；tool call output 是应用执行后回传给模型的结果。

### 34. 官方函数调用流程的五个高层步骤是什么？

答：发送带工具的请求、接收工具调用、应用侧执行、把工具结果发送给模型、接收最终回答或更多调用。

### 35. Responses `output` 为什么必须按类型遍历？

答：它可能同时包含 reasoning、message、`function_call` 等不同条目，不能假设文本始终位于固定索引。

### 36. 一个模型响应可能包含多少个函数调用？

答：官方要求应用按零个、一个或多个调用处理；不能只实现单调用假设。

### 37. `parallel_tool_calls=false` 保证什么？

答：它使模型在一次响应中产生零个或一个工具调用，从而匹配本项目在线 Runtime 的单决策端口。

### 38. 严格模式对对象 Schema 有哪两个关键要求？

答：每个对象必须设置 `additionalProperties=false`，并把 `properties` 中的字段全部列入 `required`。

### 39. 严格模式下如何表达可选字段？

答：字段仍列入 `required`，但类型允许 `null`。

### 40. 工具定义会消耗 Token 吗？

答：会。官方说明函数定义会进入模型上下文，因此计入上下文限制和输入 Token 费用。

### 41. reasoning 模型返回工具调用时，reasoning 条目应如何处理？

答：官方要求把返回的 reasoning 条目与工具调用结果一起传回后续请求；本项目的协议账本保留这些条目。

### 42. `function_call_output` 的输出通常是什么格式？

答：通常是字符串，内容可为 JSON、错误码或普通文本；图片或文件场景也可使用对应对象数组。

### 43. `tool_choice` 常见模式有哪些？

答：`auto`、`required`、强制指定函数、`allowed_tools`，以及禁止工具的 `none`。

### 44. 本项目如何使用 `previous_response_id`？

答：首轮保存响应 ID；工具成功后，续接请求携带上一响应 ID 和匹配的 `function_call_output`。

### 45. 本项目为什么每轮都重新应用 model、instructions 和 tools？

答：它把请求配置保持为显式不变量，避免续接时依赖隐式客户端状态，并使捕获请求的契约测试可以验证完整配置。

## 五、错误、测试与发布

### 46. 为什么 OpenAI SDK 异常要映射成 `FailureKind`？

答：核心 Runtime 不应依赖供应商异常类；稳定分类让重试、退出码、日志和调用方处理保持一致。

### 47. 为什么 SDK 与应用不能同时拥有独立重试循环？

答：双层重试会乘法放大请求次数、费用和延迟，也会让全局预算失真。

### 48. 默认 `clean verify` 如何保证不会产生 API 费用？

答：默认测试只使用 fake、fixture 或可注入 transport，不显式运行在线 CLI，也不由 Failsafe 执行 `*IT`。

### 49. 生产代码、测试代码和 profile 现在分别放在哪里、负责什么？

答：生产代码全部位于 `src/main/java`，测试与 fixture 全部位于 `src/test/java`；profile 只选择离线/在线入口或显式启用 live IT。

### 50. 项目发布前最关键的证据与剩余风险是什么？

答：证据包括默认构建、100% 行/分支门禁、Javadoc、离线 Demo、CLI 退出行为和文档检查；剩余风险包括进程内幂等、无副作用回滚、应用侧授权审批，以及有限的 live 延迟、费用和模型行为证据。
