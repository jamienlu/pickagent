package com.pickagent.assistant.web;

import com.pickagent.assistant.application.AssistantService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.Objects;

/** HTTP adapter that delegates prompt construction and model calls to the application service. */
@RestController
@RequestMapping("/api/assistant")
public final class AssistantController {
    private final AssistantService assistantService;

    /**
     * Creates the HTTP adapter.
     *
     * @param assistantService narrow application boundary
     */
    public AssistantController(AssistantService assistantService) {
        this.assistantService = Objects.requireNonNull(assistantService, "assistantService");
    }

    /** Returns one complete model answer. */
    @PostMapping(
            value = "/chat",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ChatReply chat(@RequestBody ChatRequest request) {
        return new ChatReply(assistantService.chat(messageOf(request)));
    }

    /** Streams model fragments as server-sent events without aggregation. */
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
