package com.yupi.yuaiagent.service;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.yupi.yuaiagent.config.WanImageProperties;
import com.yupi.yuaiagent.constant.FileConstant;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 万相 wan2.7-image 图片生成/编辑服务（阿里云百炼 DashScope）
 * 统一模型同时支持文生图与图像编辑（图生图）
 * 内置熔断器、降级和限流策略：
 * - 熔断器：连续失败 ≥ 3 次 → 熔断打开 30 秒，期间直接返回降级提示
 * - 降级：返回友好提示信息
 * - 限流：滑动窗口，每分钟最多 5 次请求
 */
@Service
@Slf4j
public class WanImageService {

    @Resource
    private WanImageProperties wanImageProperties;

    private static final String IMAGE_GENERATION_PATH = "/services/aigc/multimodal-generation/generation";

    // ==================== 熔断器状态 ====================
    /** 连续失败计数 */
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    /** 熔断器打开时间戳（毫秒），0 表示未打开 */
    private final AtomicLong circuitOpenTime = new AtomicLong(0);
    /** 熔断失败阈值 */
    private static final int CIRCUIT_BREAKER_THRESHOLD = 3;
    /** 熔断恢复等待时间（毫秒） */
    private static final long CIRCUIT_BREAKER_TIMEOUT_MS = 30_000;

    // ==================== 限流状态 ====================
    /** 滑动窗口：最近一次请求时间戳数组（固定 5 个槽位） */
    private final long[] requestTimestamps = new long[5];
    /** 限流窗口大小（毫秒） */
    private static final long RATE_LIMIT_WINDOW_MS = 60_000;
    /** 窗口内最大请求数 */
    private static final int RATE_LIMIT_MAX_REQUESTS = 5;

    /**
     * 生成图片
     *
     * @param prompt 图片描述
     * @param size   图片尺寸档位（1K 或 2K），默认 1K
     * @return 生成的图片 URL 或错误信息
     */
    public String generateImage(String prompt, String size) {
        return generateImage(prompt, size, null);
    }

    /**
     * 生成图片（支持参考图，即图像编辑/图生图）
     *
     * @param prompt         图片描述
     * @param size           图片尺寸档位（1K 或 2K），默认 1K
     * @param referenceImage 参考图（用户上传图片的本地文件名或下载 URL），为空则纯文生图
     * @return 生成的图片 URL 或错误信息
     */
    public String generateImage(String prompt, String size, String referenceImage) {
        // 1. 限流检查
        String rateLimitMsg = checkRateLimit();
        if (rateLimitMsg != null) {
            log.warn("WanImageService: rate limited, prompt={}", prompt);
            return rateLimitMsg;
        }

        // 2. 熔断检查
        String circuitMsg = checkCircuitBreaker();
        if (circuitMsg != null) {
            log.warn("WanImageService: circuit breaker open, prompt={}", prompt);
            return circuitMsg;
        }

        // 3. 调用 API
        String baseUrl = wanImageProperties.getBaseUrl();
        String apiKey = wanImageProperties.getApiKey();

        if (StrUtil.isBlank(apiKey)) {
            return fallback("API 密钥未配置");
        }

        if (StrUtil.isBlank(prompt)) {
            return fallback("图片描述不能为空");
        }

        String imageSize = normalizeSize(size);

        // DashScope multimodal-generation 消息体：content 数组中先放参考图（可选），再放文本提示词
        JSONArray content = new JSONArray();

        // 图像编辑：将用户上传的本地参考图以 base64 data URL 直传（本地图片 URL 外网不可达）
        if (StrUtil.isNotBlank(referenceImage)) {
            String referenceDataUrl = loadReferenceImageAsDataUrl(referenceImage);
            if (referenceDataUrl != null) {
                content.add(new JSONObject().set("image", referenceDataUrl));
                log.info("WanImageService: attached reference image, source={}", referenceImage);
            } else if (referenceImage.startsWith("http://") || referenceImage.startsWith("https://")) {
                // 非本地图片的远程 URL，直接传给万相
                content.add(new JSONObject().set("image", referenceImage));
                log.info("WanImageService: attached remote reference image url");
            } else {
                log.warn("WanImageService: reference image not found, fallback to text-to-image, source={}", referenceImage);
            }
        }
        content.add(new JSONObject().set("text", prompt));

        JSONObject message = new JSONObject();
        message.set("role", "user");
        message.set("content", content);

        JSONArray messages = new JSONArray();
        messages.add(message);
        JSONObject input = new JSONObject();
        input.set("messages", messages);

        JSONObject parameters = new JSONObject();
        parameters.set("size", imageSize);
        parameters.set("n", 1);
        parameters.set("watermark", false);

        JSONObject requestBody = new JSONObject();
        requestBody.set("model", wanImageProperties.getModel());
        requestBody.set("input", input);
        requestBody.set("parameters", parameters);

        String url = baseUrl + IMAGE_GENERATION_PATH;
        log.info("WanImageService: calling DashScope wan2.7-image API, prompt={}, size={}", prompt, imageSize);

        try (HttpResponse response = HttpRequest.post(url)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(requestBody.toString())
                .timeout(120000)
                .execute()) {

            if (response.getStatus() != 200) {
                log.error("WanImageService: API returned status={}, body={}", response.getStatus(), response.body());
                onFailure();
                return fallback("服务暂时异常");
            }

            JSONObject result = JSONUtil.parseObj(response.body());

            // DashScope 业务错误：HTTP 200 但返回 code/message
            String errorCode = result.getStr("code");
            if (StrUtil.isNotBlank(errorCode)) {
                log.error("WanImageService: API business error, code={}, message={}", errorCode, result.getStr("message"));
                onFailure();
                return fallback("服务暂时异常");
            }

            // 响应格式：{ "output": { "choices": [{ "message": { "content": [{ "image": "..." }] } }] } }
            String remoteImageUrl = extractImageUrl(result);

            if (StrUtil.isBlank(remoteImageUrl)) {
                log.warn("WanImageService: unknown response format: {}", response.body());
                onSuccess();
                return "图片生成成功！但无法解析返回的图片地址，请稍后重试。";
            }

            // 直接返回万相输出的可访问 URL（免二次中转，加载更快）；
            // 同时异步下载一份到本地作为持久化备份（DashScope 图片 URL 24 小时后过期）
            onSuccess();
            final String backupUrl = remoteImageUrl;
            CompletableFuture.runAsync(() -> downloadAndSaveImage(backupUrl));
            return "图片生成成功！下载地址：" + remoteImageUrl;

        } catch (Exception e) {
            log.error("WanImageService: API call failed", e);
            onFailure();
            return buildUserFriendlyError(e);
        }
    }

    /**
     * 归一化尺寸档位：wan2.7-image 仅支持 1K、2K，3K/4K 等更高档位降级为 2K，默认 1K
     */
    private String normalizeSize(String size) {
        if (StrUtil.isBlank(size)) {
            return "1K";
        }
        String upper = size.trim().toUpperCase();
        if ("1K".equals(upper)) {
            return "1K";
        }
        if ("2K".equals(upper) || "3K".equals(upper) || "4K".equals(upper)) {
            return "2K";
        }
        return "1K";
    }

    /**
     * 从 DashScope 响应中提取图片 URL
     * 格式：output.choices[0].message.content[] 中 type=image 的元素
     */
    private String extractImageUrl(JSONObject result) {
        JSONObject output = result.getJSONObject("output");
        if (output == null) {
            return null;
        }
        JSONArray choices = output.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        JSONObject message = choices.getJSONObject(0).getJSONObject("message");
        if (message == null) {
            return null;
        }
        JSONArray content = message.getJSONArray("content");
        if (content == null || content.isEmpty()) {
            return null;
        }
        for (int i = 0; i < content.size(); i++) {
            String imageUrl = content.getJSONObject(i).getStr("image");
            if (StrUtil.isNotBlank(imageUrl)) {
                return imageUrl;
            }
        }
        return null;
    }

    /**
     * 将参考图转换为 base64 data URL。
     * 支持两种输入形式：
     * 1. 本地下载 URL（含 /files/download/image/ 片段，如 /api/files/download/image/2026-07-27/xxx.png）
     * 2. 相对文件名（如 2026-07-27/xxx.png 或 ai-generated/xxx.png）
     *
     * @param referenceImage 参考图标识
     * @return data URL，文件不存在或读取失败返回 null
     */
    private String loadReferenceImageAsDataUrl(String referenceImage) {
        try {
            String relative = referenceImage.trim();
            // 从下载 URL 中提取相对路径
            int marker = relative.indexOf("/files/download/image/");
            if (marker >= 0) {
                relative = relative.substring(marker + "/files/download/image/".length());
            }
            // 去掉可能携带的查询参数
            int queryIdx = relative.indexOf('?');
            if (queryIdx >= 0) {
                relative = relative.substring(0, queryIdx);
            }
            Path imagePath = Paths.get(FileConstant.FILE_SAVE_DIR, "image").resolve(relative).normalize();
            // 防路径穿越：解析后必须仍在 image 目录下
            Path imageRoot = Paths.get(FileConstant.FILE_SAVE_DIR, "image").normalize();
            if (!imagePath.startsWith(imageRoot) || !Files.isRegularFile(imagePath)) {
                return null;
            }
            byte[] bytes = Files.readAllBytes(imagePath);
            if (bytes.length == 0) {
                return null;
            }
            String lower = imagePath.getFileName().toString().toLowerCase();
            String mimeType = "image/png";
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
                mimeType = "image/jpeg";
            } else if (lower.endsWith(".webp")) {
                mimeType = "image/webp";
            } else if (lower.endsWith(".gif")) {
                mimeType = "image/gif";
            }
            return "data:" + mimeType + ";base64," + Base64.encode(bytes);
        } catch (Exception e) {
            log.error("Failed to load reference image: {}", referenceImage, e);
            return null;
        }
    }

    /**
     * 从远程 URL 下载图片并保存到本地 tmp/image/ai-generated/ 目录。
     * @param imageUrl 远程图片 URL
     * @return 本地文件绝对路径，失败返回 null
     */
    private String downloadAndSaveImage(String imageUrl) {
        HttpResponse downloadResponse = null;
        try {
            // 确定文件扩展名
            String extension = ".png";
            String lower = imageUrl.toLowerCase();
            if (lower.contains(".jpg") || lower.contains(".jpeg")) {
                extension = ".jpg";
            } else if (lower.contains(".webp")) {
                extension = ".webp";
            } else if (lower.contains(".gif")) {
                extension = ".gif";
            }

            String fileName = UUID.randomUUID().toString().replace("-", "") + extension;
            Path saveDir = Paths.get(FileConstant.FILE_SAVE_DIR, "image", "ai-generated");
            Files.createDirectories(saveDir);
            Path targetPath = saveDir.resolve(fileName);

            // 下载图片：先发起请求并检查 HTTP 状态码和 Content-Type
            log.info("Downloading image from DashScope: {} -> {}", imageUrl, targetPath);
            downloadResponse = HttpRequest.get(imageUrl)
                    .timeout(60000)
                    .execute();

            int statusCode = downloadResponse.getStatus();
            if (statusCode != 200) {
                log.error("Failed to download image from DashScope, HTTP status={}, url={}", statusCode, imageUrl);
                return null;
            }

            // 检查 Content-Type，防止将错误页面（HTML/XML）误存为图片
            String contentType = downloadResponse.header("Content-Type");
            if (contentType != null && !contentType.toLowerCase().startsWith("image/")) {
                log.error("Unexpected Content-Type from image URL: {}, url={}", contentType, imageUrl);
                return null;
            }

            // 校验响应体大小（0 字节说明是空响应）
            String contentLengthHeader = downloadResponse.header("Content-Length");
            if (contentLengthHeader != null) {
                try {
                    long contentLength = Long.parseLong(contentLengthHeader.trim());
                    if (contentLength == 0) {
                        log.error("Empty response body (Content-Length=0) from image URL: {}", imageUrl);
                        return null;
                    }
                } catch (NumberFormatException ignored) {
                    // 非标准 Content-Length，忽略
                }
            }

            try (InputStream in = downloadResponse.bodyStream()) {
                Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            // 二次校验：确保下载的文件不为空且有合理大小
            long fileSize = Files.size(targetPath);
            if (fileSize == 0) {
                log.error("Downloaded image file is empty (0 bytes): {}", targetPath);
                try {
                    Files.deleteIfExists(targetPath);
                } catch (Exception ignored) {}
                return null;
            }
            if (fileSize < 100) {
                log.warn("Downloaded image file is suspiciously small ({} bytes): {}", fileSize, targetPath);
                // 仍然保存，但记录警告
            }

            log.info("Image saved to: {}, size={} bytes", targetPath, fileSize);
            return targetPath.toAbsolutePath().toString();
        } catch (Exception e) {
            log.error("Failed to download and save image from: {}", imageUrl, e);
            return null;
        } finally {
            // 确保 HTTP 响应被关闭
            if (downloadResponse != null) {
                try {
                    downloadResponse.close();
                } catch (Exception ignored) {}
            }
        }
    }

    // ==================== 熔断器方法 ====================

    /**
     * 检查熔断器状态
     * @return 如果熔断器打开则返回降级消息，否则返回 null
     */
    private String checkCircuitBreaker() {
        long openTime = circuitOpenTime.get();
        if (openTime > 0) {
            long elapsed = System.currentTimeMillis() - openTime;
            if (elapsed < CIRCUIT_BREAKER_TIMEOUT_MS) {
                // 熔断器仍打开
                return "【服务降级】图片生成服务暂时不可用（熔断保护中），请稍后重试。";
            } else {
                // 熔断器超时，尝试半开恢复
                circuitOpenTime.set(0);
                consecutiveFailures.set(0);
                log.info("WanImageService: circuit breaker half-open, attempt recovery");
            }
        }
        return null;
    }

    /**
     * 调用成功时重置熔断器
     */
    private void onSuccess() {
        consecutiveFailures.set(0);
        circuitOpenTime.set(0);
    }

    /**
     * 调用失败时增加计数，达到阈值则打开熔断器
     */
    private void onFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= CIRCUIT_BREAKER_THRESHOLD) {
            circuitOpenTime.set(System.currentTimeMillis());
            log.warn("WanImageService: circuit breaker OPEN after {} consecutive failures", failures);
        }
    }

    /**
     * 降级返回（仅用于内部已知原因，不暴露技术细节）
     */
    private String fallback(String reason) {
        return "【服务降级】图片生成服务暂时不可用，原因：" + reason + "。请稍后重试。";
    }

    /**
     * 将异常转换为用户友好的错误提示，隐藏内部技术细节（如 UnknownHostException 等）
     */
    private String buildUserFriendlyError(Exception e) {
        // 收集所有层次的异常信息：类名 + 消息 + 因果链
        StringBuilder fingerprint = new StringBuilder();
        Throwable current = e;
        while (current != null) {
            fingerprint.append(current.getClass().getSimpleName()).append(" ");
            if (current.getMessage() != null) {
                fingerprint.append(current.getMessage().toLowerCase()).append(" ");
            }
            current = current.getCause();
        }
        String fp = fingerprint.toString();

        // 根据异常指纹匹配
        if (fp.contains("unknownhost") || fp.contains("nohost") || fp.contains("connect")) {
            return "很抱歉，图片生成服务暂时无法连接，请稍后重试。";
        }
        if (fp.contains("timeout") || fp.contains("timed out") || fp.contains("timedout")) {
            return "图片生成请求超时（服务器响应较慢），请稍后重试或尝试简化图片描述。";
        }
        if (fp.contains("ssl") || fp.contains("certificate")) {
            return "图片生成服务连接异常，请稍后重试。";
        }
        // 其他异常统一返回通用提示，技术细节仅记录在日志中
        return "很抱歉，图片生成服务当前暂时不可用，请稍后重试。";
    }

    // ==================== 限流方法 ====================

    /**
     * 滑动窗口限流检查
     * @return 如果被限流则返回提示消息，否则返回 null
     */
    private synchronized String checkRateLimit() {
        long now = System.currentTimeMillis();
        long windowStart = now - RATE_LIMIT_WINDOW_MS;

        // 统计窗口内的请求数
        int countInWindow = 0;
        int oldestSlot = 0;
        long oldestTime = Long.MAX_VALUE;

        for (int i = 0; i < requestTimestamps.length; i++) {
            if (requestTimestamps[i] > windowStart) {
                countInWindow++;
            }
            if (requestTimestamps[i] < oldestTime) {
                oldestTime = requestTimestamps[i];
                oldestSlot = i;
            }
        }

        if (countInWindow >= RATE_LIMIT_MAX_REQUESTS) {
            return "【限流提示】请求过于频繁，每分钟最多 " + RATE_LIMIT_MAX_REQUESTS + " 次图片生成，请稍后再试。";
        }

        // 记录本次请求时间戳（替换最早的槽位）
        requestTimestamps[oldestSlot] = now;
        return null;
    }
}
