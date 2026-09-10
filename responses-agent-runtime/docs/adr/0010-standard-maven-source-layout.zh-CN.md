# ADR-0010：Maven 标准源码布局

[English](0010-standard-maven-source-layout.md) | [简体中文](0010-standard-maven-source-layout.zh-CN.md)

- 状态：已接受
- 日期：2026-09-10
- 取代：ADR-0009 中关于自定义源码目录的部分

## 背景

ADR-0009 使用自定义 Maven 源码目录，把离线 Demo、在线 CLI 和 live smoke 测试放在配置档之后。该设计虽然显式表达了执行边界，却不符合本项目期望的常规布局：生产代码只能位于 `src/main/java`，测试与测试 fixture 只能位于 `src/test/java`。

网络安全不依赖非常规源码位置，而取决于确定性测试是否创建真实客户端，或是否显式执行带凭据入口。

## 决策

- 把 `OfflineResponsesDemo` 和 `OpenAiAgentCli` 移入 `src/main/java`。
- 把它们的测试以及 `OpenAiLiveSmokeIT` 移入 `src/test/java`。
- 删除全部由 `build-helper-maven-plugin` 增加源码目录的配置。
- `offline` 和 `online` 配置档只负责为 `exec:java` 选择主类。
- `live` 配置档只负责启用 Failsafe 执行 `*IT` 测试；普通 Surefire 不包含 `*IT`。
- 默认确定性门禁编译并测试全部生产入口。任何默认测试都不得创建真实 OpenAI 客户端或发送请求。

## 后果

- IDE、Maven、静态分析、Javadoc、JaCoCo 和打包工具无需配置档专属源码发现即可识别完整工程。
- 离线与在线入口都是生产类，因此都会进入生产 JAR。
- 默认测试门禁在无凭据、无网络条件下覆盖离线 Demo 和在线 CLI 行为。
- 执行配置档仍是操作者控制，但不再承担代码位置隔离。
- live 请求仍同时需要显式启用配置档、显式凭据和模型配置。

## 验证

仓库检查拒绝 `src/main/java` 之外的生产 `.java` 文件，以及 `src/test/java` 之外的测试 `.java` 文件。默认 `clean verify` 必须在没有 `OPENAI_API_KEY`、网络访问或付费请求时通过。offline 配置档仍须输出 `ledger.proof=PASS`；live 测试必须能编译，同时保持不被普通 Surefire 执行。
