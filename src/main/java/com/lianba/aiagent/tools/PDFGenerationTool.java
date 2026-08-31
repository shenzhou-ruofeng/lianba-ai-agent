package com.lianba.aiagent.tools;

import cn.hutool.core.io.FileUtil;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.lianba.aiagent.constant.FileConstant;
import com.lianba.aiagent.manager.OssManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.File;
import java.io.IOException;

/**
 * PDF 生成工具
 * 生成后同时保存到本地存储和对象存储，返回可访问的文件 URL；
 * 标注 returnDirect = true，工具结果直接返回给用户，避免额外调用一次 AI 模型
 */
@Slf4j
public class PDFGenerationTool {

    /**
     * 对象存储管理器（可为 null，为 null 或未配置时仅保存到本地）
     */
    private final OssManager ossManager;

    public PDFGenerationTool() {
        this(null);
    }

    public PDFGenerationTool(OssManager ossManager) {
        this.ossManager = ossManager;
    }

    @Tool(description = "Generate a PDF file with given content. To embed images, first use scrapeWebPageImages to get image URLs, then pass them as comma-separated imageUrls. The result contains accessible download URLs and is returned to the user directly.", returnDirect = true)
    public String generatePDF(
            @ToolParam(description = "Name of the file to save the generated PDF") String fileName,
            @ToolParam(description = "Content to be included in the PDF") String content,
            @ToolParam(description = "Comma-separated URLs of images to embed in the PDF", required = false) String imageUrls) {
        String fileDir = FileConstant.FILE_SAVE_DIR + "/pdf";
        String filePath = fileDir + "/" + fileName;
        try {
            // 创建目录
            FileUtil.mkdir(fileDir);
            // 创建 PdfWriter 和 PdfDocument 对象
            try (PdfWriter writer = new PdfWriter(filePath);
                 PdfDocument pdf = new PdfDocument(writer);
                 Document document = new Document(pdf)) {
                PdfFont font;
                try {
                    // 使用内置 CJK 中文字体（需要 font-asian 依赖在运行时可用）
                    font = PdfFontFactory.createFont("STSongStd-Light", "UniGB-UCS2-H");
                    log.info("Using built-in CJK font STSongStd-Light for PDF generation");
                } catch (Exception fontEx) {
                    log.warn("CJK font not available, falling back to default font (Chinese characters may not render): {}", fontEx.getMessage());
                    font = PdfFontFactory.createFont();
                }
                document.setFont(font);
                // 添加文本内容
                Paragraph paragraph = new Paragraph(content);
                document.add(paragraph);
                // 添加图片（如果提供）
                if (imageUrls != null && !imageUrls.isBlank()) {
                    String[] urls = imageUrls.split(",");
                    float pageWidth = PageSize.A4.getWidth() - document.getLeftMargin() - document.getRightMargin();
                    for (String imageUrl : urls) {
                        String trimmedUrl = imageUrl.trim();
                        if (trimmedUrl.isEmpty()) continue;
                        try {
                            log.info("Downloading image for PDF: {}", trimmedUrl);
                            ImageData imageData = ImageDataFactory.create(trimmedUrl);
                            Image image = new Image(imageData);
                            // 缩放图片以适应页面宽度
                            if (image.getImageWidth() > pageWidth) {
                                image.setWidth(pageWidth * 0.9f);
                            }
                            document.add(image);
                            log.info("Image added to PDF: {}", trimmedUrl);
                        } catch (Exception imgEx) {
                            log.warn("Failed to embed image from URL {}: {}", trimmedUrl, imgEx.getMessage());
                            document.add(new Paragraph("[Image not available: " + trimmedUrl + "]").setFont(font));
                        }
                    }
                }
            }
            log.info("PDF generated successfully: {}", filePath);
            // 同步上传到对象存储（未配置或失败时降级为仅本地存储）
            String ossUrl = null;
            if (ossManager != null) {
                ossUrl = ossManager.uploadFile("pdf/" + fileName, new File(filePath));
            }
            // 返回可访问的文件 URL：优先仅返回 OSS 公网地址，上传失败时回退本地下载地址
            String downloadUrl = ossUrl != null ? ossUrl : "/api/files/download/pdf/" + fileName;
            return "PDF generated successfully! [Download: " + downloadUrl + "]";
        } catch (IOException e) {
            log.error("Error generating PDF to {}: {}", filePath, e.getMessage());
            return "Error generating PDF: " + e.getMessage();
        }
    }
}
