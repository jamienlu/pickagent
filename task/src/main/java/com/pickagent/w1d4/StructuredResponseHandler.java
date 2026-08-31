package com.pickagent.w1d4;

import java.util.Objects;
import java.util.function.Function;

/**
 * 在解码前区分结构化响应终态，并将结果映射为应用可处理的稳定类型。
 *
 * <p>未完成和拒绝响应不会进入 JSON 解码器。</p>
 *
 * @author jamieLu
 * @since 2026-08-27
 */
public final class StructuredResponseHandler {
    /** 模型文本到结构化解码结果的函数。 */
    private final Function<String, DecodeResult> decoder;

    /** 使用默认防御式解码器创建处理器。 */
    public StructuredResponseHandler() {
        this(new StructuredOutputDecoder()::decode);
    }

    /**
     * 使用指定解码函数创建处理器，便于离线测试失败路径。
     *
     * @param decoder 文本解码函数
     * @throws NullPointerException decoder 为 {@code null} 时抛出
     */
    public StructuredResponseHandler(Function<String, DecodeResult> decoder) {
        this.decoder = Objects.requireNonNull(decoder, "decoder");
    }

    /**
     * 处理一个响应快照并保留拒绝、未完成和解码失败语义。
     *
     * @param response 供应商响应的稳定快照
     * @return 应用层处理结果
     * @throws NullPointerException response 为 {@code null} 时抛出
     */
    public HandlingResult handle(ResponseSnapshot response) {
        Objects.requireNonNull(response, "response");

        if (response.status() == ResponseStatus.INCOMPLETE) {
            return new HandlingResult.Incomplete(response.incompleteReason());
        }

        if (response.content() instanceof ResponseContent.Refusal refusal) {
            return new HandlingResult.Refused(refusal.reason());
        }

        ResponseContent.OutputText outputText = (ResponseContent.OutputText) response.content();
        DecodeResult decodeResult = decoder.apply(outputText.text());
        if (decodeResult instanceof DecodeResult.Success success) {
            return new HandlingResult.EventAccepted(success.event());
        }
        return new HandlingResult.InvalidOutput((DecodeResult.Failure) decodeResult);
    }

    /** 响应生成终态。 */
    public enum ResponseStatus {
        /** 响应已经完成生成。 */
        COMPLETED,
        /** 响应未完整生成。 */
        INCOMPLETE
    }

    /** 供应商响应中的内容变体。 */
    public sealed interface ResponseContent permits ResponseContent.OutputText, ResponseContent.Refusal {
        /**
         * 正常输出文本。
         *
         * @param text 模型输出文本
         */
        record OutputText(String text) implements ResponseContent {
            /** 校验输出文本不能为空引用。 */
            public OutputText {
                Objects.requireNonNull(text, "text");
            }
        }

        /**
         * 模型明确拒绝请求的语义结果。
         *
         * @param reason 拒绝原因
         */
        record Refusal(String reason) implements ResponseContent {
            /** 校验拒绝原因不能为空引用。 */
            public Refusal {
                Objects.requireNonNull(reason, "reason");
            }
        }
    }

    /**
     * 供离线处理器使用的供应商响应快照。
     *
     * @param status 响应终态
     * @param content 输出文本或拒绝内容
     * @param incompleteReason 未完成原因，仅 INCOMPLETE 时必填
     */
    public record ResponseSnapshot(
            ResponseStatus status,
            ResponseContent content,
            String incompleteReason
    ) {
        /** 校验响应快照的终态、内容及未完成原因约束。 */
        public ResponseSnapshot {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(content, "content");
            if (status == ResponseStatus.INCOMPLETE) {
                Objects.requireNonNull(incompleteReason, "incompleteReason");
            }
        }

        /**
         * 创建已完成的文本响应快照。
         *
         * @param outputText 完整输出文本
         * @return 已完成快照
         */
        public static ResponseSnapshot completed(String outputText) {
            return new ResponseSnapshot(
                    ResponseStatus.COMPLETED,
                    new ResponseContent.OutputText(outputText),
                    null
            );
        }

        /**
         * 创建明确拒绝的响应快照。
         *
         * @param reason 拒绝原因
         * @return 拒绝快照
         */
        public static ResponseSnapshot refusal(String reason) {
            return new ResponseSnapshot(
                    ResponseStatus.COMPLETED,
                    new ResponseContent.Refusal(reason),
                    null
            );
        }

        /**
         * 创建未完整生成的响应快照。
         *
         * @param reason 未完成原因
         * @param partialOutputText 已生成的部分文本，可为 {@code null}
         * @return 未完成快照
         */
        public static ResponseSnapshot incomplete(String reason, String partialOutputText) {
            return new ResponseSnapshot(
                    ResponseStatus.INCOMPLETE,
                    new ResponseContent.OutputText(partialOutputText == null ? "" : partialOutputText),
                    reason
            );
        }
    }

    /** 应用层能够消费的结构化响应处理结果。 */
    public sealed interface HandlingResult permits HandlingResult.EventAccepted,
            HandlingResult.Refused, HandlingResult.Incomplete, HandlingResult.InvalidOutput {

        /**
         * 表示输出已经被接受为有效事件。
         *
         * @param event 已验证事件
         */
        record EventAccepted(Event event) implements HandlingResult {
            /** 校验成功结果必须包含事件。 */
            public EventAccepted {
                Objects.requireNonNull(event, "event");
            }
        }

        /**
         * 表示模型明确拒绝请求。
         *
         * @param reason 拒绝原因
         */
        record Refused(String reason) implements HandlingResult {
            /** 校验拒绝结果必须包含原因。 */
            public Refused {
                Objects.requireNonNull(reason, "reason");
            }
        }

        /**
         * 表示模型响应未完整生成。
         *
         * @param reason 未完成原因
         */
        record Incomplete(String reason) implements HandlingResult {
            /** 校验未完成结果必须包含原因。 */
            public Incomplete {
                Objects.requireNonNull(reason, "reason");
            }
        }

        /**
         * 表示已完成文本未通过 Event 契约校验。
         *
         * @param failure 解码失败详情
         */
        record InvalidOutput(DecodeResult.Failure failure) implements HandlingResult {
            /** 校验无效输出结果必须包含解码失败详情。 */
            public InvalidOutput {
                Objects.requireNonNull(failure, "failure");
            }
        }
    }
}
