> 主动回忆题
1. 为什么不能用“字符数 ÷ 4”作为精确 Token 数？

tokenizer会将文本切分为有意义的subword units,不同语言等编码为token，方式不同还包含其他产生无法作为精确的依据

2. `instructions` 与 `user` 消息冲突时，哪个优先？

`instructions`系统级指令更优先，确保模型符合开发者设定的核心规则和安全准则

3. 为什么直接读取 `output[0].content[0].text` 不安全？

`output`是异构数值，模型可能返回tool call或者空响应，先检查output[0].type

4. 输入 Token 除正文外还可能包括什么？

   1. 角色：system,user.assistant

   2. 消息边界符

   3. 提示词：instructions 或 developer指令

   4. 工具定义：tool schema

   5. 对话历史

5. `max_output_tokens` 是否只限制用户可见文字？为什么？

不是，所以token数，包含不可见的 工具调用参数，思维链，结束符等


## time1

1. 请求发起阶段：模型选择；输入内容；高级优先级指令instractions or developer指令；工具定义；会话标识

2. 服务端处理阶段：上下文重建previous_response_id会从缓存加载减少完整上下文的token；token计算；模型推理；输出结构化

3. 响应返回：output;output_text;previous_response_id;useage输入输出token和缓存命中等

4. 客户端处理和循环调用：类型安全解析output[].type,toolcalls执行工具结果作为新的user消息追加到下一轮请求input中,循环迭代直到不再返回toolcalls

## time2

1. token 为什么不能只靠字符数精确推算: 不同语言、类型等字符编码后的方式不同，不支持图像和文件，工具和模式会添加一些难以在本地计数的标记。模型特定行为可能会改变分词（例如，推理、缓存）

2. 今天的预算类只是业务计算器，不是 tokenizer。


## time3

1. 当前测试还遗漏了哪个输入边界？

 加法数据溢出最终结果为负数小于预期值。

2. 如果这个边界在生产环境出现，会造成什么错误决策？

把不能执行的执行了消耗大量token


## time4

> [https://developers.openai.com/api/docs/guides/text](https://developers.openai.com/api/docs/guides/text)
1. `output` 数组与 `output_text`

ouput包含工具调用，推理模型生成的推理标记数据和其他元素等。output_text 是模型所有文本输出的聚合

2. Message roles and instruction following

developer 开发者消息、user用户消息 、assistant模型消息级别依次降低。instructions是最高指令，设置了参数相当于在每次对话中都封装了一个developer角色的input。

3. `instructions` 与 `previous_response_id` 的作用范围

`instructions` 当前请求，`previous_response_id` 整个会话期间

