# 文档索引

[English](README.md) | [简体中文](README.zh-CN.md)

本目录是 Responses Agent Runtime 的长期知识库。每份持续维护的文档都有英文原文，以及使用相同基础文件名并增加 `.zh-CN` 后缀的简体中文版本。

## 阅读入口

- [项目 README](../README.zh-CN.md)：能力、包边界、构建方式和当前范围。
- [项目架构](architecture.zh-CN.md)：系统上下文、包和类关系、运行时链路与变更影响指引。
- [项目思维导图](project-mind-map.zh-CN.md)：当前能力、约束和可持续迭代路线。
- [配置说明](configuration.zh-CN.md)：环境变量、默认值、标准源码布局、执行配置档和重试所有权。
- [运维说明](operations.zh-CN.md)：部署控制、可观测性、发布、回滚与事故留证。
- [故障排查](troubleshooting.zh-CN.md)：稳定失败分类与处理方式。
- [测试说明](testing.zh-CN.md)：测试分层、覆盖率策略、命令与贡献规则。
- [发布验收](release-verification.zh-CN.md)：确定性发布清单与剩余风险签署。
- [项目与 OpenAI 回忆题](project-recall-qa.zh-CN.md)：基于本项目和 OpenAI Docs 的 50 道带答案复习题。

## 架构决策记录

- [ADR-0001：Runtime 与包边界](adr/0001-runtime-and-package-boundaries.zh-CN.md)
- [ADR-0002：Responses 协议账本与调用批次](adr/0002-responses-protocol-ledger.zh-CN.md)
- [ADR-0003：工具契约与执行权限](adr/0003-tool-contract-and-authority.zh-CN.md)
- [ADR-0004：重试与幂等策略](adr/0004-retry-and-idempotency.zh-CN.md)
- [ADR-0005：响应终态与解码边界](adr/0005-response-terminal-and-decoding.zh-CN.md)
- [ADR-0006：离线验证边界](adr/0006-offline-verification-boundary.zh-CN.md)
- [ADR-0007：有状态 Responses 续接](adr/0007-stateful-responses-continuation.zh-CN.md)
- [ADR-0008：全局运行预算](adr/0008-global-run-budget.zh-CN.md)
- [ADR-0009：配置档隔离与职责拆分](adr/0009-profile-isolation-and-responsibility-split.zh-CN.md)
- [ADR-0010：Maven 标准源码布局](adr/0010-standard-maven-source-layout.zh-CN.md)

## 文档维护契约

出现以下变更时，文档必须与代码在同一次变更中更新：

1. 包或依赖方向变化：更新 `architecture.zh-CN.md`，并更新 ADR-0001 或新增取代它的 ADR。
2. 新增、删除、重命名类，或类职责发生变化：更新类目录和相关类图。
3. Runtime 或协议状态转换变化：更新对应时序图和 ADR。
4. 能力或限制变化：同时更新中英文 README 和中英文思维导图。
5. 英文文档发生实质变化：在同一次变更中更新对应 `.zh-CN.md`。

ADR 是只追加的历史决策。架构变化后不要重写已接受的决策；应新增一份声明取代关系的 ADR，并更新索引。
