# ADR-0002：Responses 协议账本与调用批次

[English](0002-responses-protocol-ledger.md) | [简体中文](0002-responses-protocol-ledger.zh-CN.md)

- 状态：已接受
- 日期：2026-09-08

## 背景

Responses 的 `output` 是有序的异构数组。一个工具轮次可以包含 reasoning 条目、助手消息，以及零个、一个或多个函数调用。`call_id` 能把调用与结果关联起来，但不能保存其余协议历史。

推理模型要求把相关 reasoning 条目与工具结果一同回传。根据可见字段重建这些条目，可能丢失不透明的加密内容、状态、phase 或未来 SDK 字段。

## 决策

对一个工具轮次执行以下流程：

1. 在运行任何 handler 前，对所有输出条目创建快照并完成校验。
2. 按原顺序保留受支持的 reasoning、助手消息和 function-call SDK 对象。
3. 按响应顺序映射所有函数调用，拒绝空白或重复的 `call_id`。
4. 在首个副作用前预检完整工具批次。
5. 按响应顺序串行执行准备好的调用。
6. 按相同顺序为每个调用追加一个 `function_call_output`，并使用完全相同的原始 `call_id`。
7. 只有完整批次成功后，才能调用下一模型轮次。
8. 未显式支持的协议条目一律 fail-closed。

对于 `reasoning, C1, C2`，下一轮输入为 `reasoning, C1, C2, O1, O2`。

## 影响

- 后续无效调用不会导致前面的 handler 先执行。
- 执行失败会停止后续调用，并阻止下一次模型请求。
- 在后续调用失败前已经完成的副作用不会回滚。
- 批次执行是确定性的，但不是事务性的。
- 并行执行是独立的未来策略；允许模型一次返回多个调用，不代表本地必须并发执行。

## 参考资料

- [OpenAI Function calling：处理函数调用](https://developers.openai.com/api/docs/guides/function-calling#handling-function-calls)
- [OpenAI Reasoning models：在上下文中保留 reasoning 条目](https://developers.openai.com/api/docs/guides/reasoning#keeping-reasoning-items-in-context)
