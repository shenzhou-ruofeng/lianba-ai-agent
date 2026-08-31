package com.yupi.yuaiagent.tools;

import com.yupi.yuaiagent.service.WanImageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 图片生成工具
 * AI Agent 可调用此工具来生成图片，内部委托给 WanImageService（wan2.7-image，含熔断/降级/限流）
 */
@Slf4j
public class ImageGenerationTool {

    private final WanImageService wanImageService;

    public ImageGenerationTool(WanImageService wanImageService) {
        this.wanImageService = wanImageService;
    }

    @Tool(description = "Generate an image based on the given description/prompt. Use this tool when the user asks to create, draw, or generate an image, picture, illustration, or any visual content. If the user uploaded an image and wants to modify/redraw it, pass its download URL as referenceImageUrl to do image-to-image generation.")
    public String generateImage(
            @ToolParam(description = "A detailed description of the image to generate, in Chinese or English") String prompt,
            @ToolParam(description = "Image size tier: 1K or 2K. Default is 1K (1024x1024)", required = false) String size,
            @ToolParam(description = "Optional reference image URL for image-to-image generation. Use the uploaded image's download URL (e.g. /api/files/download/image/...) when the user wants to modify an uploaded image", required = false) String referenceImageUrl) {
        log.info("ImageGenerationTool: generating image, prompt={}, size={}, referenceImageUrl={}", prompt, size, referenceImageUrl);
        return wanImageService.generateImage(prompt, size, referenceImageUrl);
    }
}
