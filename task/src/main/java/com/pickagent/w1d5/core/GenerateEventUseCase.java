package com.pickagent.w1d5.core;

import java.util.Objects;

/**
 * 协调模型生成与事件解析的应用用例。
 *
 * <p>该类只依赖核心端口，不依赖任何模型供应商 SDK 或基础设施协议类型。</p>
 *
 * @author jamieLu
 * @since 2026-08-28
 */
public final class GenerateEventUseCase {
    /** 将自然语言描述转换为事件提取任务的提示前缀。 */
    private static final String PROMPT_PREFIX =
            "Extract one event and return its name, date, and participants: ";

    /** 模型调用出站端口。 */
    private final ModelGateway modelGateway;
    /** 结构化事件解析出站端口。 */
    private final EventParser eventParser;

    /**
     * 创建事件生成用例。
     *
     * @param modelGateway 模型调用端口
     * @param eventParser 事件文本解析端口
     * @throws NullPointerException 任一依赖为 {@code null} 时抛出
     */
    public GenerateEventUseCase(ModelGateway modelGateway, EventParser eventParser) {
        this.modelGateway = Objects.requireNonNull(modelGateway, "modelGateway");
        this.eventParser = Objects.requireNonNull(eventParser, "eventParser");
    }

    /**
     * 根据自然语言描述生成一个结构化事件。
     *
     * @param eventDescription 事件自然语言描述
     * @return 成功事件、拒绝、未完成或明确失败结果
     * @throws IllegalArgumentException eventDescription 为空时抛出
     * @throws NullPointerException adapter 违反端口契约并返回 {@code null} 时抛出
     */
    public GenerateEventResult generate(String eventDescription) {
        ModelCommand command = new ModelCommand(PROMPT_PREFIX + requireDescription(eventDescription));

        ModelResult modelResult = Objects.requireNonNull(
                modelGateway.generate(command),
                "modelGateway returned null"
        );

        if (modelResult instanceof ModelResult.Refused refused) {
            return new GenerateEventResult.Refused(refused.reason());
        }
        if (modelResult instanceof ModelResult.Incomplete incomplete) {
            return new GenerateEventResult.Incomplete(incomplete.reason());
        }
        if (modelResult instanceof ModelResult.Failed failed) {
            return new GenerateEventResult.Failed(mapFailureKind(failed.kind()), failed.reason());
        }

        ModelResult.Completed completed = (ModelResult.Completed) modelResult;
        EventParser.ParseResult parseResult = Objects.requireNonNull(
                eventParser.parse(completed.content()),
                "eventParser returned null"
        );
        if (parseResult instanceof EventParser.ParseResult.Parsed parsed) {
            return new GenerateEventResult.Generated(parsed.event());
        }

        EventParser.ParseResult.Invalid invalid = (EventParser.ParseResult.Invalid) parseResult;
        return new GenerateEventResult.Failed(
                GenerateEventResult.FailureKind.INVALID_MODEL_OUTPUT,
                invalid.reason()
        );
    }

    /**
     * 校验事件描述必填约束。
     *
     * @param eventDescription 事件描述
     * @return 原始事件描述
     * @throws IllegalArgumentException 描述为空时抛出
     */
    private static String requireDescription(String eventDescription) {
        if (eventDescription == null || eventDescription.isBlank()) {
            throw new IllegalArgumentException("eventDescription cannot be null or blank");
        }
        return eventDescription;
    }

    /**
     * 将网关失败分类映射为应用用例失败分类。
     *
     * @param kind 网关失败分类
     * @return 对应的应用失败分类
     */
    private static GenerateEventResult.FailureKind mapFailureKind(ModelResult.FailureKind kind) {
        return switch (kind) {
            case TRANSPORT -> GenerateEventResult.FailureKind.GATEWAY_TRANSPORT;
            case PROVIDER -> GenerateEventResult.FailureKind.GATEWAY_PROVIDER;
            case PROTOCOL -> GenerateEventResult.FailureKind.GATEWAY_PROTOCOL;
        };
    }
}
