package io.github.jamielu.assistant.web;

import io.github.jamielu.assistant.application.AssistantService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.Objects;

/** 将提示词构造与模型调用委托给应用服务的 HTTP 适配器。 */
@RestController
@RequestMapping("/api/assistant")
public final class AssistantController {
    private final AssistantService assistantService;

    /**
     * 创建 HTTP 适配器。
     *
     * @param assistantService 窄应用边界
     */
    public AssistantController(AssistantService assistantService) {
        this.assistantService = Objects.requireNonNull(assistantService, "assistantService");
    }

    /**
     * 返回一条完整的模型回答。
     *
     * @param request 助手请求
     * @return 同步回答及其可用元数据
     */
    @PostMapping(
            value = "/chat",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ChatReply chat(@RequestBody ChatRequest request) {
        return ChatReply.from(assistantService.chat(messageOf(request)));
    }

    /**
     * 以服务器发送事件流式返回模型分片，不进行聚合。
     *
     * @param request 助手请求
     * @return 有序的文本分片流
     */
    @PostMapping(
            value = "/stream",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestBody ChatRequest request) {
        return assistantService.stream(messageOf(request));
    }

    private static String messageOf(ChatRequest request) {
        return request == null ? null : request.message();
    }
}
