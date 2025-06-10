package com.eleven.springaideepseekdome.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatReply {
        String content; // 回答内容
        String session; // 会话ID
}
