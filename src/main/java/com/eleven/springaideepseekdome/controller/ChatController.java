package com.eleven.springaideepseekdome.controller;

import com.eleven.springaideepseekdome.domain.dto.ChatReply;
import com.eleven.springaideepseekdome.domain.dto.ChatRequest;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekAssistantMessage;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
public class ChatController {
    @Autowired
    private DeepSeekChatModel chatModel;
    @Autowired
    private ChatClient chatClient;
    @Autowired
    private ChatMemory chatMemory;


    /**
     * 处理AI生成的POST请求（支持JSON请求体）
     * @param request 包含message字段的请求对象（默认消息："Tell me a joke"）
     * @return AI生成的文本响应内容
     * @apiNote 请求示例：
     * <pre>{@code
     * {
     *   "message": "Explain quantum computing in simple terms"
     * }
     * }</pre>
     */
    @PostMapping("/ai/generate")
    public ChatReply generatePost(@RequestBody @Valid ChatRequest request) {
        // 判断sessionID是否为空，为空则生成一个sessionID
        if (request.getSession() == null) {
            request.setSession( "session_" + System.currentTimeMillis());
        }
        String content = ChatClient.create(chatModel).prompt()
                .advisors(
                        new SimpleLoggerAdvisor(), // 日志
                        MessageChatMemoryAdvisor.builder(chatMemory).
                                conversationId(request.getSession()).build() // 记录历史
                )
                .user(request.getMessage())
                .call()
                .content();
        return new ChatReply(content, request.getSession());
    }

    @GetMapping("/ai/generate")
    public Map generate(@RequestParam(value = "message", defaultValue = "Tell me a joke") String message) {
        return Map.of("generation", chatModel.call(message));
    }

    @GetMapping("/ai/generateStream")
    public Flux<ChatResponse> generateStream(@RequestParam(value = "message", defaultValue = "Tell me a joke") String message) {
        var prompt = new Prompt(new UserMessage(message));
        return chatModel.stream(prompt);
    }

    @GetMapping("/ai/generatePythonCode")
    public String generatePythonCode(@RequestParam(value = "message", defaultValue = "Please write quick sort code") String message) {
        UserMessage userMessage = new UserMessage(message);
        Message assistantMessage = DeepSeekAssistantMessage.prefixAssistantMessage("```python\\n");
        Prompt prompt = new Prompt(List.of(userMessage, assistantMessage), ChatOptions.builder().stopSequences(List.of("```")).build());
        ChatResponse response = chatModel.call(prompt);
        return response.getResult().getOutput().getText();
    }
}