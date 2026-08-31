package com.lianba.aiagent.manager;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class OssManagerTest {

    @Resource
    private OssManager ossManager;

    @Test
    void uploadFile() throws Exception {
        assertTrue(ossManager.isConfigured(), "OSS 未配置完整");
        // 生成一个临时测试文件并上传
        File tempFile = Files.createTempFile("oss-upload-test", ".txt").toFile();
        Files.writeString(tempFile.toPath(), "lianba-ai-agent OSS upload test");
        try {
            String url = ossManager.uploadFile("test/oss-upload-test.txt", tempFile);
            assertNotNull(url, "上传失败，未返回 URL");
            System.out.println("OSS 文件 URL: " + url);
            // 验证 URL 可访问
            HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            assertEquals(200, connection.getResponseCode(), "URL 无法访问");
            connection.disconnect();
        } finally {
            tempFile.delete();
        }
    }
}
