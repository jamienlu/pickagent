# 故障排查

[English](troubleshooting.md) | [简体中文](troubleshooting.zh-CN.md)

| 现象 | 常见原因 | 处理方式 |
| --- | --- | --- |
| Maven 按 Java 17 编译 | 用户级 Maven 配置档覆盖了工程 release | 使用 Java 21 并传入 `"-P!jdk-17"`；核对 `java -version` 与 `mvn -version`。 |
| 在线 CLI 报配置错误 | 环境变量缺失、空白或格式非法 | 对照[配置说明](configuration.zh-CN.md)，不要输出密钥。 |
| `AUTHENTICATION` | 凭据无效、撤销、过期或无权限 | 轮换或替换秘密，并核对项目权限。禁止自动重试。 |
| `BILLING_OR_QUOTA` | 余额、账单或组织额度失败 | 检查供应商账单与消费上限。禁止自动重试。 |
| `TRANSIENT_RATE_LIMIT` | 请求数或 Token 限流 | 降低并发/负载，或遵循 SDK 重试策略；检查组织限额。 |
| `TIMEOUT` | transport 超时 | 检查网络和供应商延迟；在总运行截止时间内调整 `OPENAI_TIMEOUT_SECONDS`。 |
| `SERVICE_OVERLOADED` | 供应商 5xx 或过载 | 只能在 SDK 独占重试预算内重试；必要时降级或停止。 |
| `INVALID_REQUEST` | 模型不支持、请求错误或端点无效 | 核对模型、base URL、工具 Schema 和请求选项。 |
| `MODEL_CALL_LIMIT` 或 `TOOL_CALL_LIMIT` | 跨轮预算耗尽 | 根据 trace 检查循环；只有修复行为并评估费用后才能提高上限。 |
| `DEADLINE_EXCEEDED` | 到达总运行截止时间 | 比较请求超时/重试与 `AGENT_MAX_RUN_SECONDS`；减少工作量或有意识地提高时限。 |
| `DUPLICATE_CALL_ID` | 同一次运行重放了 call ID | 按协议或集成缺陷处理，禁止再次执行副作用。 |
| `UNKNOWN_TOOL` 或 `INVALID_ARGUMENTS` | 模型输出违反本地白名单或 Schema | 修正工具暴露、提示词或 Schema，并保持 fail-closed。 |
| live 测试被跳过 | 未提供凭据或模型 | 安全构建中的预期行为；仅在明确审批的 live 运行中同时提供二者。 |
| 覆盖率门禁失败 | 生产分支或行缺少行为测试 | 增加包含实际断言的测试；禁止大范围 JaCoCo 排除。 |

升级问题时应附带制品版本、脱敏配置、命令、终态类型、失败分类和消耗快照；排除秘密和敏感载荷。
