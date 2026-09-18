package com.lianba.aiagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * DeepSeek 模型配置属性（OpenAI 兼容 API）
 */
@Data
@Component
@ConfigurationProperties(prefix = "deepseek")
public class DeepSeekProperties {

    /**
     * DeepSeek API 基础地址（OpenAI 兼容，官方可解析域名为 api.deepseek.com）
     */
    private String baseUrl = "https://api.deepseek.com/v1";

    /**
     * DeepSeek API 密钥
     */
    private String apiKey;

    /**
     * 对话/多模态理解模型（DeepSeek-Flash：原生多模态，处理图文对话；开启 thinking 参数后亦用于深度思考）
     */
    private String model = "deepseek-flash";
}
