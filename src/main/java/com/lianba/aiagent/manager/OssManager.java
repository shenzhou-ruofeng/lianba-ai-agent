package com.lianba.aiagent.manager;

import cn.hutool.core.util.StrUtil;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.CannedAccessControlList;
import com.lianba.aiagent.config.OssClientConfig;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URL;
import java.util.Date;

/**
 * 阿里云对象存储 OSS 操作管理器
 * 未配置密钥时优雅降级（上传方法返回 null，不影响本地存储流程）
 */
@Slf4j
@Component
public class OssManager {

    /**
     * 私有读对象降级签名 URL 的有效期：7 天
     */
    private static final long PRESIGNED_URL_EXPIRE_MILLIS = 7L * 24 * 60 * 60 * 1000;

    @Resource
    private OssClientConfig ossClientConfig;

    /**
     * 懒加载的 OSS 客户端（仅在配置完整且首次上传时创建）
     */
    private volatile OSS ossClient;

    /**
     * 判断 OSS 是否已配置完整
     */
    public boolean isConfigured() {
        return StrUtil.isNotBlank(ossClientConfig.getEndpoint())
                && StrUtil.isNotBlank(ossClientConfig.getAccessKeyId())
                && StrUtil.isNotBlank(ossClientConfig.getAccessKeySecret())
                && StrUtil.isNotBlank(ossClientConfig.getBucket());
    }

    private OSS getOssClient() {
        if (ossClient == null) {
            synchronized (this) {
                if (ossClient == null) {
                    ossClient = new OSSClientBuilder().build(
                            ossClientConfig.getEndpoint(),
                            ossClientConfig.getAccessKeyId(),
                            ossClientConfig.getAccessKeySecret());
                }
            }
        }
        return ossClient;
    }

    /**
     * 上传文件到对象存储
     *
     * @param key  对象键（如 pdf/xxx.pdf）
     * @param file 本地文件
     * @return 可访问的文件 URL；未配置 OSS 或上传失败时返回 null
     */
    public String uploadFile(String key, File file) {
        if (!isConfigured()) {
            log.info("OSS 未配置，跳过对象存储上传: {}", key);
            return null;
        }
        long start = System.currentTimeMillis();
        try {
            OSS client = getOssClient();
            client.putObject(ossClientConfig.getBucket(), key, file);
            String url = buildAccessUrl(client, key);
            log.info("文件已上传至 OSS（耗时 {} ms）: {}", System.currentTimeMillis() - start, url);
            return url;
        } catch (Exception e) {
            log.error("上传文件到 OSS 失败, key={}: {}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 构建对象的可访问 URL：
     * 优先将对象 ACL 设为公共读并返回固定 URL（自定义域名或 bucket 默认域名）；
     * 若 Bucket 开启了阻止公共访问导致设置 ACL 失败，降级生成 7 天有效期的签名 URL
     */
    private String buildAccessUrl(OSS client, String key) {
        try {
            client.setObjectAcl(ossClientConfig.getBucket(), key, CannedAccessControlList.PublicRead);
            String host = ossClientConfig.getHost();
            if (StrUtil.isBlank(host)) {
                // 按 OSS 默认外网域名规则拼接：https://{bucket}.{endpoint}
                String endpoint = ossClientConfig.getEndpoint()
                        .replaceFirst("^https?://", "");
                host = String.format("https://%s.%s", ossClientConfig.getBucket(), endpoint);
            }
            return StrUtil.removeSuffix(host, "/") + "/" + key;
        } catch (Exception e) {
            log.warn("设置对象公共读 ACL 失败（Bucket 可能开启了阻止公共访问），降级为签名 URL: {}", e.getMessage());
            Date expiration = new Date(System.currentTimeMillis() + PRESIGNED_URL_EXPIRE_MILLIS);
            URL signedUrl = client.generatePresignedUrl(ossClientConfig.getBucket(), key, expiration);
            return signedUrl.toString();
        }
    }

    /**
     * 应用关闭时释放 OSS 客户端连接资源
     */
    @PreDestroy
    public void shutdown() {
        if (ossClient != null) {
            ossClient.shutdown();
        }
    }
}
