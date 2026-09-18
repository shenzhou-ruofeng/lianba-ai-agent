package com.lianba.aiagent.service;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.lianba.aiagent.config.DeepSeekProperties;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

/**
 * DeepSeek 对话服务（OpenAI 兼容 API，base URL: https://api.deepseek.com/v1）
 * 1. 多模态图片理解：使用 DeepSeek-Flash 模型直接处理用户的图文请求（替代原 MIMO 视觉服务）
 * 2. 深度思考推理：使用 deepseek-flash 模型开启思考模式（thinking.type=enabled），流式输出推理过程（reasoning_content）
 */
@Service
@Slf4j
public class DeepSeekChatService {

    @Resource
    private DeepSeekProperties deepSeekProperties;

    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    /**
     * 图文对话：将用户文本与图片一并交给 DeepSeek-Flash 模型理解并回答
     *
     * @param userText  用户文本
     * @param imageUrls 图片 URL 列表（本地地址自动读取字节转 base64 data URL）
     * @return 模型回答文本，失败返回空字符串
     */
    public String chatWithImages(String userText, List<String> imageUrls) {
        JSONArray userContent = new JSONArray();
        JSONObject textPart = new JSONObject();
        textPart.set("type", "text");
        textPart.set("text", userText);
        userContent.add(textPart);
        appendImageParts(userContent, imageUrls);
        return callChatCompletions(deepSeekProperties.getModel(), userContent, 2000, false);
    }

    /**
     * 对多张图片进行视觉理解（替代 MiMoVisionService.understandImages）
     *
     * @param imageUrls 图片 URL 列表
     * @param prompt    可选的引导提示，为空则使用默认提示
     * @return 图片理解结果的合并文本，失败返回空字符串
     */
    public String understandImages(List<String> imageUrls, String prompt) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            log.warn("DeepSeekChatService: imageUrls is empty");
            return "";
        }
        String textPrompt = StrUtil.isNotBlank(prompt)
                ? prompt
                : "请详细描述这些图片中的内容，包括场景、人物、物体、文字、颜色、氛围等所有细节。";
        JSONArray userContent = new JSONArray();
        JSONObject textPart = new JSONObject();
        textPart.set("type", "text");
        textPart.set("text", textPrompt);
        userContent.add(textPart);
        appendImageParts(userContent, imageUrls);
        return callChatCompletions(deepSeekProperties.getModel(), userContent, 1000, false);
    }

    /**
     * 对图片字节进行视觉理解（base64 data URL 直传）
     *
     * @param imageBytes  图片字节数组
     * @param contentType 图片 MIME 类型（如 image/png），为空则默认 image/png
     * @param prompt      可选的引导提示
     * @return 图片理解结果的文本描述
     */
    public String understandImageBytes(byte[] imageBytes, String contentType, String prompt) {
        if (imageBytes == null || imageBytes.length == 0) {
            log.warn("DeepSeekChatService: imageBytes is empty");
            return "";
        }
        String mimeType = StrUtil.isNotBlank(contentType) ? contentType : "image/png";
        String dataUrl = "data:" + mimeType + ";base64," + Base64.encode(imageBytes);
        return understandImages(List.of(dataUrl), prompt);
    }

    /**
     * 深度思考：调用 deepseek-flash 模型并开启思考模式（thinking.type=enabled + reasoning_effort=high），
     * 流式推送推理过程（reasoning_content）。推理阶段不影响主对话链路，失败时静默降级（仅记录日志）。
     *
     * @param userText      用户消息（含上下文增强内容）
     * @param thinkingSink  推理内容分片消费者（每个分片到达时回调，用于 SSE 推送）
     * @return 推理过程完整文本，失败返回空字符串
     */
    public String streamDeepThink(String userText, Consumer<String> thinkingSink) {
        JSONArray messages = new JSONArray();
        JSONObject userMessage = new JSONObject();
        userMessage.set("role", "user");
        userMessage.set("content", userText);
        messages.add(userMessage);

        // 思考模式开关（OpenAI 兼容格式）：{"thinking": {"type": "enabled"}}
        JSONObject thinking = new JSONObject();
        thinking.set("type", "enabled");

        JSONObject requestBody = new JSONObject();
        requestBody.set("model", deepSeekProperties.getModel());
        requestBody.set("messages", messages);
        requestBody.set("stream", true);
        requestBody.set("max_tokens", 4000);
        requestBody.set("thinking", thinking);
        requestBody.set("reasoning_effort", "high");

        String url = deepSeekProperties.getBaseUrl() + CHAT_COMPLETIONS_PATH;
        StringBuilder reasoningBuilder = new StringBuilder();
        log.info("DeepSeekChatService: start deep thinking, model={}", deepSeekProperties.getModel());

        try (HttpResponse response = HttpRequest.post(url)
                .header("Authorization", "Bearer " + deepSeekProperties.getApiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .body(requestBody.toString())
                .timeout(120000)
                .execute()) {

            if (response.getStatus() != 200) {
                log.error("DeepSeekChatService: reasoner API returned status={}, body={}", response.getStatus(), response.body());
                return "";
            }

            // 逐行读取 SSE 流，解析 reasoning_content 增量分片
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.bodyStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.startsWith("data:")) {
                        continue;
                    }
                    String data = trimmed.substring(5).trim();
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    try {
                        JSONObject chunk = JSONUtil.parseObj(data);
                        JSONArray choices = chunk.getJSONArray("choices");
                        if (choices == null || choices.isEmpty()) {
                            continue;
                        }
                        JSONObject delta = choices.getJSONObject(0).getJSONObject("delta");
                        if (delta == null) {
                            continue;
                        }
                        String reasoning = delta.getStr("reasoning_content");
                        if (StrUtil.isNotEmpty(reasoning)) {
                            reasoningBuilder.append(reasoning);
                            thinkingSink.accept(reasoning);
                        }
                    } catch (Exception parseEx) {
                        log.debug("DeepSeekChatService: skip unparsable chunk: {}", data);
                    }
                }
            }
            log.info("DeepSeekChatService: deep thinking finished, reasoning length={}", reasoningBuilder.length());
            return reasoningBuilder.toString();
        } catch (Exception e) {
            log.error("DeepSeekChatService: deep thinking failed", e);
            return reasoningBuilder.toString();
        }
    }

    /**
     * 拼接图片理解结果到用户消息中（替代 MiMoVisionService.buildVisionEnhancedMessage）
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

    /**
     * 将图片 URL 列表追加为 OpenAI 兼容格式的 image_url 内容块。
     * http/https 远程地址直接传入；本地地址（如 /api/files/download/image/xxx）
     * 外网不可达，读取本地文件字节转 base64 data URL 直传。
     */
    private void appendImageParts(JSONArray userContent, List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return;
        }
        for (String imageUrl : imageUrls) {
            String finalUrl = imageUrl;
            if (!imageUrl.startsWith("http://") && !imageUrl.startsWith("https://") && !imageUrl.startsWith("data:")) {
                String dataUrl = readLocalImageAsDataUrl(imageUrl);
                if (dataUrl == null) {
                    log.warn("DeepSeekChatService: cannot resolve image url, skip: {}", imageUrl);
                    continue;
                }
                finalUrl = dataUrl;
            }
            JSONObject imagePart = new JSONObject();
            imagePart.set("type", "image_url");
            JSONObject imageUrlObj = new JSONObject();
            imageUrlObj.set("url", finalUrl);
            imagePart.set("image_url", imageUrlObj);
            userContent.add(imagePart);
        }
    }

    /**
     * 读取本地图片文件（相对 /api/files/download/image/ 或 tmp/file 存储路径）转 base64 data URL
     */
    private String readLocalImageAsDataUrl(String imageUrl) {
        try {
            // 提取存储相对路径：/api/files/download/image/2026-09-18/xxx.png -> 2026-09-18/xxx.png
            String relativePath = imageUrl;
            int idx = relativePath.indexOf("/files/download/image/");
            if (idx >= 0) {
                relativePath = relativePath.substring(idx + "/files/download/image/".length());
            } else if (relativePath.startsWith("/")) {
                relativePath = relativePath.substring(1);
            }
            java.nio.file.Path path = java.nio.file.Path.of(com.lianba.aiagent.constant.FileConstant.FILE_SAVE_DIR, "image", relativePath);
            if (!java.nio.file.Files.exists(path)) {
                return null;
            }
            byte[] bytes = java.nio.file.Files.readAllBytes(path);
            String mimeType = relativePath.toLowerCase().endsWith(".png") ? "image/png"
                    : relativePath.toLowerCase().endsWith(".gif") ? "image/gif"
                    : relativePath.toLowerCase().endsWith(".webp") ? "image/webp"
                    : "image/jpeg";
            return "data:" + mimeType + ";base64," + Base64.encode(bytes);
        } catch (IOException e) {
            log.warn("DeepSeekChatService: read local image failed: {}", imageUrl, e);
            return null;
        }
    }

    /**
     * 调用 OpenAI 兼容 chat/completions 接口（非流式）
     *
     * @param model       模型名
     * @param userContent 用户消息内容（文本 + 图片块）
     * @param maxTokens   最大生成 token 数
     * @param stream      是否流式（当前仅使用非流式）
     * @return 模型回答文本，失败返回空字符串
     */
    private String callChatCompletions(String model, JSONArray userContent, int maxTokens, boolean stream) {
        if (StrUtil.isBlank(deepSeekProperties.getApiKey())) {
            log.warn("DeepSeekChatService: apiKey is blank, skip call");
            return "";
        }

        JSONArray messages = new JSONArray();
        JSONObject userMessage = new JSONObject();
        userMessage.set("role", "user");
        userMessage.set("content", userContent);
        messages.add(userMessage);

        JSONObject requestBody = new JSONObject();
        requestBody.set("model", model);
        requestBody.set("messages", messages);
        requestBody.set("max_tokens", maxTokens);
        requestBody.set("stream", stream);
        // 图片理解/图文对话为直接任务，显式关闭思考模式以降低延迟与成本
        JSONObject thinking = new JSONObject();
        thinking.set("type", "disabled");
        requestBody.set("thinking", thinking);

        String url = deepSeekProperties.getBaseUrl() + CHAT_COMPLETIONS_PATH;
        log.info("DeepSeekChatService: calling DeepSeek API, model={}", model);

        try (HttpResponse response = HttpRequest.post(url)
                .header("Authorization", "Bearer " + deepSeekProperties.getApiKey())
                .header("Content-Type", "application/json")
                .body(requestBody.toString())
                .timeout(60000)
                .execute()) {

            if (response.getStatus() != 200) {
                log.error("DeepSeekChatService: API returned status={}, body={}", response.getStatus(), response.body());
                return "";
            }

            JSONObject result = JSONUtil.parseObj(response.body());
            JSONArray choices = result.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                log.warn("DeepSeekChatService: no choices in response");
                return "";
            }
            JSONObject message = choices.getJSONObject(0).getJSONObject("message");
            if (message == null) {
                log.warn("DeepSeekChatService: no message in first choice");
                return "";
            }
            String content = message.getStr("content");
            log.info("DeepSeekChatService: response length={}", content != null ? content.length() : 0);
            return content != null ? content : "";
        } catch (Exception e) {
            log.error("DeepSeekChatService: API call failed", e);
            return "";
        }
    }
}
