package com.eleven.springaideepseekdome.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class ChatClientConfig {

    /**
     * 创建 DeepSeek 模型
     */
    @Bean
    public ChatClient deepseekChatClient(DeepSeekChatModel chatModel) {
        return ChatClient.create(chatModel);
    }

    /**
     * 创建 OpenAI 模型
     */
    @Bean
    public ChatClient openAiChatClient(OpenAiChatModel chatModel) {
        return ChatClient.create(chatModel);
    }

    // 新增配置方法
    @Bean
    @Primary
    public ChatClient defaultChatClient(
            @Value("${ai.model:deepseek}") String defaultModel,
            @Qualifier("deepseekChatClient") ChatClient deepseekClient,
            @Qualifier("openAiChatClient") ChatClient openaiClient) {

        if ("deepseek".equalsIgnoreCase(defaultModel)) {
            return deepseekClient;
        } else if ("openai".equalsIgnoreCase(defaultModel)) {
            return openaiClient;
        } else {
            throw new IllegalArgumentException("不支持的模型: " + defaultModel);
        }
    }
}