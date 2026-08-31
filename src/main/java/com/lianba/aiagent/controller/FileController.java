package com.lianba.aiagent.controller;

import com.lianba.aiagent.constant.FileConstant;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * 文件下载控制器
 */
@Slf4j
@RestController
@RequestMapping("/files")
public class FileController {

    /**
     * 下载生成的 PDF 文件
     *
     * @param fileName PDF 文件名
     * @return 文件资源
     */
    @GetMapping("/download/pdf/{fileName}")
    public ResponseEntity<Resource> downloadPdf(@PathVariable String fileName) {
        // 剥离 AI 模型可能在 URL 后拼接的引号/标点等字符，防止 Path.of 抛 InvalidPathException
        String safeFileName = sanitizePathComponent(stripTrailingPunctuation(fileName));
        if (safeFileName.isBlank()) {
            return ResponseEntity.notFound().build();
        }
        // 构造文件路径，防止路径穿越攻击
        safeFileName = Path.of(safeFileName).getFileName().toString();
        String filePath = FileConstant.FILE_SAVE_DIR + "/pdf/" + safeFileName;
        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            log.warn("PDF file not found: {}", filePath);
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(file);
        log.info("Serving PDF download: {}", safeFileName);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safeFileName + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(resource);
    }

    /**
     * 下载生成的通用文件（如 writeFile 工具生成的文件）
     *
     * @param fileName 文件名
     * @return 文件资源
     */
    @GetMapping("/download/file/{fileName}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileName) {
        // 剥离 AI 模型可能在 URL 后拼接的引号/标点等字符，防止 Path.of 抛 InvalidPathException
        String safeFileName = sanitizePathComponent(stripTrailingPunctuation(fileName));
        if (safeFileName.isBlank()) {
            return ResponseEntity.notFound().build();
        }
        // 构造文件路径，防止路径穿越攻击
        safeFileName = Path.of(safeFileName).getFileName().toString();
        String filePath = FileConstant.FILE_SAVE_DIR + "/file/" + safeFileName;
        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            log.warn("File not found: {}", filePath);
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(file);
        log.info("Serving file download: {}", safeFileName);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safeFileName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    /**
     * 获取已上传的图片（支持内联显示）
     * 路径格式：/api/files/download/image/{dateDir}/{fileName:.+}
     * 例如：/api/files/download/image/2026-07-24/abc123.jpg
     * 使用 :.+ 确保带扩展名的文件名（如 .png）能被 @PathVariable 正确捕获
     *
     * @param dateDir  日期子目录
     * @param fileName 图片文件名（含扩展名）
     * @param download 是否强制下载（true 则设置 Content-Disposition: attachment）
     * @return 图片资源
     */
    @GetMapping("/download/image/{dateDir}/{fileName:.+}")
    public ResponseEntity<Resource> getImage(
            @PathVariable String dateDir,
            @PathVariable String fileName,
            @RequestParam(required = false, defaultValue = "false") boolean download) {
        // 防止路径穿越和空值
        if (dateDir == null || dateDir.isBlank() || fileName == null || fileName.isBlank()) {
            log.warn("Invalid image request: dateDir={}, fileName={}", dateDir, fileName);
            return ResponseEntity.notFound().build();
        }
        // 剥离文件名中的引号、反引号、尾随标点等非法字符（AI 模型可能在 URL 后拼接这些字符）
        String safeFileName = sanitizePathComponent(stripTrailingPunctuation(fileName));
        String safeDateDir = sanitizePathComponent(dateDir);
        String filePath = FileConstant.FILE_SAVE_DIR + "/image/" + safeDateDir + "/" + safeFileName;
        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            log.warn("Image file not found: {}", filePath);
            return ResponseEntity.notFound().build();
        }

        // 根据文件扩展名确定 MIME 类型
        MediaType mediaType = determineImageMediaType(safeFileName);

        Resource resource = new FileSystemResource(file);
        log.debug("Serving image: {}, download={}", safeFileName, download);

        try {
            ResponseEntity.BodyBuilder builder = ResponseEntity.ok().contentType(mediaType);
            if (download) {
                // 强制下载：解决跨域下 <a download> 属性被浏览器忽略的问题
                String encodedName = URLEncoder.encode(safeFileName, StandardCharsets.UTF_8)
                        .replace("+", "%20");
                builder.header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedName);
            }
            return builder.body(resource);
        } catch (Exception e) {
            log.error("Failed to serve image: {}", filePath, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 剥离路径组件中的非法字符（引号、反引号、尖括号等），防止 Windows 路径异常。
     * 同时对 AI 模型可能在 URL 后拼接的引号做防御性清除。
     */
    private String sanitizePathComponent(String component) {
        if (component == null) return "";
        return component
                .replace("\"", "")   // 双引号
                .replace("'", "")    // 单引号
                .replace("`", "")    // 反引号（Markdown 代码标记）
                .replace("<", "")
                .replace(">", "")
                .replace("|", "")
                .replace("?", "")
                .replace("*", "")
                .replace(":", "")    // 冒号（Windows 路径盘符分隔符，URL 中拼接会触发 InvalidPathException）
                .trim();
    }

    /**
     * 剥离路径组件尾部的标点符号（AI 模型可能在 URL 后拼接中文/英文标点或括号）。
     */
    private String stripTrailingPunctuation(String component) {
        if (component == null) return "";
        String result = component;
        while (!result.isEmpty()) {
            char last = result.charAt(result.length() - 1);
            if (last == ')' || last == ']' || last == '}' || last == ',' || last == ';'
                    || last == '\uFF09' || last == '\uFF1B' || last == '\uFF1A' || last == '\u3001' || last == '\u3002') {
                result = result.substring(0, result.length() - 1);
            } else {
                break;
            }
        }
        return result;
    }

    /**
     * 根据文件名确定图片 MIME 类型
     */
    private MediaType determineImageMediaType(String fileName) {
        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        } else if (lowerName.endsWith(".gif")) {
            return MediaType.IMAGE_GIF;
        } else if (lowerName.endsWith(".webp")) {
            return MediaType.valueOf("image/webp");
        }
        // 默认 JPEG
        return MediaType.IMAGE_JPEG;
    }
}
