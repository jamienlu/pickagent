import com.alibaba.fastjson2.JSONObject;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonObject;
import com.openai.core.JsonValue;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFormatTextJsonSchemaConfig;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseTextConfig;
import com.openai.models.responses.ResponseUsage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author jamieLu
 * @create 2026-08-25
 */
public class ResponseCreateParamsTest {
   private static OpenAIClient client;

    @BeforeAll
    public static void prepare() {
        client = OpenAIOkHttpClient.builder().apiKey(System.getenv("DEEPSEEK_KEY")).baseUrl("https://api.deepseek.com").build();
    }
    @Test
    public void model() {
        String model = "deepseek-v4-flash";
        Response response = client.responses().create(ResponseCreateParams.builder().model(model).
                input("1+1=2").
                build());
        assertEquals("deepseek-v4-flash",response.model().asString());
    }

    @Test
    public void input() {
        String model = "deepseek-v4-flash";
        Response response = client.responses().create(ResponseCreateParams.builder().model(model).
                input(ResponseCreateParams.Input.ofResponse(
                        List.of(
                                ResponseInputItem.ofEasyInputMessage(
                                        EasyInputMessage.builder()
                                                .role(EasyInputMessage.Role.DEVELOPER)
                                                .content("cannot introduce foods")
                                                .build()),
                                ResponseInputItem.ofEasyInputMessage(
                                        EasyInputMessage.builder()
                                                .role(EasyInputMessage.Role.USER)
                                                .content("introduce some foods")
                                                .build())))).
                build());
        // 验证成功结果里存在不能推荐食物
        assertNotNull(response.output());
    }
    @Test
    public void instructions() {
        String model = "deepseek-v4-flash";
        Response response = client.responses().create(ResponseCreateParams.builder().model(model).
                input("1+1=2").
                instructions("You are a helpful assistant.").
                build());
        assertEquals("You are a helpful assistant.",response.instructions().get().asString());
    }
    @Test
    public void maxoutputtokens() {
        String model = "deepseek-v4-flash";
        Response response = client.responses().create(ResponseCreateParams.builder().model(model).
                input("say some animal foods").
                maxOutputTokens(200).
                reasoning(Reasoning.builder().effort(ReasoningEffort.LOW).build()).
                build());
        ResponseUsage usage = response.usage().get();
        assertTrue(200 >= usage.outputTokens());
        assertTrue( usage.outputTokensDetails().reasoningTokens() > 0);
    }
    @Test
    public void previousResponseIdAndStore() {
        String model = "deepseek-v4-flash";
        Response response = client.responses().create(ResponseCreateParams.builder().model(model).
                input("1+1=?").
                maxOutputTokens(200).
                store(true).
                build());
        String responseId = response.id();
        response = client.responses().create(ResponseCreateParams.builder().model(model).
                input("什么情况下等于3呢").
                maxOutputTokens(200).
                previousResponseId(responseId).
                store(true).
                build());
        response.output().stream()
                .flatMap(item -> item.message().stream())
                .flatMap(message -> message.content().stream())
                .flatMap(content -> content.outputText().stream())
                .forEach(text -> assertTrue(text.text().contains("等于3")));
    }
    @Test
    public void text() {
        Map<String, Object> schema =
                Map.of(
                        "type",
                        "object",
                        "properties",
                        Map.of(
                                "name", Map.of("type", "string"),
                                "date", Map.of("type", "string"),
                                "participants", Map.of("type", "array", "items", Map.of("type", "string"))),
                        "required",
                        List.of("name", "date", "participants"),
                        "additionalProperties",
                        false);

        ResponseCreateParams params =
                ResponseCreateParams.builder()
                        .model("deepseek-v4-flash")
                        .inputOfResponse(
                                List.of(
                                        ResponseInputItem.ofEasyInputMessage(
                                                EasyInputMessage.builder()
                                                        .role(EasyInputMessage.Role.SYSTEM)
                                                        .content("Extract the event information.")
                                                        .build()),
                                        ResponseInputItem.ofEasyInputMessage(
                                                EasyInputMessage.builder()
                                                        .role(EasyInputMessage.Role.USER)
                                                        .content("Alice and Bob are going to a science fair on Friday.")
                                                        .build())))
                        .text(
                                ResponseTextConfig.builder()
                                        .format(
                                                ResponseFormatTextJsonSchemaConfig.builder()
                                                        .name("event")
                                                        .strict(true)
                                                        .schema(JsonValue.from(schema).convert(ResponseFormatTextJsonSchemaConfig.Schema.class))
                                                        .build())
                                        .build())
                        .build();

        client.responses().create(params).output().stream()
                .flatMap(item -> item.message().stream())
                .flatMap(message -> message.content().stream())
                .flatMap(content -> content.outputText().stream())
                .forEach(text -> JSONObject.parseObject(text.text()).forEach((key, value) -> {
                    switch (key) {
                        case "name" -> assertEquals("science fair", value);
                        case "date" -> assertEquals("Friday", value);
                        case "participants" -> {
                            assertTrue(value instanceof List);
                            List<?> participants = (List<?>) value;
                            assertTrue(participants.contains("Alice"));
                            assertTrue(participants.contains("Bob"));
                        }
                    }
                }));
    }
}
