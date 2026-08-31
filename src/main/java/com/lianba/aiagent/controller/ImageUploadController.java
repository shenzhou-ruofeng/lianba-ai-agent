package com.lianba.aiagent.controller;

import cn.hutool.core.util.StrUtil;
import com.lianba.aiagent.constant.FileConstant;
import com.lianba.aiagent.service.MiMoVisionService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 图片上传控制器
 * 支持多图片上传，返回可访问的图片 URL 列表
 */
@Slf4j
@RestController
@RequestMapping("/images")
public class ImageUploadController {

    @Resource
    private MiMoVisionService miMoVisionService;

    /** 最大文件大小：10MB */
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    /** 允许的图片格式 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");

    /** 允许的 MIME 类型 */
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );

    /**
     * 上传图片
     *
     * @param files   图片文件（支持多文件）
     * @param request HTTP 请求（用于构建完整 URL）
     * @return 上传结果，包含每张图片的访问 URL
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadImages(
            @RequestParam("files") List<MultipartFile> files,
            HttpServletRequest request) {

        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> fileResults = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;

        for (MultipartFile file : files) {
            Map<String, Object> fileResult = new HashMap<>();
            fileResult.put("originalName", file.getOriginalFilename());

            // 1. 校验文件非空
            if (file.isEmpty()) {
                fileResult.put("success", false);
                fileResult.put("message", "文件为空");
                fileResults.add(fileResult);
                failCount++;
                continue;
            }

            // 2. 校验文件大小
            if (file.getSize() > MAX_FILE_SIZE) {
                fileResult.put("success", false);
                fileResult.put("message", "文件过大，最大允许 10MB");
                fileResults.add(fileResult);
                failCount++;
                continue;
            }

            // 3. 校验文件扩展名
            String originalFilename = file.getOriginalFilename();
            String extension = getFileExtension(originalFilename).toLowerCase();
            if (!ALLOWED_EXTENSIONS.contains(extension)) {
                fileResult.put("success", false);
                fileResult.put("message", "不支持的图片格式，仅支持 jpg/png/gif/webp");
                fileResults.add(fileResult);
                failCount++;
                continue;
            }

            // 4. 校验 MIME 类型
            String contentType = file.getContentType();
            if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType)) {
                fileResult.put("success", false);
                fileResult.put("message", "不支持的 MIME 类型: " + contentType);
                fileResults.add(fileResult);
                failCount++;
                continue;
            }

            // 5. 保存文件
            try {
                String savedFileName = saveImage(file, extension);
                String imageUrl = buildImageUrl(request, savedFileName);

                fileResult.put("success", true);
                fileResult.put("fileName", savedFileName);
                fileResult.put("url", imageUrl);
                successCount++;
                log.info("Image uploaded successfully: {} -> {}", originalFilename, savedFileName);
            } catch (IOException e) {
                log.error("Failed to save image: {}", originalFilename, e);
                fileResult.put("success", false);
                fileResult.put("message", "文件保存失败: " + e.getMessage());
                failCount++;
            }

            fileResults.add(fileResult);
        }

        result.put("success", failCount == 0);
        result.put("successCount", successCount);
        result.put("failCount", failCount);
        result.put("fileResults", fileResults);
        result.put("message", String.format("上传完成：成功 %d 个，失败 %d 个", successCount, failCount));

        return ResponseEntity.ok(result);
    }

    /**
     * 解析图片：上传保存 + MIMO 视觉理解，一次完成。
     * 前端在用户选择图片后立即调用，解析结果随后随提示词一起发送给智能体。
     * 通过字节 base64 直传 MIMO，避免本地图片 URL 外网不可达导致 AI 获取不到图片。
     *
     * @param file    单张图片文件
     * @param request HTTP 请求（用于构建完整 URL）
     * @return 解析结果，包含图片访问 URL 和视觉理解文本
     */
    @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> parseImage(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {

        Map<String, Object> result = new HashMap<>();

        // 1. 基础校验（与上传接口一致）
        if (file.isEmpty()) {
            result.put("success", false);
            result.put("message", "文件为空");
            return ResponseEntity.ok(result);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            result.put("success", false);
            result.put("message", "文件过大，最大允许 10MB");
            return ResponseEntity.ok(result);
        }
        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            result.put("success", false);
            result.put("message", "不支持的图片格式，仅支持 jpg/png/gif/webp");
            return ResponseEntity.ok(result);
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType)) {
            result.put("success", false);
            result.put("message", "不支持的 MIME 类型: " + contentType);
            return ResponseEntity.ok(result);
        }

        // 2. 保存文件 + 视觉理解
        try {
            byte[] imageBytes = file.getBytes();
            String savedFileName = saveImage(file, extension);
            String imageUrl = buildImageUrl(request, savedFileName);

            // 字节直传 MIMO 进行视觉理解
            String understanding = miMoVisionService.understandImageBytes(imageBytes, contentType, null);

            result.put("success", true);
            result.put("fileName", savedFileName);
            result.put("url", imageUrl);
            result.put("understanding", understanding);
            if (StrUtil.isBlank(understanding)) {
                result.put("message", "图片已保存，但视觉理解未返回结果");
            }
            log.info("Image parsed successfully: {} -> {}, understanding length={}",
                    originalFilename, savedFileName, understanding != null ? understanding.length() : 0);
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            log.error("Failed to parse image: {}", originalFilename, e);
            result.put("success", false);
            result.put("message", "图片解析失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 保存图片到本地磁盘
     */
    private String saveImage(MultipartFile file, String extension) throws IOException {
        // 按日期分目录存储
        String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        Path saveDir = Path.of(FileConstant.FILE_SAVE_DIR, "image", dateDir);
        Files.createDirectories(saveDir);

        // 生成唯一文件名
        String uniqueName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path targetPath = saveDir.resolve(uniqueName);

        // 保存文件
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        return dateDir + "/" + uniqueName;
    }

    /**
     * 构建可访问的图片 URL
     */
    private String buildImageUrl(HttpServletRequest request, String savedFileName) {
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();
        String contextPath = request.getContextPath();

        StringBuilder url = new StringBuilder();
        url.append(scheme).append("://").append(serverName);
        // 非标准端口才追加端口号
        if (!((scheme.equals("http") && serverPort == 80) || (scheme.equals("https") && serverPort == 443))) {
            url.append(":").append(serverPort);
        }
        url.append(contextPath);
        url.append("/files/download/image/").append(savedFileName);

        return url.toString();
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        if (StrUtil.isBlank(filename) || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }
}
