package com.lianba.aiagent.app.model;

import java.util.List;

/**
 * 恋爱报告（结构化输出结果）
 *
 * @param title       报告标题，格式为 {用户名}的恋爱报告
 * @param suggestions 恋爱建议列表（3-5 条具体可执行的建议）
 */
public record LoveReport(String title, List<String> suggestions) {
}
