# 配置说明

[English](configuration.md) | [简体中文](configuration.zh-CN.md)

在线配置只从进程环境变量读取。默认构建与 `offline` 配置档不会读取凭据，也不会访问网络服务。

## 环境变量

| 变量 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- |
| `OPENAI_API_KEY` | 仅在线必填 | 无 | API 凭据。禁止记录、持久化或提交。 |
| `OPENAI_MODEL` | 仅在线必填 | 无 | 每次 Responses 请求使用的模型标识。 |
| `OPENAI_INSTRUCTIONS` | 否 | 不设置 | 每次续接请求都会重复发送的指令。 |
| `OPENAI_BASE_URL` | 否 | SDK 默认值 | 可选兼容端点根地址；使用 OpenAI 时应省略。 |
| `OPENAI_TIMEOUT_SECONDS` | 否 | `30` | 单次 SDK 请求的正数超时时间。 |
| `OPENAI_MAX_RETRIES` | 否 | `2` | SDK transport 独占的非负重试次数。 |
| `OPENAI_MAX_OUTPUT_TOKENS` | 否 | `1024` | 单个 response 的正数输出 Token 上限。 |
| `OPENAI_STORE` | 否 | `true` | 启用 `previous_response_id` 所需的服务端状态。 |
| `AGENT_MAX_MODEL_CALLS` | 否 | `8` | 跨轮模型调用正数预算。 |
| `AGENT_MAX_TOOL_CALLS` | 否 | `4` | 应用工具调用非负预算。 |
| `AGENT_MAX_RUN_SECONDS` | 否 | `120` | 基于单调时钟的正数运行截止时间。 |

必填值缺失、空白或格式非法时，会在首个请求前失败。`OpenAiOnlineConfig.toString()` 会遮蔽密钥与指令，但调用方仍不应把完整配置对象写入遥测。

## 源码布局与执行配置档

所有生产代码使用 `src/main/java`，所有测试与测试 fixture 使用 `src/test/java`。配置档不得再增加非标准源码目录。

| 选择 | 执行职责 | 网络行为 |
| --- | --- | --- |
| 默认 | 编译全部生产与测试代码；Surefire 执行 `*Test`。 | 只执行确定性测试；无凭据、无网络。 |
| `offline` | 为 `exec:java` 选择 `OfflineResponsesDemo`。 | 确定性本地演示；无凭据、无网络。 |
| `online` | 为 `exec:java` 选择 `OpenAiAgentCli`。 | 只有显式执行 CLI 才会发起请求。 |
| `live` | 启用 Failsafe 执行 `OpenAiLiveSmokeIT`。 | 未提供凭据与模型时自动跳过。 |

配置档隔离执行方式，而不隔离源码位置。普通 CI 不得启用 `live`。

## 安全示例

```powershell
mvn "-P!jdk-17" clean verify
mvn "-P!jdk-17,offline" clean verify exec:java
mvn "-P!jdk-17,online" clean verify
mvn "-P!jdk-17,live" -DskipITs test-compile
```

只有在已审批、具备秘密注入与费用控制的环境中才运行在线请求：

```powershell
$env:OPENAI_API_KEY = '<由秘密管理系统注入>'
$env:OPENAI_MODEL = '<已审批模型>'
mvn "-P!jdk-17,online" -Dexec.args='"你好"' compile exec:java
```

## 重试所有权

SDK 客户端是 transport 请求的唯一重试方。Runtime 预算统计逻辑模型调用，而 `OPENAI_MAX_RETRIES` 限制单次 transport 调用内部的尝试。禁止在 `OpenAiOnlineRunner` 外再套一层重试，否则流量会倍增；若确有需要，必须重新设计端到端重试预算。
