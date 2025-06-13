package com.eleven.springaideepseekdome;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SpringAiDeepseekDomeApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringAiDeepseekDomeApplication.class, args);
    }

    @Value("${ai.user.input}")
    private String userInput;
    @Value("${ai.model}")
    private String model;

    // 直接注入 ChatClient 而不是 Builder
    @Autowired
    private ChatClient chatClient;

    @Bean
    public CommandLineRunner predefinedQuestions(ToolCallbackProvider tools) {
        return args -> {

            try {
                System.out.println("\n>>> MODEL: " + model);
                System.out.println("\n>>> QUESTION: " + userInput);
                // 使用注入的 chatClient
                System.out.println("\n>>> ASSISTANT: " + chatClient.prompt(userInput).toolCallbacks(tools).call().content());
            } catch (Exception e) {
                System.err.println("[警告] 预设问题执行失败，但不影响主服务");
                System.err.println("原因: " + e.getMessage());
                // 实际项目中应使用日志框架
                // log.error("预设问题执行异常", e);
            }
        };
    }
}
