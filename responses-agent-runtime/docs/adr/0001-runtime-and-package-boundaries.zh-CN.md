# ADR-0001：Runtime 与包边界

[English](0001-runtime-and-package-boundaries.md) | [简体中文](0001-runtime-and-package-boundaries.zh-CN.md)

- 状态：已接受
- 日期：2026-09-08

## 背景

源项目通过每日练习逐步增长。运行时策略、供应商 SDK 映射、工具、fixture 和学习记录都按周组织，而不是按职责组织。这种结构难以看清依赖方向，也不利于识别可复用的 Runtime。

## 决策

采用稳定的能力包：

- `io.github.jamielu.agent.api`：供应商中立的不可变结构。
- `io.github.jamielu.agent.runtime`：Agent 循环、生命周期、历史和停止预算。
- `io.github.jamielu.agent.tool`：工具白名单、校验、预检和分发。
- `io.github.jamielu.agent.openai`：OpenAI SDK 映射和 Responses 协议状态。
- `io.github.jamielu.agent.reliability`：重试和幂等策略。
- `io.github.jamielu.agent.example`：可复现 adapter 和可执行演示。

模型端口定义在使用它的 Runtime 旁边，供应商 adapter 实现或驱动该端口。Runtime 和 API 代码不得导入 OpenAI SDK 类。

## 影响

- SDK 升级被限制在 OpenAI adapter 内。
- Runtime 测试可以只构造小型供应商中立值对象。
- 供应商协议状态不会被错误地压平到通用 Agent 历史中。
- 包名表达长期职责，而不是学习日期。
- 新增其他供应商时，无须修改 API record 或工具 handler，除非核心确实出现新的通用能力。
