package com.pickagent.w2.core;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 供应商中立的最小工具定义。
 *
 * <p>当前版本只支持必填字符串参数；参数顺序同时决定生成 Schema 的稳定顺序。</p>
 *
 * @param name 符合核心命名规则的工具名称
 * @param description 提供给模型的工具用途说明
 * @param parameters 按声明顺序保存的必填字符串参数
 * @author jamieLu
 * @since 2026-08-31
 */
public record ToolDefinition(String name, String description, List<RequiredStringParameter> parameters) {
    /**
     * 创建工具定义并校验名称、说明、参数非空和参数名唯一性。
     *
     * @throws IllegalArgumentException 名称格式非法、必填文本为空或参数名重复时抛出
     * @throws NullPointerException parameters 或其中元素为 {@code null} 时抛出
     */
    public ToolDefinition {
        Checks.nonBlank(name, "tool name");
        Checks.nonBlank(description, "tool description");
        if (!name.matches("[a-z][a-z0-9_]*")) {
            throw new IllegalArgumentException("tool name must match [a-z][a-z0-9_]*");
        }
        parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters"));
        Set<String> names = new LinkedHashSet<>();
        for (RequiredStringParameter parameter : parameters) {
            Objects.requireNonNull(parameter, "parameter");
            if (!names.add(parameter.name())) {
                throw new IllegalArgumentException("duplicate parameter: " + parameter.name());
            }
        }
    }

    /**
     * 使用原始名称集合创建兼容工具定义。
     *
     * <p>输入集合会按名称排序，以保证生成 Schema 的顺序稳定。</p>
     *
     * @param name 工具名称
     * @param description 工具用途说明
     * @param requiredArguments 必填字符串参数名集合
     * @throws IllegalArgumentException 工具或参数名称非法时抛出
     * @throws NullPointerException requiredArguments 为 {@code null} 时抛出
     */
    public ToolDefinition(String name, String description, Set<String> requiredArguments) {
        this(name, description, toParameters(requiredArguments));
    }

    /**
     * 返回供 Runtime 校验使用的必填参数名集合。
     *
     * @return 保持参数声明顺序的不可修改集合
     */
    public Set<String> requiredArguments() {
        Set<String> names = new LinkedHashSet<>();
        parameters.forEach(parameter -> names.add(parameter.name()));
        return Collections.unmodifiableSet(names);
    }

    /**
     * 将名称集合转换为顺序稳定的参数描述列表。
     *
     * @param requiredArguments 必填参数名称集合
     * @return 按名称排序的不可变参数列表
     * @throws NullPointerException requiredArguments 为 {@code null} 时抛出
     */
    private static List<RequiredStringParameter> toParameters(Set<String> requiredArguments) {
        Objects.requireNonNull(requiredArguments, "requiredArguments");
        return requiredArguments.stream()
                .sorted()
                .map(RequiredStringParameter::new)
                .toList();
    }

    /**
     * 一个必填字符串参数的供应商中立描述。
     *
     * <p>当前版本不允许可选、数组、对象或非字符串类型。</p>
     *
     * @param name 参数名称
     * @param description 参数用途说明，可以为空字符串
     */
    public record RequiredStringParameter(String name, String description) {
        /**
         * 创建必填字符串参数。
         *
         * @throws IllegalArgumentException name 为空时抛出
         * @throws NullPointerException description 为 {@code null} 时抛出
         */
        public RequiredStringParameter {
            Checks.nonBlank(name, "argument name");
            description = Objects.requireNonNull(description, "parameter description");
        }

        /**
         * 创建没有用途说明的必填字符串参数。
         *
         * @param name 参数名称
         */
        public RequiredStringParameter(String name) {
            this(name, "");
        }
    }
}
