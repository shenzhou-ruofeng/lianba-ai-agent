package com.yupi.yuaiagent.service;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.yupi.yuaiagent.config.MiMoProperties;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * MIMO-v2.5 视觉理解服务
 * 负责将图片发送给 MIMO 模型进行视觉理解，返回文本描述
 */
@Service
@Slf4j
public class MiMoVisionService {

    @Resource
    private MiMoProperties miMoProperties;

    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    /**
     * 对单张图片进行视觉理解
     *
     * @param imageUrl 图片 URL（需可公开访问或为本地静态资源地址）
     * @param prompt   可选的引导提示，为空则使用默认提示
     * @return 图片理解结果的文本描述
     */
    public String understandImage(String imageUrl, String prompt) {
        return understandImages(List.of(imageUrl), prompt);
    }

    /**
     * 对图片字节进行视觉理解（通过 base64 data URL 直传，避免本地图片 URL 外网不可达导致 MIMO 无法获取图片）
     *
     * @param imageBytes  图片字节数组
     * @param contentType 图片 MIME 类型（如 image/png），为空则默认 image/png
     * @param prompt      可选的引导提示
     * @return 图片理解结果的文本描述
     */
    public String understandImageBytes(byte[] imageBytes, String contentType, String prompt) {
        if (imageBytes == null || imageBytes.length == 0) {
            log.warn("MiMoVisionService: imageBytes is empty");
            return "";
        }
        String mimeType = StrUtil.isNotBlank(contentType) ? contentType : "image/png";
        String dataUrl = "data:" + mimeType + ";base64," + Base64.encode(imageBytes);
        return understandImages(List.of(dataUrl), prompt);
    }

    /**
     * 对多张图片进行视觉理解
     *
     * @param imageUrls 图片 URL 列表
     * @param prompt    可选的引导提示
     * @return 多张图片理解结果的合并文本
     */
    public String understandImages(List<String> imageUrls, String prompt) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            log.warn("MiMoVisionService: imageUrls is empty");
            return "";
        }

        String baseUrl = miMoProperties.getBaseUrl();
        String apiKey = miMoProperties.getApiKey();

        if (StrUtil.isBlank(apiKey)) {
            log.warn("MiMoVisionService: apiKey is blank, skip vision understanding");
            return "";
        }

        // 构建用户消息内容（文本 + 图片）
        JSONArray userContent = new JSONArray();

        // 添加文本提示
        String textPrompt = StrUtil.isNotBlank(prompt)
                ? prompt
                : "请详细描述这张图片中的内容，包括场景、人物、物体、文字、颜色、氛围等所有细节。";
        JSONObject textPart = new JSONObject();
        textPart.set("type", "text");
        textPart.set("text", textPrompt);
        userContent.add(textPart);

        // 添加图片
        for (String imageUrl : imageUrls) {
            JSONObject imagePart = new JSONObject();
            imagePart.set("type", "image_url");
            JSONObject imageUrlObj = new JSONObject();
            imageUrlObj.set("url", imageUrl);
            imagePart.set("image_url", imageUrlObj);
            userContent.add(imagePart);
        }

        // 构建消息
        JSONArray messages = new JSONArray();
        JSONObject userMessage = new JSONObject();
        userMessage.set("role", "user");
        userMessage.set("content", userContent);
        messages.add(userMessage);

        // 构建请求体
        JSONObject requestBody = new JSONObject();
        requestBody.set("model", "mimo-v2.5");
        requestBody.set("messages", messages);
        requestBody.set("max_tokens", 1000);

        // 发送请求
        String url = baseUrl + CHAT_COMPLETIONS_PATH;
        log.info("MiMoVisionService: calling MIMO API, image count={}", imageUrls.size());

        try (HttpResponse response = HttpRequest.post(url)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(requestBody.toString())
                .timeout(60000)
                .execute()) {

            if (response.getStatus() != 200) {
                log.error("MiMoVisionService: API returned status={}, body={}", response.getStatus(), response.body());
                return "";
            }

            JSONObject result = JSONUtil.parseObj(response.body());
            JSONArray choices = result.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                log.warn("MiMoVisionService: no choices in response");
                return "";
            }

            JSONObject firstChoice = choices.getJSONObject(0);
            JSONObject message = firstChoice.getJSONObject("message");
            if (message == null) {
                log.warn("MiMoVisionService: no message in first choice");
                return "";
            }

            String content = message.getStr("content");
            log.info("MiMoVisionService: understanding result length={}", content != null ? content.length() : 0);
            return content != null ? content : "";

        } catch (Exception e) {
            log.error("MiMoVisionService: API call failed", e);
            return "";
        }
    }

    /**
     * 拼接图片理解结果到用户消息中
     *
     * @param originalMessage 用户原始消息
     * @param understanding   图片理解结果
     * @return 拼接后的完整消息
     */
    public String buildVisionEnhancedMessage(String originalMessage, String understanding) {
        if (StrUtil.isBlank(understanding)) {
            return originalMessage;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[图片理解结果]\n");
        sb.append(understanding);
        sb.append("\n\n[用户问题]\n");
        sb.append(originalMessage);
        return sb.toString();
    }
}
