package com.pickagent.w1d5.core;

/**
 * 应用核心定义的模型调用出站端口。
 *
 * <p>基础设施 adapter 负责把供应商请求、响应和已识别故障转换为该端口的稳定类型。</p>
 *
 * @author jamieLu
 * @since 2026-08-28
 */
@FunctionalInterface
public interface ModelGateway {
    /**
     * 执行一次模型生成请求。
     *
     * @param command 供应商中立模型命令
     * @return 完成、拒绝、未完成或已分类失败结果，不允许返回 {@code null}
     */
    ModelResult generate(ModelCommand command);
}
