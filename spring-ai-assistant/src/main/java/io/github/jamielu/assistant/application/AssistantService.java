package io.github.jamielu.assistant.application;

import reactor.core.publisher.Flux;

/** Narrow application boundary for synchronous and streaming assistant text. */
public interface AssistantService {
    /**
     * Obtains one complete model answer.
     *
     * @param userContent exact user content
     * @return complete assistant content
     */
    String chat(String userContent);

    /**
     * Streams model text fragments without aggregating them.
     *
     * @param userContent exact user content
     * @return ordered content fragments
     */
    Flux<String> stream(String userContent);
}
