package com.lianba.aiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 恋爱大师应用文档加载器
 */
@Component
@Slf4j
public class LoveAppDocumentLoader {

    private final ResourcePatternResolver resourcePatternResolver;

    public LoveAppDocumentLoader(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }

    /**
     * 加载多篇 Markdown 文档
     * @return
     */
    public List<Document> loadMarkdowns() {
        List<Document> allDocuments = new ArrayList<>();
        try {
            Resource[] resources = resourcePatternResolver.getResources("classpath:document/*.md");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                // 提取文档倒数第 3 和第 2 个字作为标签
                String status = filename.substring(filename.length() - 6, filename.length() - 4);
                // 提取“ - ”前的部分作为文档类型（如：恋爱常见问题和回答 / 恋爱对象信息）
                int docTypeSeparatorIndex = filename.indexOf(" - ");
                String docType = docTypeSeparatorIndex > 0 ? filename.substring(0, docTypeSeparatorIndex) : "通用";
                MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                        .withHorizontalRuleCreateDocument(true)
                        .withIncludeCodeBlock(false)
                        .withIncludeBlockquote(false)
                        .withAdditionalMetadata("filename", filename)
                        .withAdditionalMetadata("status", status)
                        // 补充更多元信息，支持检索时按文档类型、来源等维度过滤
                        .withAdditionalMetadata("docType", docType)
                        .withAdditionalMetadata("source", "local-markdown")
                        .withAdditionalMetadata("loadedAt", LocalDate.now().toString())
                        .build();
                MarkdownDocumentReader markdownDocumentReader = new MarkdownDocumentReader(resource, config);
                List<Document> documents = markdownDocumentReader.get();
                // 为同一文件切分出的每个片段补充序号元信息，便于溯源和调试
                for (int i = 0; i < documents.size(); i++) {
                    documents.get(i).getMetadata().put("chunkIndex", i);
                }
                // 恋爱对象文档补充 gender 元数据（从候选人标题的“（男，/（女，”中提取），支持推荐时按性别硬过滤
                if ("对象".equals(status)) {
                    for (Document document : documents) {
                        String title = String.valueOf(document.getMetadata().getOrDefault("title", ""));
                        if (title.contains("（男，")) {
                            document.getMetadata().put("gender", "男");
                        } else if (title.contains("（女，")) {
                            document.getMetadata().put("gender", "女");
                        }
                    }
                }
                allDocuments.addAll(documents);
            }
        } catch (IOException e) {
           log.error("Markdown 文档加载失败", e);
        }
        return allDocuments;
    }

    /**
     * 解析知识库文档中的课程推荐链接（Markdown 格式：[《课程名》](https://...)）
     *
     * @return 课程名 -> 链接，保持文档出现顺序
     */
    public Map<String, String> loadCourseLinks() {
        Map<String, String> courseLinks = new LinkedHashMap<>();
        try {
            Resource[] resources = resourcePatternResolver.getResources("classpath:document/*.md");
            for (Resource resource : resources) {
                String content = resource.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
                Matcher matcher = COURSE_LINK_PATTERN.matcher(content);
                while (matcher.find()) {
                    courseLinks.putIfAbsent(matcher.group(1), matcher.group(2));
                }
            }
        } catch (IOException e) {
            log.error("课程链接解析失败", e);
        }
        return courseLinks;
    }

    /**
     * 匹配推荐课程 Markdown 链接：[《课程名》](链接)
     */
    private static final Pattern COURSE_LINK_PATTERN =
            Pattern.compile("\\[《([^》]+)》\\]\\((https?://[^)\\s]+)\\)");
}
