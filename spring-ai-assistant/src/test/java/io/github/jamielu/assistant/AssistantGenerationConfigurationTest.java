package io.github.jamielu.assistant;

import io.github.jamielu.assistant.application.ChatClientAssistantService;
import io.github.jamielu.assistant.config.AssistantChatConfiguration;
import io.github.jamielu.assistant.config.AssistantGenerationProperties;
import io.github.jamielu.assistant.config.AssistantStreamProperties;
import io.github.jamielu.assistant.integration.springai.SpringAiChatResponseMapper;
import io.github.jamielu.assistant.support.DeterministicChatModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证最大输出 Token 策略的绑定、校验和实际 Prompt 传播。 */
class AssistantGenerationConfigurationTest {

    /** 验证外部属性覆盖默认上限，并实际进入同步模型 Prompt。 */
    @Test
    void externalPropertyOverridesMaxOutputTokensInTheActualPrompt() {
        var model = new DeterministicChatModel();

        contextRunner(model)
                .withPropertyValues("assistant.generation.max-output-tokens=2048")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AssistantGenerationProperties.class);

                    context.getBean(ChatClient.class)
                            .prompt()
                            .user("use configured generation limit")
                            .call()
                            .content();

                    assertThat(model.lastOptions().getMaxTokens()).isEqualTo(2048);
                });
    }

    /** 验证同一个 ChatClient 默认选项同时进入同步调用和已订阅的流式调用。 */
    @Test
    void synchronousAndSubscribedStreamingPromptsShareTheSameMaxTokens() {
        var model = new DeterministicChatModel();

        contextRunner(model)
                .withPropertyValues("assistant.generation.max-output-tokens=1536")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var chatClient = context.getBean(ChatClient.class);
                    var service = new ChatClientAssistantService(
                            chatClient,
                            new SpringAiChatResponseMapper(),
                            new AssistantStreamProperties(Duration.ofSeconds(30)));

                    service.chat("synchronous request");
                    assertThat(model.lastOptions().getMaxTokens()).isEqualTo(1536);

                    var stream = service.stream("streaming request");
                    assertThat(model.streams()).isZero();
                    StepVerifier.create(stream)
                            .expectNext("fixed", " ", "stream")
                            .verifyComplete();

                    assertThat(model.streams()).isEqualTo(1);
                    assertThat(model.lastOptions().getMaxTokens()).isEqualTo(1536);
                });
    }

    /** 验证零值和负值在应用启动阶段被正数约束明确拒绝。 */
    @ParameterizedTest(name = "max-output-tokens={0} 必须启动失败")
    @ValueSource(strings = {"0", "-1"})
    void nonPositiveMaxOutputTokensFailConfigurationValidation(String invalidValue) {
        contextRunner(new DeterministicChatModel())
                .withPropertyValues("assistant.generation.max-output-tokens=" + invalidValue)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining(
                                    "assistant.generation.max-output-tokens must be greater than zero");
                });
    }

    /** 验证孤立配置边界缺少必要值时不会静默构造无限制客户端。 */
    @Test
    void missingMaxOutputTokensFailsConfigurationValidation() {
        contextRunner(new DeterministicChatModel())
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining(
                                    "assistant.generation.max-output-tokens must be configured");
                });
    }

    private static ApplicationContextRunner contextRunner(DeterministicChatModel model) {
        return new ApplicationContextRunner()
                .withUserConfiguration(AssistantChatConfiguration.class)
                .withPropertyValues(
                        "assistant.prompt.system=Offline application policy",
                        "assistant.stream.signal-timeout=30s")
                .withBean(ChatClient.Builder.class, () -> ChatClient.builder(model));
    }
}
