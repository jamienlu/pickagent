package io.github.jamielu.agent.online;

import io.github.jamielu.agent.runtime.AgentRuntime;
import io.github.jamielu.agent.tool.ToolRegistry;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;

/** OpenAI Responses 在线命令行入口；只有操作者显式运行时才会发起请求。 */
public final class OpenAiAgentCli {
    private OpenAiAgentCli() {
    }

    /**
     * 运行一次在线请求；非成功终态通过异常使进程返回非零状态。
     *
     * @param args 唯一参数为用户输入
     */
    public static void main(String[] args) { failOnNonZero(execute(
            args, System.getenv(), System.out, System.err,
            OpenAiOnlineRunner.production()::run)); }

    /**
     * 将非零退出码转换为不包含凭据的命令行异常。
     *
     * @param exitCode 命令执行退出码
     */
    static void failOnNonZero(int exitCode) {
        if (exitCode != 0) {
            throw new CliExitException(exitCode);
        }
    }

    /**
     * 校验命令参数、构建外置配置并映射供应商中立终态。
     *
     * @param args 命令行参数
     * @param environment 环境变量快照
     * @param out 标准输出目标
     * @param err 标准错误目标
     * @param command 在线运行用例
     * @return 稳定进程退出码
     */
    static int execute(String[] args, Map<String, String> environment,
                       PrintStream out, PrintStream err, OnlineCommand command) {
        if (args.length != 1 || args[0].isBlank()) {
            err.println("用法：OpenAiAgentCli \"<用户输入>\"");
            return 2;
        }
        try {
            AgentRuntime.Result result = command.run(
                    OpenAiOnlineConfig.fromEnvironment(environment),
                    args[0],
                    new ToolRegistry(List.of()));
            if (result instanceof AgentRuntime.Completed completed) {
                out.println(completed.answer().text());
                return 0;
            }
            if (result instanceof AgentRuntime.ModelFailed failed) {
                err.println("模型请求失败：" + failed.failure().kind());
                return 4;
            }
            if (result instanceof AgentRuntime.Stopped stopped) {
                err.println("运行停止：" + stopped.reason() + " - " + stopped.detail());
                return 3;
            }
            err.println("工具执行失败");
            return 5;
        } catch (IllegalArgumentException failure) {
            err.println("配置错误：" + failure.getMessage());
            return 2;
        }
    }

    /** 可替换的在线运行用例边界，使命令行终态映射可离线验证。 */
    @FunctionalInterface
    interface OnlineCommand {
        /**
         * 执行一次在线组合。
         *
         * @param config 已校验在线配置
         * @param input 用户输入
         * @param tools 工具注册表
         * @return 供应商中立运行终态
         */
        AgentRuntime.Result run(OpenAiOnlineConfig config, String input, ToolRegistry tools);
    }

    /** 仅携带稳定退出码、且不包含凭据的命令行失败。 */
    static final class CliExitException extends RuntimeException {
        private CliExitException(int exitCode) {
            super("online CLI failed with exit code " + exitCode);
        }
    }
}
