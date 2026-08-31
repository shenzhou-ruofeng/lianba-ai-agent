package com.lianba.aiagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云对象存储 OSS 客户端配置
 */
@Configuration
@ConfigurationProperties(prefix = "oss.client")
@Data
public class OssClientConfig {

    /**
     * 地域节点（如 oss-cn-guangzhou.aliyuncs.com）
     */
    private String endpoint;

    /**
     * AccessKey ID
     */
    private String accessKeyId;

    /**
     * AccessKey Secret
     */
    private String accessKeySecret;

    /**
     * 存储桶名称
     */
    private String bucket;

    /**
     * 可选：自定义访问域名（如 CDN/CNAME 域名），为空时按 bucket + endpoint 自动拼接
     */
    private String host;
}
