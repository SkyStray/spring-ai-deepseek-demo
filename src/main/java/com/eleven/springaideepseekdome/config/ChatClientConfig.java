package com.eleven.springaideepseekdome.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient deepseekChatClient(DeepSeekChatModel chatModel) {
        return ChatClient.create(chatModel);
    }
}