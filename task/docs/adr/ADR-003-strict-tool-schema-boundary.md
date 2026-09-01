# ADR-003：Strict Tool Schema 与执行边界

- 状态：Accepted
- 日期：2026-09-01
- 关联：ADR-002-agent-runtime-loop-and-stop-policy.md

## Context

同一工具存在两个容易漂移的视图：发送给模型的 JSON Schema，以及工具真正执行前由 Registry 检查的参数契约。如果 mapper 和 Registry 分别维护字段名，模型可能看到已经删除的字段，或 Registry 可能接受模型从未被告知的字段。

OpenAI 官方文档推荐启用 Strict mode，但这不是“所有 API 强制默认严格”的陈述。显式 `strict: true` 要求每个参数对象设置 `additionalProperties: false`，并把全部 properties 列入 required。省略 strict 时，Responses 会在可能时尝试规范化为 strict，不能兼容时回退到非严格；Chat Completions 默认仍是非严格模式。[OpenAI Function Calling — Strict mode](https://developers.openai.com/api/docs/guides/function-calling#strict-mode)

Schema adherence 只说明生成参数的结构匹配 Schema。它既不能证明业务值有效，也不能证明当前主体被授权执行工具。

## Decision

1. `ToolDefinition` 是模型契约和执行契约的唯一事实源。OpenAI mapper 从其有序 `parameters` 生成 properties、required 和 string 类型；`ToolRegistry` 从同一参数列表派生 `requiredArguments`，检查精确字段集合与非空白字符串。
2. OpenAI mapper 显式生成 `strict: true`、根 `type: object`、`additionalProperties: false`，并把所有属性按声明顺序放入 required。本阶段不加入可选参数、嵌套对象、数组或复杂 Schema。
3. Runtime/Registry 始终执行本地校验。即使上游声称 Strict，调用也可能来自 Replay、其他供应商、旧缓存、手工构造、错误 mapper 或被篡改的边界输入。Schema 严格不能取代执行前校验。
4. Registry 拒绝 unknown tool、missing、extra 和 blank。尤其 blank 仍满足 JSON Schema 的 `type: string`，它属于本地业务输入约束。
5. 授权是独立的应用策略：在 handler 读取受保护数据或产生副作用前，根据调用主体、资源与动作判断是否允许。本 ADR 不新增授权系统；Registry 的工具 allowlist 也不等于按用户/资源授权。

## 安全结论

“模型生成合法参数”仍不代表“模型获得了执行权限”：Schema 验证的是数据形状，权限必须由可信应用基于真实身份、资源范围和业务策略授予；模型输出不能给自己提升权限。

## 被否决方案

- 只依赖 Strict mode，删除 Registry 校验：把供应商承诺误当成本地安全边界，也无法拒绝 blank 或非模型来源的调用。
- mapper 与 Registry 各自维护参数名：同一契约会产生两个事实源，字段演进时容易漂移。
- 把 Schema 合规当作授权：攻击者只需提供结构正确的高权限参数就可能越权。
- 本次顺带加入并行、重试、幂等或复杂 Schema：这些属于不同的 Runtime、安全或协议决策，不扩大当前范围。

## Consequences

收益：修改 `ToolDefinition` 后，模型看到的 Schema 与 Registry 的字段集合同步变化；一致性测试会在 properties、required、类型或关闭额外字段发生漂移时失败。合法 Replay 完整运行，额外 `admin` 参数在 handler 前被拒绝。

代价：当前契约故意只支持必填字符串；业务值规则和授权仍需单独实现。测试可证明本地 mapper/registry 一致，但不能证明某供应商在真实网络请求中的实际遵循程度。

## Verification

- `StrictToolContractTest`：直接比较 Schema properties/required 与 Registry 契约，并覆盖 valid、missing、extra、blank。
- `StrictToolContractDemoTest`：固定验证演示输出中合法 handler 执行一次、非法 handler 执行零次。
- `StrictToolContractDemo`：离线运行合法 Replay 回合，再提交额外 `admin` 字段；任何期望不成立都会以非零退出结束。
- 默认 Maven 回归必须离线执行，不读取 API Key，不运行 integration profile。
