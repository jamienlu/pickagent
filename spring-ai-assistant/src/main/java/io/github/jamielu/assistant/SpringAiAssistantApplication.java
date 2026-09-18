package io.github.jamielu.assistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Spring AI 助手 HTTP 服务的应用入口。 */
@SpringBootApplication
public class SpringAiAssistantApplication {
    /** 创建 Spring Boot 应用配置实例。 */
    public SpringAiAssistantApplication() {
    }

    /**
     * 启动应用。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(SpringAiAssistantApplication.class, args);
    }
}
