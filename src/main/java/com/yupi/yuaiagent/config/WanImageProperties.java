package com.yupi.yuaiagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 万相 wan2.7-image 图片生成/编辑模型配置属性（阿里云百炼 DashScope）
 */
@Data
@Component
@ConfigurationProperties(prefix = "wan-image")
public class WanImageProperties {

    /**
     * DashScope API 基础地址
     */
    private String baseUrl = "https://dashscope.aliyuncs.com/api/v1";

    /**
     * DashScope API 密钥（默认复用 spring.ai.dashscope.api-key）
     */
    private String apiKey;

    /**
     * 模型名称
     */
    private String model = "wan2.7-image";
}
