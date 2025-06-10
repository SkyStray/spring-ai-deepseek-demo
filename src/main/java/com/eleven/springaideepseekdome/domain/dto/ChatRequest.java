package com.eleven.springaideepseekdome.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatRequest{
        @NotBlank(message = "消息内容不能为空")
        String message; // 消息内容
        String session; // 会话ID
}
