package com.lianba.aiagent.controller;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.itextpdf.kernel.pdf.canvas.parser.listener.LocationTextExtractionStrategy;
import com.lianba.aiagent.rag.MyKeywordEnricher;
import com.lianba.aiagent.rag.MyTokenTextSplitter;
import com.lianba.aiagent.rag.hybrid.FullTextSearchIndex;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/rag")
@Slf4j
public class RagImportController {

    @Resource
    @Qualifier("loveAppVectorStore")
    private VectorStore loveAppVectorStore;

    @Resource
    private MyTokenTextSplitter myTokenTextSplitter;

    @Resource
    private MyKeywordEnricher myKeywordEnricher;

    @Resource
    private FullTextSearchIndex loveAppFullTextIndex;

    /**
     * 已解析待入库的文档缓存：parseId -> 解析结果
     * 前端选择文档后先解析缓存，用户发送提示词后再决定是否确认入库
     */
    private final Map<String, ParsedDocument> parsedDocumentCache = new ConcurrentHashMap<>();

    /** 解析结果缓存过期时间：30 分钟 */
    private static final long PARSE_CACHE_TTL_MS = 30 * 60 * 1000;

    /**
     * 解析结果缓存条目
     */
    private record ParsedDocument(String filename, List<Document> documents, long parsedAt) {
    }

    /**
     * 解析文档（不入库）：提取文本 → 切分 → 关键词补充，解析结果缓存待确认。
     * 沿用原有文档解析逻辑，仅把最后的向量库写入延迟到 /rag/confirm。
     */
    @PostMapping("/parse")
    public ResponseEntity<Map<String, Object>> parseDocument(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        evictExpiredParsedDocuments();
        try {
            String filename = file.getOriginalFilename();
            if (filename == null || filename.isBlank()) {
                result.put("success", false);
                result.put("message", "文件名不能为空");
                return ResponseEntity.ok(result);
            }

            String content = extractText(file, filename.toLowerCase());
            if (content == null || content.isBlank()) {
                result.put("success", false);
                result.put("message", "文件内容为空或无法解析");
                return ResponseEntity.ok(result);
            }

            // 沿用原有解析逻辑：创建 Document → 切分 → 关键词补充（status=上传，保证咨询链路的 status 过滤语义可靠）
            Document document = new Document(content, Map.of("filename", filename, "status", "上传", "source", "user-upload"));
            List<Document> splitDocuments = myTokenTextSplitter.splitDocuments(List.of(document));
            List<Document> enrichedDocuments = myKeywordEnricher.enrichDocuments(splitDocuments);

            String parseId = UUID.randomUUID().toString().replace("-", "");
            parsedDocumentCache.put(parseId, new ParsedDocument(filename, enrichedDocuments, System.currentTimeMillis()));

            log.info("RAG 文档解析完成（待确认入库）: filename={}, chunks={}, parseId={}", filename, enrichedDocuments.size(), parseId);
            // 拼接所有片段的文本内容，供前端内联到 AI 消息中，确保 AI 能直接读到文档内容
            String textContent = enrichedDocuments.stream()
                    .map(Document::getText)
                    .collect(java.util.stream.Collectors.joining("\n\n"));
            result.put("success", true);
            result.put("message", "文档解析完成");
            result.put("parseId", parseId);
            result.put("filename", filename);
            result.put("chunks", enrichedDocuments.size());
            result.put("text", textContent);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("RAG 文档解析失败: filename={}", file.getOriginalFilename(), e);
            result.put("success", false);
            result.put("message", "解析失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 确认入库：将已解析缓存的文档写入向量库（用户发送提示词后由前端决定调用）
     */
    @PostMapping("/confirm")
    public ResponseEntity<Map<String, Object>> confirmImport(@RequestParam("parseId") String parseId) {
        Map<String, Object> result = new HashMap<>();
        ParsedDocument parsed = parsedDocumentCache.remove(parseId);
        if (parsed == null) {
            result.put("success", false);
            result.put("message", "解析结果不存在或已过期，请重新上传文档");
            return ResponseEntity.ok(result);
        }
        try {
            loveAppVectorStore.add(parsed.documents());
            // 同步写入全文检索索引，保证混合检索多数据源数据一致
            loveAppFullTextIndex.add(parsed.documents());
            log.info("RAG 文档确认入库成功: filename={}, chunks={}", parsed.filename(), parsed.documents().size());
            result.put("success", true);
            result.put("message", "文档导入成功");
            result.put("filename", parsed.filename());
            result.put("chunks", parsed.documents().size());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("RAG 文档确认入库失败: filename={}", parsed.filename(), e);
            // 入库失败时放回缓存，允许重试
            parsedDocumentCache.put(parseId, parsed);
            result.put("success", false);
            result.put("message", "导入失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 丢弃已解析的文档（用户在待上传区移除文档时调用）
     */
    @PostMapping("/discard")
    public ResponseEntity<Map<String, Object>> discardParsedDocument(@RequestParam("parseId") String parseId) {
        parsedDocumentCache.remove(parseId);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return ResponseEntity.ok(result);
    }

    /**
     * 清理过期的解析结果缓存
     */
    private void evictExpiredParsedDocuments() {
        long now = System.currentTimeMillis();
        parsedDocumentCache.entrySet().removeIf(entry -> now - entry.getValue().parsedAt() > PARSE_CACHE_TTL_MS);
    }

    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importDocument(@RequestParam("file") MultipartFile[] files) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> fileResults = new ArrayList<>();
        int totalChunks = 0;
        int successCount = 0;
        int failCount = 0;

        try {
            for (MultipartFile file : files) {
                Map<String, Object> fileResult = processSingleFile(file);
                fileResults.add(fileResult);
                if (Boolean.TRUE.equals(fileResult.get("success"))) {
                    successCount++;
                    totalChunks += (Integer) fileResult.getOrDefault("chunks", 0);
                } else {
                    failCount++;
                }
            }

            result.put("success", failCount == 0);
            result.put("message", String.format("成功导入 %d 个文件，失败 %d 个文件，共 %d 个片段", successCount, failCount, totalChunks));
            result.put("totalChunks", totalChunks);
            result.put("successCount", successCount);
            result.put("failCount", failCount);
            result.put("fileResults", fileResults);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("RAG 文档导入失败", e);
            result.put("success", false);
            result.put("message", "导入失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 处理单个文件导入
     */
    private Map<String, Object> processSingleFile(MultipartFile file) {
        Map<String, Object> fileResult = new HashMap<>();
        try {
            String filename = file.getOriginalFilename();
            if (filename == null || filename.isBlank()) {
                fileResult.put("success", false);
                fileResult.put("message", "文件名不能为空");
                return fileResult;
            }

            String content = extractText(file, filename.toLowerCase());
            if (content == null || content.isBlank()) {
                fileResult.put("success", false);
                fileResult.put("message", "文件内容为空或无法解析");
                return fileResult;
            }

            // 创建 Document（status=上传，保证咨询链路的 status 过滤语义可靠）
            Document document = new Document(content, Map.of("filename", filename, "status", "上传", "source", "user-upload"));
            List<Document> documents = List.of(document);

            // 切分文档
            List<Document> splitDocuments = myTokenTextSplitter.splitDocuments(documents);

            // 补充关键词元信息
            List<Document> enrichedDocuments = myKeywordEnricher.enrichDocuments(splitDocuments);

            // 存入向量库
            loveAppVectorStore.add(enrichedDocuments);

            // 同步写入全文检索索引，保证混合检索多数据源数据一致
            loveAppFullTextIndex.add(enrichedDocuments);

            log.info("RAG 文档导入成功: filename={}, chunks={}", filename, enrichedDocuments.size());
            fileResult.put("success", true);
            fileResult.put("message", "文档导入成功");
            fileResult.put("filename", filename);
            fileResult.put("chunks", enrichedDocuments.size());
        } catch (Exception e) {
            log.error("单个文件导入失败: filename={}", file.getOriginalFilename(), e);
            fileResult.put("success", false);
            fileResult.put("message", "导入失败: " + e.getMessage());
            fileResult.put("filename", file.getOriginalFilename());
        }
        return fileResult;
    }

    /**
     * 根据文件后缀提取文本内容
     */
    private String extractText(MultipartFile file, String filename) throws Exception {
        if (filename.endsWith(".docx")) {
            try (XWPFDocument doc = new XWPFDocument(file.getInputStream());
                 XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
                return extractor.getText();
            }
        } else if (filename.endsWith(".doc")) {
            try (HWPFDocument doc = new HWPFDocument(file.getInputStream());
                 WordExtractor extractor = new WordExtractor(doc)) {
                return extractor.getText();
            }
        } else if (filename.endsWith(".pdf")) {
            return extractPdfText(file);
        } else {
            // .md / .txt 等纯文本文件
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * 提取 PDF 文本内容（逐页提取，按版面位置排序）。
     * 注意：扫描件/纯图片 PDF 无文本层，提取结果为空，由调用方提示无法解析。
     */
    private String extractPdfText(MultipartFile file) throws Exception {
        StringBuilder text = new StringBuilder();
        try (PdfDocument pdfDocument = new PdfDocument(new PdfReader(file.getInputStream()))) {
            int pageCount = pdfDocument.getNumberOfPages();
            for (int i = 1; i <= pageCount; i++) {
                String pageText = PdfTextExtractor.getTextFromPage(
                        pdfDocument.getPage(i), new LocationTextExtractionStrategy());
                if (pageText != null && !pageText.isBlank()) {
                    text.append(pageText).append('\n');
                }
            }
        }
        return text.toString().trim();
    }
}
