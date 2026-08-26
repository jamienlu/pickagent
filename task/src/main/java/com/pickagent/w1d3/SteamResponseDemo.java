package com.pickagent.w1d3;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.StreamResponse;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseStreamEvent;
import com.pickagent.w1d2.ResponseRequestFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;

/**
 * @author jamieLu
 * @create 2026-08-26
 */
public class SteamResponseDemo {
    public static void main(String[] args) {
        OpenAIClient client = OpenAIOkHttpClient.builder().apiKey(System.getenv("DEEPSEEK_KEY")).baseUrl("https://api.deepseek.com").build();
        ResponseCreateParams params = ResponseRequestFactory.builder()
                .input("1+1=?").model("deepseek-v4-flash").maxOutputTokens(100).build()
                .builderResponseParams();
        // 无key情况：Exception in thread "main" java.lang.IllegalStateException: At least one credential source must be specified: credential (apiKey), workloadIdentity, or adminApiKey
        //	at com.openai.core.ClientOptions$Builder.effectiveCredential(ClientOptions.kt:559)
        //	at com.openai.core.ClientOptions$Builder.build(ClientOptions.kt:697)
        //	at com.openai.client.okhttp.OpenAIOkHttpClient$Builder.build(OpenAIOkHttpClient.kt:476)
        //	at com.pickagent.w1d3.SteamResponseDemo.main(SteamResponseDemo.java:20)
        Instant startedAt = Instant.now();
        long startedAtNanos = System.nanoTime();
        Long firstDeltaLatencyMillis = null;
        Long terminalLatencyMillis = null;
        String finalState = "stream_closed_without_terminal_event";
        int eventIndex = 0;

        System.out.println("started_at=" + startedAt);

        try (StreamResponse<ResponseStreamEvent> stream = client.responses().createStreaming(params)) {
            Iterator<ResponseStreamEvent> events = stream.stream().iterator();
            while (events.hasNext()) {
                ResponseStreamEvent event = events.next();
                long eventElapsedMillis = elapsedMillis(startedAtNanos);
                System.err.printf("event[%02d] +%dms %s%n",
                        ++eventIndex, eventElapsedMillis, eventType(event));

                var outputTextDelta = event.outputTextDelta();
                if (outputTextDelta.isPresent()) {
                    if (firstDeltaLatencyMillis == null) {
                        firstDeltaLatencyMillis = eventElapsedMillis;
                        System.out.println("first_text_delta_latency_ms=" + firstDeltaLatencyMillis);
                    }
                    System.out.print(outputTextDelta.get().delta());
                    continue;
                }

                var completed = event.completed();
                if (completed.isPresent()) {
                    finalState = "completed";
                    terminalLatencyMillis = eventElapsedMillis;
                    System.out.println("\nresponse.completed id=" + completed.get().response().id());
                    continue;
                }

                var failed = event.failed();
                if (failed.isPresent()) {
                    finalState = "failed";
                    terminalLatencyMillis = eventElapsedMillis;
                    String detail = failed.get().response().error()
                            .map(Object::toString)
                            .orElse("unknown response error");
                    System.err.println("\nresponse.failed detail=" + detail);
                    continue;
                }

                var incomplete = event.incomplete();
                if (incomplete.isPresent()) {
                    finalState = "incomplete";
                    terminalLatencyMillis = eventElapsedMillis;
                    String detail = incomplete.get().response().incompleteDetails()
                            .map(Object::toString)
                            .orElse("unknown incomplete reason");
                    System.err.println("\nresponse.incomplete detail=" + detail);
                    continue;
                }

                var error = event.error();
                if (error.isPresent()) {
                    finalState = "error";
                    terminalLatencyMillis = eventElapsedMillis;
                    String code = error.get().code().orElse("unknown");
                    System.err.println("\nerror code=" + code + ", message=" + error.get().message());
                }
            }
        } catch (RuntimeException exception) {
            finalState = "transport_error";
            System.err.println("stream_error=" + exception.getMessage());
        } finally {
            System.out.println("final_state=" + finalState);
            System.out.println("terminal_latency_ms="
                    + (terminalLatencyMillis == null ? "not_received" : terminalLatencyMillis));
            System.out.println("elapsed_ms=" + elapsedMillis(startedAtNanos));
            System.out.println("first_text_delta_latency_ms="
                    + (firstDeltaLatencyMillis == null ? "not_received" : firstDeltaLatencyMillis));
        }
    }

    private static long elapsedMillis(long startedAtNanos) {
        return Duration.ofNanos(System.nanoTime() - startedAtNanos).toMillis();
    }

    private static String eventType(ResponseStreamEvent event) {
        if (event.isCreated()) return "response.created";
        if (event.isInProgress()) return "response.in_progress";
        if (event.isOutputItemAdded()) return "response.output_item.added";
        if (event.isContentPartAdded()) return "response.content_part.added";
        if (event.isReasoningSummaryPartAdded()) return "response.reasoning_summary_part.added";
        if (event.isReasoningSummaryTextDelta()) return "response.reasoning_summary_text.delta";
        if (event.isReasoningSummaryTextDone()) return "response.reasoning_summary_text.done";
        if (event.isReasoningSummaryPartDone()) return "response.reasoning_summary_part.done";
        if (event.isReasoningTextDelta()) return "response.reasoning_text.delta";
        if (event.isReasoningTextDone()) return "response.reasoning_text.done";
        if (event.isOutputTextDelta()) return "response.output_text.delta";
        if (event.isOutputTextDone()) return "response.output_text.done";
        if (event.isRefusalDelta()) return "response.refusal.delta";
        if (event.isRefusalDone()) return "response.refusal.done";
        if (event.isContentPartDone()) return "response.content_part.done";
        if (event.isOutputItemDone()) return "response.output_item.done";
        if (event.isCompleted()) return "response.completed";
        if (event.isFailed()) return "response.failed";
        if (event.isIncomplete()) return "response.incomplete";
        if (event.isError()) return "error";
        return "unhandled_event";
    }
}
