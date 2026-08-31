package com.yupi.yuaiagent.tools;

import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * 邮件发送工具类（基于 Spring Mail 给用户发送邮件，比如约会计划、恋爱报告等）
 * <p>
 * 未配置 spring.mail 时优雅降级，返回提示信息而不是让智能体中断
 */
public class EmailSendingTool {

    /**
     * 简单的邮箱格式校验正则
     */
    private static final String EMAIL_REGEX = "^[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}$";

    private final JavaMailSender mailSender;

    /**
     * 发件人地址（一般与 spring.mail.username 一致）
     */
    private final String fromAddress;

    public EmailSendingTool(JavaMailSender mailSender, String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Tool(description = "Send an email to the user, useful for delivering date plans, love reports or reminders")
    public String sendEmail(@ToolParam(description = "Recipient email address") String to,
                            @ToolParam(description = "Email subject") String subject,
                            @ToolParam(description = "Email plain text content") String content) {
        if (mailSender == null || StrUtil.isBlank(fromAddress)) {
            return "邮件服务未配置，请先在 application.yml 中配置 spring.mail 相关信息（host、username、password）";
        }
        if (!ReUtil.isMatch(EMAIL_REGEX, StrUtil.trim(to))) {
            return "Error sending email: 收件人邮箱格式不正确：" + to;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(StrUtil.trim(to));
            message.setSubject(subject);
            message.setText(content);
            mailSender.send(message);
            return "Email sent successfully to: " + to;
        } catch (Exception e) {
            return "Error sending email: " + e.getMessage();
        }
    }
}
