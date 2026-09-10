# ADR-0009：配置档隔离与职责拆分

[English](0009-profile-isolation-and-responsibility-split.md) | [简体中文](0009-profile-isolation-and-responsibility-split.zh-CN.md)

- 状态：已接受
- 日期：2026-09-09
- 取代：ADR-0006 中关于源码位置的部分，以及 ADR-0007 描述的单体 adapter 形态

## 背景

生产代码、确定性 fixture、在线 CLI 和带凭据 smoke 测试具有不同的信任与执行边界。全部放入默认源码集既可能意外联网，也会把测试 fixture 打入制品。原有有状态 OpenAI adapter 同时承担请求构建、会话状态、传输、解码、错误映射和在线资源生命周期，导致变更难以隔离。

## 决策

使用 Maven 配置档分离源码集：

- 默认：生产类库和确定性单元/契约测试；
- `offline`：确定性 Demo 与 fixture 支持；
- `online`：显式 CLI 入口；
- `live`：仅带凭据集成测试。

职责拆分如下：

- `OpenAiResponsesModel` 协调供应商端口；
- `OpenAiConversation` 持有请求构建与续接状态；
- `OpenAiResponseDecoder` 解释 SDK 输出；
- `OpenAiResponsesTransport` 是狭窄 SDK 调用边界；
- `OpenAiExceptionMapper` 产生稳定的供应商中立失败；
- `OpenAiOnlineConfig` 校验外置配置；
- `OpenAiClientFactory` 创建唯一拥有重试责任的 SDK 客户端；
- `OpenAiOnlineRunner` 管理每次运行的客户端生命周期与组合。

ArchUnit 强制供应商中立核心包不得依赖 OpenAI 或在线组合。默认构建必须无需密钥、网络或付费请求。live 请求同时需要显式启用配置档和显式提供凭据。

## 后果

- 默认 CI 确定且可安全重复执行。
- 生产对象只有聚焦的变更原因，并可通过狭窄注入点测试。
- 未选择对应源码集时，配置档专属类不可用。
- 发布验收必须编译各配置档，避免休眠入口发生漂移。
- live 服务兼容性、延迟、费用和模型行为仍需独立审批与留证。

## 验证

默认测试套件包含生命周期、配置、transport、解码、映射、包边界和配置档无关行为测试。offline 与 online 专属测试源码集把各自配置档入口纳入同一覆盖率门禁。发布检查完整验证 `offline` 与 `online`、编译 `live`，未经明确审批绝不发送 live 请求。
