package com.pickagent.w1d5.infrastructure;

import com.pickagent.w1d5.core.ModelCommand;
import com.pickagent.w1d5.core.ModelGateway;
import com.pickagent.w1d5.core.ModelResult;

import java.util.Map;
import java.util.Objects;

/**
 * 面向 Demo、契约测试和本地开发的确定性离线模型 adapter。
 *
 * @author jamieLu
 * @since 2026-08-28
 */
public final class ReplayModelGateway implements ModelGateway {
    /** 每种离线场景对应的稳定模型结果。 */
    private static final Map<Scenario, ModelResult> REPLAY_DATA = Map.of(
            Scenario.COMPLETED, new ModelResult.Completed(
                    "{\"name\":\"Architecture Review\",\"date\":\"2026-08-28\","
                            + "\"participants\":[\"Jamie\",\"Ada\"]}"
            ),
            Scenario.REFUSED, new ModelResult.Refused("replay safety refusal"),
            Scenario.INCOMPLETE, new ModelResult.Incomplete("max_output_tokens"),
            Scenario.TRANSPORT_FAILURE, new ModelResult.Failed(
                    ModelResult.FailureKind.TRANSPORT, "replay connection timeout"),
            Scenario.PROVIDER_FAILURE, new ModelResult.Failed(
                    ModelResult.FailureKind.PROVIDER, "replay provider rejected request"),
            Scenario.PROTOCOL_FAILURE, new ModelResult.Failed(
                    ModelResult.FailureKind.PROTOCOL, "replay response protocol mismatch")
    );

    /** 当前 adapter 要重放的场景。 */
    private final Scenario scenario;

    /**
     * 创建固定场景的 Replay adapter。
     *
     * @param scenario 要重放的模型场景
     * @throws NullPointerException scenario 为 {@code null} 时抛出
     */
    public ReplayModelGateway(Scenario scenario) {
        this.scenario = Objects.requireNonNull(scenario, "scenario");
    }

    /**
     * 返回场景对应的固定模型结果。
     *
     * @param command 供应商中立模型命令，仅校验非空
     * @return 当前场景的固定结果
     * @throws NullPointerException command 为 {@code null} 时抛出
     */
    @Override
    public ModelResult generate(ModelCommand command) {
        Objects.requireNonNull(command, "command");
        return REPLAY_DATA.get(scenario);
    }

    /** Replay adapter 支持的离线模型场景。 */
    public enum Scenario {
        /** 正常完成并返回 Event JSON。 */
        COMPLETED,
        /** 模型明确拒绝请求。 */
        REFUSED,
        /** 模型响应未完整生成。 */
        INCOMPLETE,
        /** 模拟传输失败。 */
        TRANSPORT_FAILURE,
        /** 模拟供应商失败。 */
        PROVIDER_FAILURE,
        /** 模拟协议映射失败。 */
        PROTOCOL_FAILURE
    }
}
