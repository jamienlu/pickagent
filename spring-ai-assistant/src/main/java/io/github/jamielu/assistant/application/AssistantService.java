package io.github.jamielu.assistant.application;

import reactor.core.publisher.Flux;

/** 同步与流式助手文本的窄应用边界。 */
public interface AssistantService {
    /**
     * 获取一条完整的模型回答。
     *
     * @param userContent 原样传入的用户内容
     * @return 完整的助手回答
     */
    AssistantAnswer chat(String userContent);

    /**
     * 按顺序传递模型文本分片，不进行聚合。
     *
     * @param userContent 原样传入的用户内容
     * @return 有序的文本分片流
     */
    Flux<String> stream(String userContent);
}
