package com.lianba.aiagent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * 时间工具类（提供当前时间查询、日期倒计时计算等功能）
 */
public class TimeTool {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Tool(description = "Get the current date, time and day of week. Optionally specify a time zone id such as 'Asia/Shanghai'")
    public String getCurrentDateTime(@ToolParam(required = false, description = "Time zone id, e.g. 'Asia/Shanghai', defaults to the system time zone") String timeZone) {
        try {
            ZoneId zoneId = (timeZone == null || timeZone.isBlank()) ? ZoneId.systemDefault() : ZoneId.of(timeZone);
            ZonedDateTime now = ZonedDateTime.now(zoneId);
            String dayOfWeek = now.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.CHINA);
            return String.format("当前时间：%s %s（时区：%s）", now.format(DATE_TIME_FORMATTER), dayOfWeek, zoneId.getId());
        } catch (Exception e) {
            return "Error getting current date time: " + e.getMessage();
        }
    }

    @Tool(description = "Calculate the number of days from today until a target date, useful for countdowns like anniversaries, festivals or date plans")
    public String getDaysUntil(@ToolParam(description = "Target date in yyyy-MM-dd format, e.g. '2026-08-19'") String targetDate) {
        try {
            LocalDate target = LocalDate.parse(targetDate, DATE_FORMATTER);
            LocalDate today = LocalDate.now();
            long days = ChronoUnit.DAYS.between(today, target);
            if (days > 0) {
                return String.format("今天是 %s，距离 %s 还有 %d 天", today.format(DATE_FORMATTER), targetDate, days);
            }
            if (days == 0) {
                return String.format("目标日期 %s 就是今天", targetDate);
            }
            return String.format("今天是 %s，目标日期 %s 已经过去 %d 天", today.format(DATE_FORMATTER), targetDate, -days);
        } catch (Exception e) {
            return "Error calculating days: 日期格式应为 yyyy-MM-dd，" + e.getMessage();
        }
    }
}
