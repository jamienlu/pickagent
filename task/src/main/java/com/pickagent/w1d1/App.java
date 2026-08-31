package com.pickagent.w1d1;

/**
 * 验证 W1D1 Java 21 学习环境可正常编译和运行的最小入口。
 *
 * @author jamieLu
 * @since 2026-08-24
 */
public class App {
    /** 禁止实例化环境验证入口。 */
    private App() {
    }

    /**
     * 输出环境就绪标记，供命令行验收使用。
     *
     * @param args 命令行参数，本示例不使用
     */
    public static void main(String[] args) {
        System.out.println("W1D1 environment ready");
    }
}
