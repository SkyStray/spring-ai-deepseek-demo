package com.eleven.springaideepseekdome.domain.dto;

import lombok.Data;

@Data
public class ChatCommonRequest {
    /**
     * 会话ID
     * <p>
     * - 用于标识连续对话会话
     * - 为空时将自动生成新会话ID
     * - 相同会话ID可保持对话上下文连续性
     */
    private String session;

    /**
     * 用户输入消息内容
     * <p>
     * - 必填字段
     * - 包含用户向AI提出的问题或指令
     */
    private String message;

    /**
     * 工具类型选择
     * <p>
     * 控制AI使用的功能扩展工具：
     * - MCP: 使用MCP工具回调（需配置SyncMcpToolCallbackProvider）
     * - FUNCTION: 使用函数调用工具（DateTimeTools/MysqlTools）
     * - NONE: 不使用额外工具（默认）
     *
     * @see ToolType
     */
    private ToolType toolType = ToolType.NONE;

    /**
     * 是否使用预设提示词
     * <p>
     * - true: 使用 {@code PromptConsole.MYSQL_STUDYDB_PROMPT} 作为系统提示
     * - false: 不使用额外提示词（默认）
     *
     * @see com.eleven.springaideepseekdome.console.PromptConsole#MYSQL_STUDYDB_PROMPT
     */
    private boolean usePrompt = false;

    /**
     * 工具类型枚举
     * <p>
     * 定义可用的AI功能扩展工具类型
     */
    public enum ToolType {
        /**
         * MCP工具回调模式
         * <p>
         * 适用于需要调用外部服务的场景
         */
        MCP,

        /**
         * 函数调用模式
         * <p>
         * 使用内置工具类（DateTimeTools/MysqlTools）
         */
        FUNCTION,

        /**
         * 无额外工具
         * <p>
         * 仅使用基础AI能力（默认）
         */
        NONE
    }
}