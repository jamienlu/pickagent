# 测试说明

[English](testing.md) | [简体中文](testing.zh-CN.md)

## 测试分层

- 单元测试覆盖值对象、防御校验、预算、重试决策、幂等、映射、解码、配置和资源生命周期。
- 契约测试在无网络条件下组合多个包和 SDK fixture 对象。
- ArchUnit 测试强制依赖方向，核心包不得导入 OpenAI 或在线入口类型。
- 离线 Demo 提供确定性、可执行的验收证明。
- live smoke 测试需要凭据、默认不执行，也不属于普通验收。

## 必须通过的门禁

```powershell
mvn "-P!jdk-17" clean verify
```

门禁执行 181 个默认测试，对工程生产类强制 100% 行覆盖率和 100% 分支覆盖率，检查 Maven 标准源码布局，并使用 `failOnWarnings=true` 生成 Javadoc。单元测试 fork 有 120 秒硬超时，卡住的测试不能无限占用构建。每个 `@Test` 方法都有中文注释，说明场景、行为和预期。测试必须断言实际行为；大范围 JaCoCo 排除或只检查输出的 smoke 测试不能替代行为测试。

## 附加检查

```powershell
mvn "-P!jdk-17,offline" clean verify exec:java
mvn "-P!jdk-17,online" clean verify
mvn "-P!jdk-17,live" -DskipITs test-compile
```

所有生产类都位于 `src/main/java`，全部 181 个确定性测试与 fixture 都位于 `src/test/java`。因此默认门禁会覆盖离线 Demo、八个 CLI 终态到退出码测试以及源码布局守卫，且无需凭据或网络。`offline` 与 `online` 配置档只选择可执行入口，不再增加代码或测试。

离线证据应包含 `ledger.proof=PASS`、两次模型调用和一次工具调用。online 验证与 live 编译不得发送请求。文档单独迭代时仍可直接执行 `mvn "-P!jdk-17" javadoc:javadoc`，但发布验收无需重复执行。

## 新增测试要求

所有测试与测试 fixture 都放在 `src/test/java` 的对应包中。OpenAI 契约行为使用捕获的 `ResponseCreateParams` 和 SDK 响应 fixture。截止时间测试注入 `NanoClock`，状态和资源隔离测试注入工厂。根据场景验证终态类型、trace、history、steps、usage、副作用次数、精确 call ID 关联和失败分类。

live 测试绝不能成为某项行为的唯一证据。它证明端点兼容性，不证明确定性正确性。
