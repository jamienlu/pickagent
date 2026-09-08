# ADR-0003：工具契约与执行权限

[English](0003-tool-contract-and-authority.md) | [简体中文](0003-tool-contract-and-authority.zh-CN.md)

- 状态：已接受
- 日期：2026-09-08

## 背景

模型生成的调用属于不可信输入。严格函数 Schema 能提高参数形状的可靠性，但不能证明用户有权执行操作，也不能证明操作在当前业务状态下有效。

## 决策

强制区分以下边界：

1. OpenAI mapper 校验供应商协议字段并解析参数 JSON。
2. `ToolRegistry` 只允许已注册名称，并要求运行时参数集合与声明的契约完全一致。
3. `ToolRegistry.prepareAll` 在不调用 handler 的前提下校验完整批次。
4. 应用授权、租户检查、审批、限额和业务状态校验必须在敏感副作用前完成。
5. Handler 不能选择或改写 `call_id`；Registry 从已验证调用中复制该标识到结果。
6. 已知工具失败使用带类型的 checked exception；未知编程错误继续向上抛出，不能被错误标记为传输失败。

严格工具 Schema 使用 `strict: true`，要求所有属性均为 required，并为每个 object 设置 `additionalProperties: false`。可选值必须显式建模，不能通过静默省略表达。

## 影响

- 不会把 Schema 一致性误认为授权。
- 模型不能选择任意 Java 方法或类。
- 缺失、多余和空白参数会在 handler 执行前被拒绝。
- 安全策略仍由应用拥有，可以独立于 OpenAI Schema 映射演进。

## 参考资料

- [OpenAI Function calling：Strict mode](https://developers.openai.com/api/docs/guides/function-calling#strict-mode)
