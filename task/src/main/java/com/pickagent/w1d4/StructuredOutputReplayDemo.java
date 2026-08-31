package com.pickagent.w1d4;

import java.util.List;

/**
 * 离线重放结构化输出的成功、拒绝、未完成和契约失败路径。
 *
 * @author jamieLu
 * @since 2026-08-27
 */
public final class StructuredOutputReplayDemo {
    private StructuredOutputReplayDemo() {
    }

    /**
     * 构造固定响应快照并输出每种终态的处理结果。
     *
     * @param args 命令行参数，本示例不使用
     */
    public static void main(String[] args) {
        StructuredResponseHandler handler = new StructuredResponseHandler();

        replay(handler, "completed + valid Event", StructuredResponseHandler.ResponseSnapshot.completed("""
                {"name":"AI Meetup","date":"2026-08-27","participants":["Alice","Bob"]}
                """));

        replay(handler, "refusal", StructuredResponseHandler.ResponseSnapshot.refusal(
                "The request cannot be fulfilled"));

        replay(handler, "incomplete / max_output_tokens", StructuredResponseHandler.ResponseSnapshot.incomplete(
                "max_output_tokens",
                "{\"name\":\"Partial Event\",\"date\":"));

        replay(handler, "completed + nonconforming JSON", StructuredResponseHandler.ResponseSnapshot.completed("""
                {"name":"AI Meetup","date":"2026-08-27","participants":[],"unexpected":true}
                """));
    }

    /**
     * 执行一个离线响应场景并打印结果。
     *
     * @param handler 结构化响应处理器
     * @param scenario 场景名称
     * @param response 固定响应快照
     */
    private static void replay(
            StructuredResponseHandler handler,
            String scenario,
            StructuredResponseHandler.ResponseSnapshot response
    ) {
        StructuredResponseHandler.HandlingResult result = handler.handle(response);
        System.out.println(scenario + " -> " + describe(result));
    }

    /**
     * 将处理结果转换为便于命令行验收的文本。
     *
     * @param result 结构化响应处理结果
     * @return 可读的结果摘要
     */
    private static String describe(StructuredResponseHandler.HandlingResult result) {
        if (result instanceof StructuredResponseHandler.HandlingResult.EventAccepted accepted) {
            Event event = accepted.event();
            return "SUCCESS name=" + event.name()
                    + ", date=" + event.date()
                    + ", participants=" + List.copyOf(event.participants());
        }
        if (result instanceof StructuredResponseHandler.HandlingResult.Refused refused) {
            return "REFUSAL reason=" + refused.reason();
        }
        if (result instanceof StructuredResponseHandler.HandlingResult.Incomplete incomplete) {
            return "INCOMPLETE reason=" + incomplete.reason();
        }
        StructuredResponseHandler.HandlingResult.InvalidOutput invalid =
                (StructuredResponseHandler.HandlingResult.InvalidOutput) result;
        return "INVALID_OUTPUT code=" + invalid.failure().code()
                + ", reason=" + invalid.failure().reason();
    }
}
