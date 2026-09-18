package io.github.jamielu.assistant;

import io.github.jamielu.assistant.config.AssistantChatConfiguration;
import io.github.jamielu.assistant.config.AssistantPromptProperties;
import io.github.jamielu.assistant.support.DeterministicChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证应用提示词配置的外部覆盖与启动期校验。 */
class AssistantPromptConfigurationTest {

    /** 验证外部属性覆盖默认系统提示词，并实际进入模型 Prompt。 */
    @Test
    void externalPropertyOverridesTheApplicationSystemPrompt() {
        var model = new DeterministicChatModel();

        contextRunner(model)
                .withPropertyValues("assistant.prompt.system=External deployment policy")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AssistantPromptProperties.class);
                    assertThat(context).hasSingleBean(ChatClient.class);

                    String result = context.getBean(ChatClient.class)
                            .prompt()
                            .user("exact user input")
                            .call()
                            .content();

                    assertThat(result).isEqualTo("fixed answer");
                    assertThat(model.lastSystemMessage().getText())
                            .isEqualTo("External deployment policy");
                    assertThat(model.lastUserMessage().getText()).isEqualTo("exact user input");
                });
    }

    /** 验证空白系统提示词在应用启动阶段产生清晰的配置校验失败。 */
    @Test
    void blankSystemPromptFailsConfigurationValidationClearly() {
        var model = new DeterministicChatModel();

        contextRunner(model)
                .withPropertyValues("assistant.prompt.system=   ")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("assistant.prompt.system must not be blank");
                });
    }

    private static ApplicationContextRunner contextRunner(DeterministicChatModel model) {
        return new ApplicationContextRunner()
                .withUserConfiguration(AssistantChatConfiguration.class)
                .withPropertyValues("assistant.stream.signal-timeout=30s")
                .withBean(ChatClient.Builder.class, () -> ChatClient.builder(model));
    }
}
