package io.github.jamielu.agent.online;

import com.openai.client.OpenAIClient;
import io.github.jamielu.agent.openai.OpenAiResponsesModel;
import io.github.jamielu.agent.runtime.AgentModelPort;
import io.github.jamielu.agent.runtime.AgentRuntime;
import io.github.jamielu.agent.tool.ToolRegistry;

import java.util.Objects;

/** 在线组合服务：管理 SDK 客户端生命周期并执行一次隔离的 Agent 会话。 */
public final class OpenAiOnlineRunner {
    private final SessionFactory sessionFactory;

    /**
     * 创建可测试的在线组合服务。
     *
     * @param sessionFactory 模型会话资源工厂
     */
    public OpenAiOnlineRunner(SessionFactory sessionFactory) {
        this.sessionFactory = Objects.requireNonNull(sessionFactory, "sessionFactory");
    }

    /**
     * 创建使用生产 SDK 客户端工厂的组合服务。
     *
     * @return 在线组合服务
     */
    public static OpenAiOnlineRunner production() {
        return new OpenAiOnlineRunner(OpenAiOnlineRunner::productionSession);
    }

    static ModelSession productionSession(OpenAiOnlineConfig config) {
        OpenAIClient client = new OpenAiClientFactory().create(config);
        return new ModelSession(
                new OpenAiResponsesModel(
                        client,
                        config.model(),
                        config.instructions(),
                        config.responseOptions()),
                client::close);
    }

    /**
     * 执行一次在线会话并保证关闭 SDK 客户端。
     *
     * @param config 在线配置
     * @param input 用户输入
     * @param tools 工具注册表
     * @return 供应商中立运行终态
     */
    public AgentRuntime.Result run(
            OpenAiOnlineConfig config, String input, ToolRegistry tools) {
        OpenAiOnlineConfig checkedConfig = Objects.requireNonNull(config, "config");
        ToolRegistry checkedTools = Objects.requireNonNull(tools, "tools");
        try (ModelSession session = Objects.requireNonNull(
                sessionFactory.open(checkedConfig), "sessionFactory returned null")) {
            AgentRuntime runtime = new AgentRuntime(
                    session.model(), checkedTools, checkedConfig.runBudget());
            return runtime.run(input);
        }
    }

    /** 创建一个需要在运行后关闭的模型会话。 */
    @FunctionalInterface
    public interface SessionFactory {
        /**
         * 创建新的独立模型会话资源。
         *
         * @param config 已校验在线配置
         * @return 新的独立模型会话资源
         */
        ModelSession open(OpenAiOnlineConfig config);
    }

    /**
     * 模型端口及其底层可关闭资源。
     *
     * @param model 独立会话模型端口
     * @param resource 与模型绑定的底层资源
     */
    public record ModelSession(AgentModelPort model, AutoCloseable resource)
            implements AutoCloseable {
        /** 创建并校验会话资源。 */
        public ModelSession {
            Objects.requireNonNull(model, "model");
            Objects.requireNonNull(resource, "resource");
        }

        /** 关闭底层资源，并把受检异常包装为明确的资源关闭失败。 */
        @Override
        public void close() {
            try {
                resource.close();
            } catch (Exception failure) {
                throw new IllegalStateException("failed to close online model session", failure);
            }
        }
    }
}
