# ADR-0005：响应终态与解码边界

[English](0005-response-terminal-and-decoding.md) | [简体中文](0005-response-terminal-and-decoding.zh-CN.md)

- 状态：已接受
- 日期：2026-09-08

## 背景

早期练习涉及 Token 预算、流式响应和结构化输出。它们不会作为独立的每日模块复制到新工程，但其失败边界在未来加入实时 Responses 传输和结构化最终回答时仍然有效。

## 决策

- 字符数启发式只能作为估算，不能当作 Tokenizer；显式预留输出预算，并使用基于减法的防溢出校验。
- 按类型遍历异构 `output` 条目，不能假设文本位于固定索引。
- 流式处理中，`response.output_text.done` 只表示一个文本部分结束，`response.completed` 才是响应级成功终态。
- 不把 done 事件中的完整文本再次追加到 delta 缓冲；应将二者精确比较作为完整性校验。
- 支持多个文本部分时，按 item 和 content 索引维护缓冲。
- 将拒绝、不完整响应、供应商失败、传输失败和无效应用输出视为不同结果。
- 只有已完成的 output-text 条目才能进入结构化解码。
- Structured Outputs 在受支持 Schema 上提供 Schema 一致性；JSON mode 只保证 JSON 有效。
- 构造领域对象前校验准确的允许字段集合和业务规则；拒绝或不完整输出不进入 JSON 解码。

## 影响

- 部分输出或拒绝不会意外成为成功业务命令。
- 流式处理可保持传输无关，并能离线测试。
- 当前工程记录这些约束，为未来实时 adapter 服务，同时不保留过时的每日 DTO 和演示。

## 参考资料

- [OpenAI Responses API reference](https://developers.openai.com/api/reference/resources/responses/methods/create)
- [OpenAI Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs)
