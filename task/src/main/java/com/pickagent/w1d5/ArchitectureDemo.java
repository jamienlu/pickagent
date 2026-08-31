package com.pickagent.w1d5;

import com.pickagent.w1d5.core.GenerateEventResult;
import com.pickagent.w1d5.core.GenerateEventUseCase;
import com.pickagent.w1d5.infrastructure.ReplayModelGateway;
import com.pickagent.w1d5.infrastructure.StructuredEventParser;

/**
 * 展示六边形架构纵向切片的离线组合根。
 *
 * <p>Demo 将应用用例、供应商中立端口、Replay adapter 和结构化解析 adapter 组合起来，且不访问网络。</p>
 *
 * @author jamieLu
 * @since 2026-08-28
 */
public final class ArchitectureDemo {
    private ArchitectureDemo() {
    }

    /**
     * 依次演示成功、拒绝和未完成三种模型终态。
     *
     * @param args 命令行参数，本示例不使用
     */
    public static void main(String[] args) {
        demonstrate(ReplayModelGateway.Scenario.COMPLETED);
        demonstrate(ReplayModelGateway.Scenario.REFUSED);
        demonstrate(ReplayModelGateway.Scenario.INCOMPLETE);
    }

    /**
     * 组合并执行指定 Replay 场景。
     *
     * @param scenario 离线模型场景
     */
    private static void demonstrate(ReplayModelGateway.Scenario scenario) {
        var gateway = new ReplayModelGateway(scenario);
        var useCase = new GenerateEventUseCase(gateway, new StructuredEventParser());
        GenerateEventResult result = useCase.generate("Prepare the replay architecture review event.");

        System.out.println(scenario.name().toLowerCase() + " -> " + describe(result));
    }

    /**
     * 将应用结果转换为命令行可读文本。
     *
     * @param result 事件生成用例结果
     * @return 结果摘要
     */
    private static String describe(GenerateEventResult result) {
        if (result instanceof GenerateEventResult.Generated generated) {
            return "generated: " + generated.event();
        }
        if (result instanceof GenerateEventResult.Refused refused) {
            return "refused: " + refused.reason();
        }
        if (result instanceof GenerateEventResult.Incomplete incomplete) {
            return "incomplete: " + incomplete.reason();
        }

        GenerateEventResult.Failed failed = (GenerateEventResult.Failed) result;
        return "failed[" + failed.kind() + "]: " + failed.reason();
    }
}
