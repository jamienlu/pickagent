package io.github.jamielu.assistant;

import io.github.jamielu.assistant.config.AssistantChatConfiguration;
import io.github.jamielu.assistant.config.AssistantGenerationProperties;
import io.github.jamielu.assistant.config.AssistantPromptProperties;
import io.github.jamielu.assistant.support.ScriptedToolCallingChatModel;
import io.github.jamielu.assistant.tools.AssistantPolicyTools;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** 验证 Spring AI 2.0 的真实模型—工具—模型循环及其失败边界。 */
class AssistantToolLoopTest {
    private static final String SYSTEM_PROMPT = "Answer accurately and concisely.";
    private static final String USER_MESSAGE = "What is the privacy policy of the local policy tool?";
    private static final String CALL_ID = "policy-call-1";
    private static final String PRIVACY_RESULT = "privacy: this policy lookup is local and read-only; "
            + "it performs no network access, file writes, database access, or other external side effects.";
    private static final String PRIVACY_TOOL_RESPONSE = "\"" + PRIVACY_RESULT + "\"";

    /** 验证一次工具请求被执行一次，结果进入第二轮后产生最终回答。 */
    @Test
    void registeredToolCompletesOneOfflineRoundTrip() {
        var tools = spy(new AssistantPolicyTools(Duration.ofSeconds(30), 1024));
        var model = new ScriptedToolCallingChatModel(
                CALL_ID,
                AssistantPolicyTools.TOOL_NAME,
                "{\"topic\":\"privacy\"}",
                PRIVACY_TOOL_RESPONSE,
                "The policy lookup is local, read-only, and has no external side effects.");
        ChatClient client = configuredClient(model, tools);

        String answer = client.prompt().user(USER_MESSAGE).call().content();

        assertThat(answer)
                .isEqualTo("The policy lookup is local, read-only, and has no external side effects.");
        assertThat(model.calls()).isEqualTo(2);
        verify(tools, times(1)).lookupPolicy("privacy");

        List<org.springframework.ai.chat.prompt.Prompt> prompts = model.prompts();
        assertThat(prompts).hasSize(2);
        assertPromptDefaults(prompts.getFirst());
        assertPromptDefaults(prompts.getLast());
        assertThat(prompts.getFirst().getInstructions())
                .extracting(Object::getClass)
                .containsExactly(SystemMessage.class, UserMessage.class);
        assertThat(prompts.getLast().getInstructions())
                .extracting(Object::getClass)
                .containsExactly(
                        SystemMessage.class,
                        UserMessage.class,
                        AssistantMessage.class,
                        ToolResponseMessage.class);

        AssistantMessage toolRequest = (AssistantMessage) prompts.getLast().getInstructions().get(2);
        assertThat(toolRequest.getToolCalls()).singleElement().satisfies(call -> {
            assertThat(call.id()).isEqualTo(CALL_ID);
            assertThat(call.name()).isEqualTo(AssistantPolicyTools.TOOL_NAME);
            assertThat(call.arguments()).isEqualTo("{\"topic\":\"privacy\"}");
        });
        ToolResponseMessage toolResult = (ToolResponseMessage) prompts.getLast().getInstructions().get(3);
        assertThat(toolResult.getResponses()).singleElement().satisfies(response -> {
            assertThat(response.id()).isEqualTo(CALL_ID);
            assertThat(response.name()).isEqualTo(AssistantPolicyTools.TOOL_NAME);
            assertThat(response.responseData()).isEqualTo(PRIVACY_TOOL_RESPONSE);
        });
    }

    /** 验证模型请求未注册工具时立即失败，白名单工具不会被误执行。 */
    @Test
    void unregisteredToolCannotExecute() {
        var tools = spy(new AssistantPolicyTools(Duration.ofSeconds(30), 1024));
        var model = new ScriptedToolCallingChatModel(
                "unknown-call-1",
                "write_policy",
                "{\"topic\":\"privacy\"}",
                "unused",
                "must not be returned");
        ChatClient client = configuredClient(model, tools);

        assertThatThrownBy(() -> client.prompt().user(USER_MESSAGE).call().content())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No ToolCallback found for tool name: write_policy");
        assertThat(model.calls()).isEqualTo(1);
        verify(tools, never()).lookupPolicy(anyString());
    }

    private static ChatClient configuredClient(
            ScriptedToolCallingChatModel model,
            AssistantPolicyTools tools) {
        return new AssistantChatConfiguration().assistantChatClient(
                ChatClient.builder(model),
                new AssistantPromptProperties(SYSTEM_PROMPT),
                new AssistantGenerationProperties(1024),
                tools);
    }

    private static void assertPromptDefaults(org.springframework.ai.chat.prompt.Prompt prompt) {
        assertThat(prompt.getSystemMessage().getText()).isEqualTo(SYSTEM_PROMPT);
        assertThat(prompt.getUserMessage().getText()).isEqualTo(USER_MESSAGE);
        assertThat(prompt.getOptions()).isInstanceOf(ToolCallingChatOptions.class);
        var options = (ToolCallingChatOptions) prompt.getOptions();
        assertThat(options.getMaxTokens()).isEqualTo(1024);
        assertThat(options.getToolCallbacks())
                .extracting(callback -> callback.getToolDefinition().name())
                .containsExactly(AssistantPolicyTools.TOOL_NAME);
    }
}
