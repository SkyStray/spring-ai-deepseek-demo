package com.eleven.springaideepseekdome.controller;

import com.eleven.springaideepseekdome.console.PromptConsole;
import com.eleven.springaideepseekdome.domain.dto.ChatReply;
import com.eleven.springaideepseekdome.domain.dto.ChatRequest;
import com.eleven.springaideepseekdome.tools.DateTimeTools;
import com.eleven.springaideepseekdome.tools.MysqlTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import javax.validation.Valid;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
public class ChatController {
    @Autowired
    private DeepSeekChatModel deepSeekChatModel;
    @Autowired
    private ChatClient chatClient;
    @Autowired
    private ChatMemory chatMemory;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SyncMcpToolCallbackProvider toolCallbackProvider;


    /**
     * 处理AI生成的聊天会话POST请求（MCP同步响应）
     *
     * @param request
     * @return
     */
    @PostMapping("/ai/chatmemory/mcp/sync")
    public ChatReply chatmemoryMcpSync(@RequestBody @Valid ChatRequest request) {
        // 判断sessionID是否为空，为空则生成一个sessionID
        if (request.getSession() == null) {
            request.setSession("session_" + System.currentTimeMillis());
        }
        ToolCallback[] toolCallbacks = toolCallbackProvider.getToolCallbacks();
        // 日志打印toolCallbacks
        log.info("toolCallbacks: {}", toolCallbacks);

        ChatResponse chatResponse = chatClient
                .prompt(PromptConsole.MYSQL_STUDYDB_PROMPT)
                .toolCallbacks(toolCallbacks)
                .advisors(
                        new SimpleLoggerAdvisor(), // 日志
                        MessageChatMemoryAdvisor.builder(chatMemory).
                                conversationId(request.getSession()).build() // 聊天记忆功能
                )
                .user(request.getMessage())
                .call().chatResponse();

        return new ChatReply(chatResponse.getResult().getOutput().getText(), request.getSession());
    }

    /**
     * 处理AI生成的聊天会话POST请求（MCP流式响应）
     *
     * @param request
     * @return
     */
    @PostMapping(value = "/ai/chatmemory/mcp/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Map<String, Object>> chatmemoryPromptMcpStream(@RequestBody @Valid ChatRequest request) {
        // 判断sessionID是否为空，为空则生成一个sessionID
        if (request.getSession() == null) {
            request.setSession("session_" + System.currentTimeMillis());
        }
        ToolCallback[] toolCallbacks = toolCallbackProvider.getToolCallbacks();
        // 日志打印toolCallbacks
        log.info("toolCallbacks: {}", toolCallbacks);

        return chatClient
                .prompt(PromptConsole.MYSQL_STUDYDB_PROMPT)
                .toolCallbacks(toolCallbacks)
                .advisors(
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory)
                                .conversationId(request.getSession()).build()
                )
                .user(request.getMessage())
                .stream()
                .chatResponse()
                .map(chatResponse -> {
                    // 构建响应Map
                    Map<String, Object> responseMap = Map.of(
                            "session", request.getSession(),
                            "round", chatMemory.get(request.getSession()) != null ?
                                    chatMemory.get(request.getSession()).size() : 0,
                            "content", chatResponse.getResult().getOutput().getText(),
                            "tools", chatResponse.getResult().getOutput().getToolCalls()
                                    .stream().map(tc -> tc.toString()).toList()
                    );

                    // 打印日志
                    log.info("流式响应数据: {}",
                            responseMap.entrySet().stream()
                                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                                    .collect(Collectors.joining(", ", "{", "}"))
                    );

                    return responseMap; // 直接返回Map
                });
    }



    /**
     * 处理AI生成的聊天会话POST请求（Function Calling同步响应）
     *
     * @param request
     * @return
     */
    @PostMapping("/ai/chatmemory/prompt/tool/sync")
    public ChatReply chatmemoryPromptToolSync(@RequestBody @Valid ChatRequest request) {
        // 判断sessionID是否为空，为空则生成一个sessionID
        if (request.getSession() == null) {
            request.setSession("session_" + System.currentTimeMillis());
        }
        ChatResponse chatResponse = chatClient
                .prompt(PromptConsole.MYSQL_STUDYDB_PROMPT)
                .tools(new DateTimeTools(), new MysqlTools(jdbcTemplate))
                .advisors(
                        new SimpleLoggerAdvisor(), // 日志
                        MessageChatMemoryAdvisor.builder(chatMemory).
                                conversationId(request.getSession()).build() // 聊天记忆功能
                )
                .user(request.getMessage())
                .call().chatResponse();

        return new ChatReply(chatResponse.getResult().getOutput().getText(), request.getSession());
    }


    /**
     * 处理AI生成的聊天会话POST请求（流式响应）
     *
     * @param request
     * @return
     */
    @PostMapping(value = "/ai/chatmemory/prompt/tool/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Map<String, Object>> chatmemoryPromptToolStream(@RequestBody @Valid ChatRequest request) {
        // 判断sessionID是否为空，为空则生成一个sessionID
        if (request.getSession() == null) {
            request.setSession("session_" + System.currentTimeMillis());
        }
        return chatClient
                .prompt(PromptConsole.MYSQL_STUDYDB_PROMPT)
                .tools(new DateTimeTools(), new MysqlTools(jdbcTemplate))
                .advisors(
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory)
                                .conversationId(request.getSession()).build()
                )
                .user(request.getMessage())
                .stream()
                .chatResponse()
                .map(chatResponse -> {
                    // 构建响应Map
                    Map<String, Object> responseMap = Map.of(
                            "session", request.getSession(),
                            "round", chatMemory.get(request.getSession()) != null ?
                                    chatMemory.get(request.getSession()).size() : 0,
                            "content", chatResponse.getResult().getOutput().getText(),
                            "tools", chatResponse.getResult().getOutput().getToolCalls()
                                    .stream().map(tc -> tc.toString()).toList()
                    );

                    // 打印日志
                    log.info("流式响应数据: {}",
                            responseMap.entrySet().stream()
                                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                                    .collect(Collectors.joining(", ", "{", "}"))
                    );

                    return responseMap; // 直接返回Map
                });
    }


    /**
     * 处理AI生成的聊天会话POST请求（同步响应）
     *
     * @param request
     * @return
     */
    @PostMapping("/ai/chatmemory/tool/sync")
    public ChatReply chatmemoryToolSync(@RequestBody @Valid ChatRequest request) {
        // 判断sessionID是否为空，为空则生成一个sessionID
        if (request.getSession() == null) {
            request.setSession("session_" + System.currentTimeMillis());
        }
        ChatResponse chatResponse = chatClient
                .prompt()
                .tools(new DateTimeTools(), new MysqlTools(jdbcTemplate))
                .advisors(
                        new SimpleLoggerAdvisor(), // 日志
                        MessageChatMemoryAdvisor.builder(chatMemory).
                                conversationId(request.getSession()).build() // 聊天记忆功能
                )
                .user(request.getMessage())
                .call().chatResponse();

        return new ChatReply(chatResponse.getResult().getOutput().getText(), request.getSession());
    }


    /**
     * 处理AI生成的聊天会话POST请求（流式响应）
     *
     * @param request
     * @return
     */
    @PostMapping(value = "/ai/chatmemory/tool/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Map<String, Object>> chatmemoryToolStream(@RequestBody @Valid ChatRequest request) {
        final String sessionId = request.getSession() != null ?
                request.getSession() : "session_" + System.currentTimeMillis();

        return chatClient.prompt()
                .tools(new DateTimeTools(), new MysqlTools(jdbcTemplate))
                .advisors(
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory)
                                .conversationId(sessionId).build()
                )
                .user(request.getMessage())
                .stream()
                .chatResponse()
                .map(chatResponse -> {
                    // 构建响应Map
                    Map<String, Object> responseMap = Map.of(
                            "session", sessionId,
                            "round", chatMemory.get(sessionId) != null ?
                                    chatMemory.get(sessionId).size() : 0,
                            "content", chatResponse.getResult().getOutput().getText(),
                            "tools", chatResponse.getResult().getOutput().getToolCalls()
                                    .stream().map(tc -> tc.toString()).toList()
                    );

                    // 打印日志
                    log.info("流式响应数据: {}",
                            responseMap.entrySet().stream()
                                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                                    .collect(Collectors.joining(", ", "{", "}"))
                    );

                    return responseMap; // 直接返回Map
                });
    }


    /**
     * 处理AI生成的聊天会话POST请求（同步响应）
     *
     * @param request
     * @return
     */
    @PostMapping("/ai/chatmemory/sync")
    public ChatReply generatePost(@RequestBody @Valid ChatRequest request) {
        // 判断sessionID是否为空，为空则生成一个sessionID
        if (request.getSession() == null) {
            request.setSession("session_" + System.currentTimeMillis());
        }
        String content = chatClient.prompt()
                .advisors(
                        new SimpleLoggerAdvisor(), // 日志
                        MessageChatMemoryAdvisor.builder(chatMemory).
                                conversationId(request.getSession()).build() // 聊天记忆功能
                )
                .user(request.getMessage())
                .call()
                .content();
        return new ChatReply(content, request.getSession());
    }


    /**
     * 处理AI生成的聊天会话POST请求（流式响应）
     *
     * @param request
     * @return
     */
    @PostMapping(value = "/ai/chatmemory/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Map<String, Object>> generateSee(@RequestBody @Valid ChatRequest request) {
        final String sessionId = request.getSession() != null ?
                request.getSession() : "session_" + System.currentTimeMillis();

        return chatClient.prompt()
                .advisors(
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory)
                                .conversationId(sessionId).build()
                )
                .user(request.getMessage())
                .stream()
                .chatResponse()
                .map(chatResponse -> Map.of(
                        "session", sessionId,
                        "content", chatResponse.getResult().getOutput().getText()
                ));
    }

    /**
     * 处理AI生成的GET请求 同步
     *
     * @param message
     * @return
     */
    @GetMapping("/ai/sync")
    public Map generate(@RequestParam(value = "message", defaultValue = "Tell me a joke") String message) {
        return Map.of("generation", deepSeekChatModel.call(message));
    }

    /**
     * 处理AI生成的GET请求 流式
     *
     * @param message
     * @return
     */
    @GetMapping(value = "/ai/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatResponse> generateStream(@RequestParam(value = "message", defaultValue = "Tell me a joke") String message) {
        var prompt = new Prompt(new UserMessage(message));
        return deepSeekChatModel.stream(prompt);
    }
}