# pickagent
ai teach agent
## W1D1 Learning Log
> [https://developers.openai.com/api/docs/guides/text](https://developers.openai.com/api/docs/guides/text)

为什么不能固定读取 output[0].content[0].text？
	output工具调用，推理模型生成的推理标记数据和其他元素等应先遍历并按类型处理输出条目
instructions、developer、user、assistant 分别承担什么职责？
	instructions当前请求的高优先级指令，优先于 input，近似 developer 消息；不会随 previous_response_id 自动沿用;developer应用规则;user终端用户输入;assistant模型生成消息
为什么通过 previous_response_id 延续对话时，不能假设上一轮 instructions 自动沿用？
	instructions只对当前对话有效
对 Java 设计有什么影响？至少写出两个结论。
	获取模型结果要注意数据类型遵循outputtype类型遍历原则，每轮显式构建所需指令。