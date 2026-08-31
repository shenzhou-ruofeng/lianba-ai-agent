package com.lianba.aiagent.service;

import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.List;
import com.itextpdf.layout.element.ListItem;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.ListNumberingType;
import com.itextpdf.layout.properties.TextAlignment;
import com.lianba.aiagent.app.model.LoveReport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 恋爱报告导出服务：将结构化报告 {title, suggestions} 导出为 PDF / Word / Markdown 字节流
 * 全部在内存中生成，不落盘，直接供 Controller 以附件形式返回
 */
@Slf4j
@Service
public class LoveReportExportService {

    /**
     * 通用会话记录导出为 PDF：标题 + 按序渲染的对话消息（角色标注）
     */
    public byte[] exportChatToPdf(String title, java.util.List<ChatMessage> messages) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PdfWriter writer = new PdfWriter(out);
             PdfDocument pdf = new PdfDocument(writer);
             Document document = new Document(pdf)) {
            PdfFont font;
            try {
                font = PdfFontFactory.createFont("STSongStd-Light", "UniGB-UCS2-H");
            } catch (Exception fontEx) {
                log.warn("CJK font not available, falling back to default font: {}", fontEx.getMessage());
                font = PdfFontFactory.createFont();
            }
            document.setFont(font);
            document.add(new Paragraph(safeChatTitle(title))
                    .setFontSize(20)
                    .simulateBold()
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(8));
            document.add(new Paragraph("导出时间：" + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date()))
                    .setFontSize(10)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(16));
            int index = 1;
            for (ChatMessage msg : safeMessages(messages)) {
                String role = "user".equalsIgnoreCase(msg.role()) ? "我" : "AI";
                Paragraph p = new Paragraph()
                        .setFontSize(12)
                        .setMarginBottom(10);
                p.add(new com.itextpdf.layout.element.Text("[" + index++ + "] " + role + "：")
                        .simulateBold());
                p.add(new com.itextpdf.layout.element.Text(msg.content() == null ? "" : msg.content()));
                document.add(p);
            }
        }
        return out.toByteArray();
    }

    /**
     * 通用会话记录导出为 Word（Word 兼容 HTML）
     */
    public byte[] exportChatToWord(String title, java.util.List<ChatMessage> messages) {
        StringBuilder html = new StringBuilder();
        html.append("<html xmlns:o=\"urn:schemas-microsoft-com:office:office\" ")
                .append("xmlns:w=\"urn:schemas-microsoft-com:office:word\">")
                .append("<head><meta charset=\"UTF-8\">")
                .append("<title>").append(escapeHtml(safeChatTitle(title))).append("</title>")
                .append("<style>body{font-family:'Microsoft YaHei',SimSun,serif;}")
                .append("h1{text-align:center;font-size:22pt;}")
                .append(".msg{margin-bottom:12pt;}")
                .append(".role{font-weight:bold;font-size:12pt;margin-bottom:2pt;}")
                .append(".user{color:#B03A5B;}")
                .append(".ai{color:#2F7E7A;}")
                .append(".content{font-size:12pt;line-height:1.8;white-space:pre-wrap;}</style>")
                .append("</head><body>");
        html.append("<h1>").append(escapeHtml(safeChatTitle(title))).append("</h1>");
        html.append("<p style=\"text-align:center;color:#888;font-size:10pt;\">导出时间：")
                .append(escapeHtml(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date())))
                .append("</p>");
        int index = 1;
        for (ChatMessage msg : safeMessages(messages)) {
            boolean isUser = "user".equalsIgnoreCase(msg.role());
            html.append("<div class=\"msg\">")
                    .append("<div class=\"role ")
                    .append(isUser ? "user" : "ai")
                    .append("\">[").append(index++).append("] ")
                    .append(isUser ? "我" : "AI")
                    .append("</div>")
                    .append("<div class=\"content\">")
                    .append(escapeHtml(msg.content() == null ? "" : msg.content()).replace("\n", "<br/>"))
                    .append("</div></div>");
        }
        html.append("</body></html>");
        return html.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 通用会话记录导出为 Markdown
     */
    public byte[] exportChatToMarkdown(String title, java.util.List<ChatMessage> messages) {
        StringBuilder md = new StringBuilder();
        md.append("# ").append(safeChatTitle(title)).append("\n\n");
        md.append("> 导出时间：")
                .append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date()))
                .append("\n\n---\n\n");
        int index = 1;
        for (ChatMessage msg : safeMessages(messages)) {
            boolean isUser = "user".equalsIgnoreCase(msg.role());
            md.append("### [").append(index++).append("] ").append(isUser ? "我" : "AI").append("\n\n");
            md.append(msg.content() == null ? "" : msg.content()).append("\n\n---\n\n");
        }
        return md.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String safeChatTitle(String title) {
        return (title == null || title.isBlank()) ? "对话记录" : title;
    }

    private java.util.List<ChatMessage> safeMessages(java.util.List<ChatMessage> messages) {
        return messages == null ? java.util.List.of() : messages;
    }

    /**
     * 对话消息记录（角色 + 内容）
     */
    public record ChatMessage(String role, String content) {
    }

    /**
     * 导出为 PDF（复用 PDFGenerationTool 的内置 CJK 中文字体方案）
     */
    public byte[] exportToPdf(LoveReport report) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PdfWriter writer = new PdfWriter(out);
             PdfDocument pdf = new PdfDocument(writer);
             Document document = new Document(pdf)) {
            PdfFont font;
            try {
                // 使用内置 CJK 中文字体（需要 font-asian 依赖在运行时可用）
                font = PdfFontFactory.createFont("STSongStd-Light", "UniGB-UCS2-H");
            } catch (Exception fontEx) {
                log.warn("CJK font not available, falling back to default font: {}", fontEx.getMessage());
                font = PdfFontFactory.createFont();
            }
            document.setFont(font);
            // 标题
            document.add(new Paragraph(safeTitle(report))
                    .setFontSize(20)
                    .simulateBold()
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(16));
            // 编号建议列表
            List list = new List(ListNumberingType.DECIMAL)
                    .setFontSize(12)
                    .setSymbolIndent(8);
            for (String suggestion : safeSuggestions(report)) {
                list.add(new ListItem(suggestion));
            }
            document.add(list);
        }
        return out.toByteArray();
    }

    /**
     * 导出为 Word（Word 兼容 HTML，保存为 .doc，MS Word / WPS 可直接打开，无需引入 POI 依赖）
     */
    public byte[] exportToWord(LoveReport report) {
        StringBuilder html = new StringBuilder();
        html.append("<html xmlns:o=\"urn:schemas-microsoft-com:office:office\" ")
                .append("xmlns:w=\"urn:schemas-microsoft-com:office:word\">")
                .append("<head><meta charset=\"UTF-8\">")
                .append("<title>").append(escapeHtml(safeTitle(report))).append("</title>")
                .append("<style>body{font-family:'Microsoft YaHei',SimSun,serif;}")
                .append("h1{text-align:center;font-size:22pt;}")
                .append("li{font-size:12pt;line-height:1.8;margin-bottom:8pt;}</style>")
                .append("</head><body>");
        html.append("<h1>").append(escapeHtml(safeTitle(report))).append("</h1>");
        html.append("<ol>");
        for (String suggestion : safeSuggestions(report)) {
            html.append("<li>").append(escapeHtml(suggestion)).append("</li>");
        }
        html.append("</ol></body></html>");
        return html.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 导出为 Markdown
     */
    public byte[] exportToMarkdown(LoveReport report) {
        StringBuilder md = new StringBuilder();
        md.append("# ").append(safeTitle(report)).append("\n\n");
        int index = 1;
        for (String suggestion : safeSuggestions(report)) {
            md.append(index++).append(". ").append(suggestion).append("\n");
        }
        return md.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String safeTitle(LoveReport report) {
        return (report == null || report.title() == null || report.title().isBlank())
                ? "恋爱报告" : report.title();
    }

    private java.util.List<String> safeSuggestions(LoveReport report) {
        return (report == null || report.suggestions() == null)
                ? java.util.List.of() : report.suggestions();
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
