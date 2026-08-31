package com.yupi.yuaiagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MIMO 多模态模型配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "mimo")
public class MiMoProperties {

    /**
     * MIMO API 基础地址
     */
    private String baseUrl = "https://api.xiaomimimo.com/v1";

    /**
     * MIMO API 密钥
     */
    private String apiKey;
}
