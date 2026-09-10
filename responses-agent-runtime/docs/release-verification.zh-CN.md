# 发布验收

[English](release-verification.md) | [简体中文](release-verification.zh-CN.md)

在仓库根目录使用 Java 21 执行。发布候选只有在所有非 live 项通过后才可接受；若部署要求 live 证据，必须在明确审批后独立产生。

## 确定性门禁

```powershell
mvn "-P!jdk-17" clean verify
mvn "-P!jdk-17,offline" clean verify exec:java
mvn "-P!jdk-17,online" clean verify
mvn "-P!jdk-17,live" -DskipITs test-compile
git diff --check
```

验收证据：

- 默认门禁执行 181 个测试，失败、错误和跳过均为零；offline 与 online 配置档复用同一套标准生产与测试目录。
- 默认门禁对全部生产类报告 JaCoCo bundle 行覆盖率与分支覆盖率为 `1.00`。
- 默认 `verify` 会生成 Javadoc，并在出现任一警告时失败。
- 离线输出包含 `ledger.proof=PASS`、`model.calls=2` 和 `tool.calls=1`。
- 在线 CLI 行为完成验证且未发送请求；live 集成测试在默认门禁中只编译、不执行。
- JAR 包含 `src/main/java` 下的全部生产类（包括离线与在线入口），并排除 `src/test/java` 下的全部测试类。
- 每份持续维护的英文 Markdown 都有同基础名 `.zh-CN.md` 副本。
- 相对 Markdown 链接、代码围栏、尾随空白和 Mermaid 图均通过仓库检查。
- Java 源码中没有纯英文注释或 Javadoc；每个测试方法都有规定的中文场景注释。
- Git 状态中没有意外生成物或凭据文件。仓库外层无关变更应保留并报告，不得擅自修改。

## 可选带凭据 smoke

普通验收不得执行该项。只有明确审批后，才可提供已审批密钥与模型，在供应商侧严格费用和限流保护下运行 `live` 配置档。只记录脱敏结果元数据，绝不记录密钥或敏感内容。

## 剩余风险签署

生产发布前，所属应用必须接受或关闭以下风险：进程内幂等、已完成副作用不回滚、授权/审批由应用负责、尚无流式或结构化最终输出，以及 live 延迟/费用/模型行为证据有限。
