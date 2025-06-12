package com.eleven.springaideepseekdome.config;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.mcp.customizer.McpSyncClientCustomizer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 自定义 MCP 同步客户端配置类
 * 用于配置同步客户端的各种行为，例如请求超时、文件系统访问、事件处理等。
 */
@Component
public class CustomMcpSyncClientCustomizer implements McpSyncClientCustomizer {

    /**
     * 定制化方法，用于配置 MCP 同步客户端的行为
     *
     * @param serverConfigurationName 服务器配置名称
     * @param spec                    同步客户端的规格对象，用于设置各种客户端行为
     */
    @Override
    public void customize(String serverConfigurationName, McpClient.SyncSpec spec) {

        // 设置请求超时时间为30秒
        spec.requestTimeout(Duration.ofSeconds(30));

        // 配置客户端可以访问的文件系统根目录（roots）
        // roots 是一个预先定义好的 List<URI> 对象，表示服务器可以访问的目录
        // spec.roots(roots);

        // 设置自定义采样处理器，用于处理来自服务器的消息生成请求
        // 这允许客户端保持对模型访问权限的控制
        // spec.sampling((CreateMessageRequest messageRequest) -> {
        //     // 处理 LLM 的采样请求
        //     CreateMessageResult result = ...; // 实际应根据业务逻辑填充结果
        //     return result;
        // });

        // 添加工具变更监听器，当服务器可用工具列表发生变化时触发
        spec.toolsChangeConsumer((List<McpSchema.Tool> tools) -> {
            // 在这里实现工具变更时的处理逻辑
        });

        // 添加资源变更监听器，当服务器可用资源列表发生变化时触发
        spec.resourcesChangeConsumer((List<McpSchema.Resource> resources) -> {
            // 在这里实现资源变更时的处理逻辑
        });

        // 添加提示变更监听器，当服务器可用提示列表发生变化时触发
        spec.promptsChangeConsumer((List<McpSchema.Prompt> prompts) -> {
            // 在这里实现提示变更时的处理逻辑
        });

        // 添加日志消息处理器，接收服务器发送的结构化日志消息
        spec.loggingConsumer((McpSchema.LoggingMessageNotification log) -> {
            // 在这里实现日志消息的处理逻辑
            System.out.println("MCP日志: " + log); // 添加此日志输出
        });
    }
}
