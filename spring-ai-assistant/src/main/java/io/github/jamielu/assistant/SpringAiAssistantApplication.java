package io.github.jamielu.assistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Application entry point for the Spring AI assistant HTTP service. */
@SpringBootApplication
public class SpringAiAssistantApplication {
    /**
     * Starts the application.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(SpringAiAssistantApplication.class, args);
    }
}
