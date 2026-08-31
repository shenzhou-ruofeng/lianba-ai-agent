package com.lianba.aiagent.tools;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * 邮件发送工具单元测试（验证降级提示与参数校验，不实际发信）
 */
class EmailSendingToolTest {

    @Test
    void sendEmailWithoutMailSenderReturnsFriendlyMessage() {
        EmailSendingTool tool = new EmailSendingTool(null, "");
        String result = tool.sendEmail("test@example.com", "主题", "内容");
        Assertions.assertTrue(result.contains("邮件服务未配置"));
    }

    @Test
    void sendEmailWithInvalidRecipientReturnsError() {
        EmailSendingTool tool = new EmailSendingTool(new JavaMailSenderImpl(), "from@example.com");
        String result = tool.sendEmail("not-an-email", "主题", "内容");
        Assertions.assertTrue(result.contains("邮箱格式不正确"));
    }

    @Test
    void sendEmailDelegatesToMailSender() {
        // 用桩替身捕获发送的消息，验证组装逻辑
        StubMailSender stubMailSender = new StubMailSender();
        EmailSendingTool tool = new EmailSendingTool(stubMailSender, "from@example.com");
        String result = tool.sendEmail(" to@example.com ", "约会计划", "周六下午两点见");
        Assertions.assertTrue(result.startsWith("Email sent successfully"));
        Assertions.assertNotNull(stubMailSender.lastMessage);
        Assertions.assertEquals("from@example.com", stubMailSender.lastMessage.getFrom());
        Assertions.assertArrayEquals(new String[]{"to@example.com"}, stubMailSender.lastMessage.getTo());
        Assertions.assertEquals("约会计划", stubMailSender.lastMessage.getSubject());
    }

    /**
     * 仅记录消息不真正发送的桩实现
     */
    private static class StubMailSender extends JavaMailSenderImpl {
        SimpleMailMessage lastMessage;

        @Override
        public void send(SimpleMailMessage simpleMessage) {
            this.lastMessage = simpleMessage;
        }
    }
}
