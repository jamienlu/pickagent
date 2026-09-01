# OpenAI API 中文工程手册

> 更新日期：2026-08-31（Asia/Shanghai）  
> 性质：面向 Java/Agent 工程实践的原创中文说明，不是 OpenAI 官方中文译本。  
> 准确性原则：字段、模型、限额、价格和可用区域可能变化；实现前以链接的官方 OpenAI documentation 为准。

## 1. 官方入口

- [开发者快速开始](https://developers.openai.com/api/docs/quickstart)
- [全部概念与实现指南索引](https://developers.openai.com/api/docs/llms.txt)
- [全部接口参考索引](https://developers.openai.com/api/reference/llms.txt)
- [官方 API 文档单文件导出](https://developers.openai.com/api/llms-full.txt)
- [API 更新日志](https://developers.openai.com/api/docs/changelog)
- [弃用公告](https://developers.openai.com/api/docs/deprecations)
- [模型目录](https://developers.openai.com/api/docs/models)
- [生产部署检查表](https://developers.openai.com/api/docs/guides/deployment-checklist)

本手册提供中文概念、工程边界和导航。需要某个字段的完整类型、默认值或枚举时，进入接口参考；需要理解设计方式时，进入概念指南。

## 2. 总体心智模型

OpenAI API 不等于“输入字符串、返回字符串”。一次模型请求可能产生：

- assistant message；
- reasoning item；
- function/custom tool call；
- 内建工具调用与结果；
- refusal；
- incomplete 或 failed 终态；
- usage、状态和元数据。

因此生产代码应遍历 `output` 并按条目类型分支，不应固定读取 `output[0]`。

推荐的应用边界：

```text
Application/Core
    ├── ModelPort：定义应用需要的稳定模型语义
    ├── AgentRuntime：循环、预算、停止、重试和审计策略
    └── ToolPort：工具契约、授权和业务结果
             ↑ implements
Infrastructure Adapters
    ├── OpenAI Responses adapter
    ├── 其他供应商 adapter
    └── Replay/Fake adapter
```

SDK 类型、HTTP 状态和供应商事件应停留在 adapter；只有应用确实需要依赖的稳定语义才进入核心端口。

## 3. 身份认证、密钥和地域

- API Key 只放在服务端环境变量或密钥管理系统，不写入源码、前端、日志和 Git。
- 生产环境使用项目隔离、最小权限、用量告警和密钥轮换。
- 不输出或记录完整 Authorization header。
- 可用国家和地区以官方支持范围为准；不要绕过地区限制。
- 大型组织可查看 [Admin APIs](https://developers.openai.com/api/docs/guides/admin-apis) 和工作负载身份联合文档。

Java 示例应优先使用 `OpenAIOkHttpClient.fromEnv()` 或等价安全配置。使用自定义 base URL 时，必须明确标注这是兼容端点，不能自动推导为 OpenAI 全协议兼容。

## 4. Responses API

官方入口：

- [Responses 概览](https://developers.openai.com/api/reference/responses/overview)
- [创建 Response](https://developers.openai.com/api/reference/resources/responses/methods/create)
- [Responses 资源参考](https://developers.openai.com/api/reference/resources/responses)
- [文本生成](https://developers.openai.com/api/docs/guides/text)

### 4.1 常用请求字段

| 字段 | 中文含义 | 工程注意点 |
| --- | --- | --- |
| `model` | 使用的模型 | 能力、上下文、价格和地域可能不同 |
| `input` | 用户输入或输入条目列表 | 可以包含文本、图片、文件、消息和工具结果 |
| `instructions` | 当前请求的高优先级指令 | 不应假定会随 `previous_response_id` 自动继承 |
| `tools` | 可用工具定义 | 模型提出调用，不代表工具已经执行或获得授权 |
| `tool_choice` | 工具选择约束 | 应用仍负责校验和实际执行 |
| `parallel_tool_calls` | 是否允许并行工具调用 | 开启后需要明确并发、顺序和副作用策略 |
| `previous_response_id` | 续接先前 Response | 与完整输入重放、服务端存储和供应商兼容性有关 |
| `conversation` | 关联会话资源 | 不要与其他续接方式随意混用 |
| `store` | 是否保存 Response | 不等于必然减少 token，也不等于应用持久化已完成 |
| `stream` | 以事件流返回 | Java SDK 通常通过 streaming 方法而非普通 builder 字段消费 |
| `max_output_tokens` | 最大输出预算 | 包含可见输出及可能的 reasoning token |
| `text` | 文本输出格式配置 | Structured Outputs 的 schema 位于该配置体系中 |
| `reasoning` | 推理相关配置 | 支持情况依模型而异 |
| `metadata` | 应用元数据 | 不放敏感数据；控制键值规模 |
| `include` | 请求额外返回内容 | 会影响响应大小、隐私和成本 |

### 4.2 响应处理

按以下层次处理：

```text
Response
├── id / status / model
├── output[]
│   ├── message
│   │   └── content[]
│   │       ├── output_text
│   │       └── refusal
│   ├── reasoning
│   ├── function_call
│   ├── custom_tool_call
│   └── 内建工具相关条目
├── incomplete_details / error
└── usage
```

处理顺序建议：

1. 先检查顶层状态、error 和 incomplete reason。
2. 遍历全部 `output`。
3. 按条目类型分流 message、reasoning、tool call 等。
4. 在 message 内继续按 content 类型分流 output text 与 refusal。
5. 把 usage 交给成本、配额和可观测模块。

不要把 `response.output_text` 一类快捷字段当成完整协议模型；它适合简单展示，不适合丢弃工具调用、拒绝和状态信息。

## 5. Streaming

官方入口：[Streaming API responses](https://developers.openai.com/api/docs/guides/streaming-responses)。

HTTP 流式响应通常使用 SSE，并以类型化语义事件传递。文本场景常见生命周期：

```text
response.created
response.output_text.delta  （可重复）
response.output_text.done
response.completed          （整个 Response 完成）
error                       （错误路径）
```

关键区别：

- delta 是增量，不是可独立验证的最终结果；
- text done 只表示某段文本结束，不等于整个 Response 已完成；
- completed 才是整个响应生命周期的完成信号；
- 客户端取消、网络中断与模型完整终态必须区分；
- 流式内容的审核更困难，因为部分文本可能先到达用户。

工具参数流式返回时，先累积参数增量，再在参数完成事件后解析；不要对半个 JSON 执行业务工具。

## 6. Structured Outputs 与 JSON mode

官方入口：[Structured model outputs](https://developers.openai.com/api/docs/guides/structured-outputs)。

| 模式 | 保证 |
| --- | --- |
| 普通文本 | 无 JSON 保证 |
| JSON mode | 保证合法 JSON，不保证符合业务 schema |
| Structured Outputs | 在支持范围内保证遵循给定 JSON Schema |

严格 schema 的工程基线：

- 所有业务必需字段放入 `required`；
- 使用 `additionalProperties: false` 封闭对象字段；
- 启用 strict；
- 即使供应商声明遵循 schema，应用仍执行领域校验；
- refusal 与 incomplete 在 decoder 之前处理；
- schema 版本需要和领域模型一起演进。

供应商兼容端点声称“支持 JSON”不等于支持 OpenAI 的 strict Structured Outputs。

## 7. Function Calling 与 Agent Runtime

官方入口：[Function calling](https://developers.openai.com/api/docs/guides/function-calling)。

五步流程：

1. 向模型发送输入和可用工具。
2. 模型返回 tool call。
3. 应用校验参数、授权并执行工具。
4. 应用把 tool output 返回模型。
5. 模型给出最终答案或继续请求工具。

`call_id` 是一次调用与一次结果之间的关联键。同一工具可能被多次调用，因此不能用工具名代替 `call_id`，也不能生成新的 ID 冒充原调用。

对于返回 tool call 的 reasoning model，官方文档还要求把相关 reasoning items 与 tool outputs 一起传回。provider-neutral 核心可以隐藏供应商类型，但 OpenAI adapter 必须保留完整的协议续接信息，不能只保存 `call_id`。

生产 Runtime 至少需要：

- 最大步数、token、金额和墙钟时间预算；
- 工具 allowlist 与参数 schema；
- 权限、审批和副作用分类；
- tool call/result 关联；
- refusal、incomplete、tool failure、provider failure、invalid output 的独立语义；
- 重试、幂等、取消和审计策略；
- 防止工具输出被当作高优先级指令。

## 8. 内建工具与扩展工具

完整工具目录以[工具指南索引](https://developers.openai.com/api/docs/llms.txt)为准，常见类别包括：

- Web search：获取网络信息并处理引用。
- File search：搜索托管文件和 vector store。
- Code Interpreter：在隔离环境执行 Python。
- Computer use：基于截图提出计算机操作。
- Image generation：在响应流程中生成或编辑图片。
- MCP/Connectors：接入远程工具和数据源。
- Apply Patch、Shell、Local shell：面向编码或计算机任务的执行能力。
- Function tools：使用 JSON Schema 定义参数。
- Custom tools：以自由文本或受约束语法作为输入。

任何能写数据、发送消息、付款、删除或执行代码的工具都应在应用侧增加权限、确认、审计、超时和幂等控制。模型工具选择不是授权决定。

## 9. Conversations、Background 与 Compaction

- [Conversations API reference](https://developers.openai.com/api/reference/resources/conversations)
- [Background mode](https://developers.openai.com/api/docs/guides/background)
- [Compaction](https://developers.openai.com/api/docs/guides/compaction)

Conversations 用于托管会话状态；`previous_response_id` 用于响应链续接；应用自管 transcript 则拥有最高可移植性。三者在状态归属、删除、审计、成本和供应商迁移方面不同。

Background mode 适合长任务，需要轮询、取消或 webhook 策略。Compaction 解决长会话上下文增长，但摘要/压缩不是无损存储，领域关键状态仍应由应用显式保存。

## 10. Files、Uploads、Vector Stores 与 RAG

- [Files reference](https://developers.openai.com/api/reference/resources/files)
- [Uploads reference](https://developers.openai.com/api/reference/resources/uploads)
- [Vector Stores reference](https://developers.openai.com/api/reference/resources/vector_stores)
- [File search guide](https://developers.openai.com/api/docs/guides/tools-file-search)
- [Retrieval guide](https://developers.openai.com/api/docs/guides/retrieval)

典型托管 RAG 流程：上传文件 → 加入 vector store → 等待处理 → 搜索或由 file search 工具检索 → 返回带来源的回答。

工程上仍需考虑：解析质量、chunk 策略、删除同步、权限过滤、租户隔离、引用正确率、评测集和供应商迁移。托管 vector store 不等于你的业务知识库已满足合规要求。

## 11. Batch API

- [Batch guide](https://developers.openai.com/api/docs/guides/batch)
- [Batch reference](https://developers.openai.com/api/reference/resources/batches)

Batch 适合不要求即时返回的大规模离线任务。设计时关注：请求文件格式、自定义 ID、结果与错误文件、任务过期、重复提交和部分失败恢复。

Batch 与在线重试策略不同；不能假定所有条目一起成功或按输入顺序完成。

## 12. Embeddings

- [Embeddings guide](https://developers.openai.com/api/docs/guides/embeddings)
- [Embeddings reference](https://developers.openai.com/api/reference/resources/embeddings)

Embedding 把输入映射为向量。常见用途包括语义检索、聚类、推荐和相似度检测。

重要边界：

- 同一索引内保持 embedding model 与维度一致；
- 切换模型通常需要重建向量；
- 归一化、距离函数和阈值必须用真实数据评测；
- 向量相似不等于业务相关或事实正确；
- 不要忽略原文权限和删除传播。

## 13. Images 与 Videos

- [Image generation guide](https://developers.openai.com/api/docs/guides/image-generation)
- [Images reference](https://developers.openai.com/api/reference/resources/images)
- [Videos reference](https://developers.openai.com/api/reference/resources/videos)

图片能力可能包含生成、编辑、输入理解和在 Responses 中作为工具调用。视频通常是异步任务，需要创建、查询状态、获取结果和处理失败/过期。

保存产物时记录模型、请求、内容政策结果、尺寸、格式、生成时间和业务权限。不要假定临时 URL 永久有效。

## 14. Audio 与 Realtime

- [Audio and speech guide](https://developers.openai.com/api/docs/guides/audio)
- [Audio reference](https://developers.openai.com/api/reference/resources/audio)
- [Realtime guide](https://developers.openai.com/api/docs/guides/realtime)
- [Realtime reference](https://developers.openai.com/api/reference/resources/realtime)

Audio API 覆盖语音转文字、文字转语音及相关音频处理。Realtime 面向低延迟双向交互，可通过 WebRTC、WebSocket 或相关呼叫接口建立会话。

Realtime 工程重点：会话配置、客户端临时凭证、音频缓冲、语音活动检测、打断、事件顺序、重连、工具调用、延迟和回声处理。不要把普通 HTTP Responses 的状态机直接照搬为 Realtime 状态机。

## 15. Moderation 与安全

- [Moderation guide](https://developers.openai.com/api/docs/guides/moderation)
- [Moderations reference](https://developers.openai.com/api/reference/resources/moderations)
- [Safety best practices](https://developers.openai.com/api/docs/guides/safety-best-practices)

Moderation 是安全信号，不是完整业务政策。应用还要处理：用户权限、数据分类、提示注入、工具越权、输出验证、人工审批和审计。

流式输出可能在完整审核结果前到达用户；高风险场景需要缓冲、分段策略或其他保护措施。

## 16. Fine-tuning、Evals 与模型改进

- [Fine-tuning guide](https://developers.openai.com/api/docs/guides/fine-tuning)
- [Evals guide](https://developers.openai.com/api/docs/guides/evals)
- [Evals reference](https://developers.openai.com/api/reference/resources/evals)

先建立可重复评测，再讨论 prompt、RAG 或 fine-tuning。评测至少包含：任务成功率、结构遵循、引用正确率、拒绝质量、工具选择、延迟和成本。

Fine-tuning 不是注入实时知识的首选方式；实时或可删除知识通常更适合 RAG。训练、验证和生产数据要避免泄漏，并保存数据集与模型版本关系。

## 17. Webhooks

官方入口：[Webhooks guide](https://developers.openai.com/api/docs/guides/webhooks) 与 [events reference](https://developers.openai.com/api/reference/resources/webhooks)。

必须验证签名，防止伪造；按事件 ID 去重，防止重复投递；处理乱序、重放和延迟；业务处理失败时使用可审计的重试队列。返回 HTTP 成功只表示接收成功，不代表内部业务必然完成。

## 18. Models、Token、价格与限流

- [Models](https://developers.openai.com/api/docs/models)
- [Token counting](https://developers.openai.com/api/docs/guides/token-counting)
- [Prompt caching](https://developers.openai.com/api/docs/guides/prompt-caching)
- [Rate limits](https://developers.openai.com/api/docs/guides/rate-limits)

模型名称、能力、上下文长度、价格和限流会变化。本手册不固化“最新模型”或价格数字。

预算公式示例：

```text
remaining = contextLimit - inputTokens - reservedOutputTokens
```

在 Java `long` 中避免先做可能溢出的加法。完成非负校验后，可先检查：

```text
reservedOutputTokens <= contextLimit
inputTokens <= contextLimit - reservedOutputTokens
```

Prompt caching、服务端存储和 `previous_response_id` 是不同概念；不要把它们都描述为“自动省 token”。

## 19. 错误、重试与幂等

错误至少分为：

- 客户端输入或认证错误；
- 限流；
- 服务端暂时故障；
- 网络、连接和超时；
- provider 拒绝或 incomplete；
- 协议映射错误；
- 结构或领域输出无效；
- 工具业务失败；
- 应用自身编程错误。

只对明确可重试且满足预算的操作重试。指数退避需要随机抖动，并尊重服务端提示。带副作用工具在重试前必须有业务幂等键或查询状态能力；`call_id` 去重不能单独保证跨进程 exactly-once。

## 20. Java 集成建议

1. SDK adapter 只做请求构造、响应遍历和错误翻译。
2. 核心定义自己的 command/result/port，不暴露 SDK DTO。
3. 使用 sealed interface/record 表达 Completed、Refused、Incomplete、Failed 和 ToolCall。
4. 单元测试使用 Fake；协议契约使用 Replay；真实网络测试标记为 integration。
5. 默认 `mvn test` 不读取 API Key、不访问网络、不产生费用。
6. Streaming resource 使用 try-with-resources，确保关闭连接。
7. 不依赖 output 固定顺序；按类型安全遍历。
8. 日志记录 request/response ID、模型、状态、token、延迟和错误分类，但不记录密钥和敏感原文。

项目中的离线 Runtime 示例可参考：

- `com.pickagent.w2.core.AgentRuntime`
- `com.pickagent.w2.core.AgentContext`
- `com.pickagent.w2.core.ToolRegistry`
- `com.pickagent.w2.AgentRuntimeDemo`

## 21. OpenAI 与兼容供应商

“OpenAI-compatible”通常只表示某部分请求形状或 SDK 用法兼容，不能推导出以下能力完全相同：

- Responses output item 类型；
- strict Structured Outputs；
- refusal/incomplete；
- reasoning item 续接；
- Streaming 事件集合与顺序；
- tool schema 与并行调用；
- `previous_response_id`、Conversations、store；
- 内建工具；
- 错误码、限流、计费和数据保留。

为每个供应商维护 capability matrix，并通过供应商专属 integration test 验证；公共契约测试只能证明 adapter 对测试夹具的规范化结果。

## 22. 本学习计划中的使用顺序

| 周次 | 首选章节 |
| --- | --- |
| W1–2 | Responses、Streaming、Structured Outputs、Function Calling |
| W3–5 | Agent Runtime、错误、重试、幂等、工具安全 |
| W6–9 | Java 边界、Spring AI、Memory、Observability |
| W10–13 | Files、Embeddings、Vector Stores、RAG、Evals |
| W14–17 | Conversations、Background、Webhooks、MCP、审批 |
| W18–20 | Agent 模式、工具编排、多智能体取舍 |
| W21–24 | Moderation、安全、Evals、成本、综合项目 |

## 23. 更新规则

每次需要实现具体 API 时：

1. 打开官方 guides index 搜索主题。
2. 打开匹配的概念指南。
3. 再打开对应 endpoint reference 核对字段和枚举。
4. 检查 changelog、deprecations 和模型页。
5. 在本手册记录“更新日期、官方链接、项目决定”，不要复制整页官方原文。

如果本手册与官方 OpenAI documentation 冲突，以官方当前页面为准。
