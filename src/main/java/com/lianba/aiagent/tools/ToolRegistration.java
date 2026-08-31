package com.lianba.aiagent.tools;

import com.lianba.aiagent.manager.OssManager;
import com.lianba.aiagent.service.WanImageService;
import jakarta.annotation.Resource;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * 集中的工具注册类
 */
@Configuration
public class ToolRegistration {

    @Value("${search-api.api-key}")
    private String searchApiKey;

    @Value("${spring.mail.username:}")
    private String mailFromAddress;

    @Resource
    private WanImageService wanImageService;

    @Resource
    private OssManager ossManager;

    @Resource
    private ObjectProvider<JavaMailSender> mailSenderProvider;

    @Resource
    private ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    @Bean
    public ToolCallback[] allTools() {
        FileOperationTool fileOperationTool = new FileOperationTool();
        WebSearchTool webSearchTool = new WebSearchTool(searchApiKey);
        WebScrapingTool webScrapingTool = new WebScrapingTool();
        ResourceDownloadTool resourceDownloadTool = new ResourceDownloadTool();
        TerminalOperationTool terminalOperationTool = new TerminalOperationTool();
        PDFGenerationTool pdfGenerationTool = new PDFGenerationTool(ossManager);
        TerminateTool terminateTool = new TerminateTool();
        AskHumanTool askHumanTool = new AskHumanTool();
        ImageGenerationTool imageGenerationTool = new ImageGenerationTool(wanImageService);
        TimeTool timeTool = new TimeTool();
        // 邮件服务和数据源未配置时传入 null，由工具内部优雅降级
        EmailSendingTool emailSendingTool = new EmailSendingTool(mailSenderProvider.getIfAvailable(), mailFromAddress);
        DatabaseOperationTool databaseOperationTool = new DatabaseOperationTool(jdbcTemplateProvider.getIfAvailable());
        return ToolCallbacks.from(
                fileOperationTool,
                webSearchTool,
                webScrapingTool,
                resourceDownloadTool,
                terminalOperationTool,
                pdfGenerationTool,
                terminateTool,
                askHumanTool,
                imageGenerationTool,
                timeTool,
                emailSendingTool,
                databaseOperationTool
        );
    }
}
