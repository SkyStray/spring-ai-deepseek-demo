package com.eleven.springaideepseekdome.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.context.i18n.LocaleContextHolder;

import java.time.LocalDateTime;

@Slf4j
public class DateTimeTools {

    @Tool(description = "获取用户所在时区的当前日期和时间")
    String getCurrentDateTime() {
        log.info("tool工具调用: getCurrentDateTime 获取用户所在时区的当前日期和时间");
        return LocalDateTime.now().atZone(LocaleContextHolder.getTimeZone().toZoneId()).toString();
    }

}