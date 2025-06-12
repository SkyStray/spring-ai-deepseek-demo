package com.eleven.springaideepseekdome.controller;

import com.eleven.springaideepseekdome.console.PromptConsole;
import com.eleven.springaideepseekdome.domain.dto.ChatCommonRequest;
import com.eleven.springaideepseekdome.domain.dto.ChatReply;
import com.eleven.springaideepseekdome.tools.DateTimeTools;
import com.eleven.springaideepseekdome.tools.MysqlTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import javax.validation.Valid;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RestController
public class ChatCommonController {

    @Autowired
    private ChatClient chatClient;
    @Autowired
    private ChatMemory chatMemory;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SyncMcpToolCallbackProvider toolCallbackProvider;


    /**
     * 统一同步聊天接口
     *
     * @param request 包含以下控制参数：
     *   - session：会话ID（为空时自动生成）
     *   - message：用户消息内容
     *   - toolType：工具类型枚举（MCP/FUNCTION/NONE）
     *   - usePrompt：是否使用预设提示词
     *
     * 处理流程：
     * 1. 会话ID处理 → 2. 提示词配置 → 3. 顾问配置 →
     * 4. 工具配置 → 5. 执行请求 → 6. 返回响应
     */
    @PostMapping("/ai/chat/sync")
    public ChatReply unifiedChatSync(@RequestBody @Valid ChatCommonRequest request) {
        final String sessionId = getOrGenerateSessionId(request.getSession());

        ChatClient.ChatClientRequestSpec chatClientRequestSpec;

        // 添加提示词
        if (request.isUsePrompt()) {
            chatClientRequestSpec = chatClient.prompt(PromptConsole.MYSQL_STUDYDB_PROMPT);
        }else {
           chatClientRequestSpec = chatClient.prompt();
        }

        // 构建基础请求链
        chatClientRequestSpec = chatClientRequestSpec.advisors(
                /**
                 * 添加日志记录顾问：
                 * 用于记录聊天请求和响应的详细信息，便于调试和监控
                 * 实现类：{@code SimpleLoggerAdvisor}
                 */
                new SimpleLoggerAdvisor(),
                /**
                 * 添加聊天记忆顾问：
                 * 为聊天会话提供记忆功能，保持对话上下文连续性
                 *
                 * 配置说明：
                 * - {@code chatMemory}：聊天记忆存储实现
                 * - {@code conversationId}：使用会话ID作为记忆存储的键
                 *   确保同一会话中的消息保持上下文关联
                 */
                MessageChatMemoryAdvisor.builder(chatMemory)
                        .conversationId(sessionId).build()
        ).user(request.getMessage());

        // 添加工具
        switch (request.getToolType()) {
            case MCP:
                ToolCallback[] toolCallbacks = toolCallbackProvider.getToolCallbacks();
                log.info("MCP toolCallbacks: {}", toolCallbacks);
                chatClientRequestSpec = chatClientRequestSpec.toolCallbacks(toolCallbacks);
                break;
            case FUNCTION:
                chatClientRequestSpec = chatClientRequestSpec.tools(new DateTimeTools(), new MysqlTools(jdbcTemplate));
                break;
            case NONE:
            default:
                // 无额外工具
                log.info("无额外工具");
        }

        // 执行请求
        ChatResponse chatResponse = chatClientRequestSpec.call().chatResponse();
        return new ChatReply(chatResponse.getResult().getOutput().getText(), sessionId);
    }


    /**
     * 统一流式聊天接口
     *
     * @param request 参数说明同同步接口
     * @return 流式响应包含以下字段：
     *   - session：会话ID
     *   - round：当前对话轮次
     *   - content：AI生成内容
     *   - tools：使用过的工具列表（若有）
     *
     * 特别说明：
     * 流式响应使用Server-Sent Events(SSE)协议
     * 媒体类型：{@code MediaType.TEXT_EVENT_STREAM_VALUE}
     */
    @PostMapping(value = "/ai/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Map<String, Object>> unifiedChatStream(@RequestBody @Valid ChatCommonRequest request) {
        final String sessionId = getOrGenerateSessionId(request.getSession());

        ChatClient.ChatClientRequestSpec chatClientRequestSpec;

        // 添加提示词
        if (request.isUsePrompt()) {
            chatClientRequestSpec = chatClient.prompt(PromptConsole.MYSQL_STUDYDB_PROMPT);
        }else {
            chatClientRequestSpec = chatClient.prompt();
        }

        // 构建基础请求链
        chatClientRequestSpec = chatClientRequestSpec.advisors(
                new SimpleLoggerAdvisor(),
                MessageChatMemoryAdvisor.builder(chatMemory)
                        .conversationId(sessionId).build()
        ).user(request.getMessage());

        // 添加工具
        switch (request.getToolType()) {
            case MCP:
                ToolCallback[] toolCallbacks = toolCallbackProvider.getToolCallbacks();
                log.info("MCP toolCallbacks: {}", toolCallbacks);
                chatClientRequestSpec = chatClientRequestSpec.toolCallbacks(toolCallbacks);
                break;
            case FUNCTION:
                chatClientRequestSpec = chatClientRequestSpec.tools(new DateTimeTools(), new MysqlTools(jdbcTemplate));
                break;
            case NONE:
            default:
                // 无额外工具
                log.info("无额外工具");
        }

        // 返回流式响应
        return chatClientRequestSpec
                .stream()
                .chatResponse()
                .map(chatResponse -> buildResponseMap(sessionId, chatResponse));
    }

    // 辅助方法
    private String getOrGenerateSessionId(String session) {
        return session != null ? session : "session_" + System.currentTimeMillis();
    }

    private Map<String, Object> buildResponseMap(String sessionId, ChatResponse chatResponse) {
        Map<String, Object> responseMap = Map.of(
                "session", sessionId,
                "round", chatMemory.get(sessionId) != null ?
                        chatMemory.get(sessionId).size() : 0,
                "content", chatResponse.getResult().getOutput().getText(),
                "tools", Optional.ofNullable(chatResponse.getResult().getOutput().getToolCalls())
                        .orElse(Collections.emptyList())
                        .stream().map(Object::toString).toList()
        );

        log.info("流式响应数据: {}",
                responseMap.entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .collect(Collectors.joining(", ", "{", "}"))
        );

        return responseMap;
    }

}