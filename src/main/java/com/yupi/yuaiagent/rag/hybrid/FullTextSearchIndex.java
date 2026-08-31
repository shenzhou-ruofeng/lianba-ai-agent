package com.yupi.yuaiagent.rag.hybrid;

import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * 内存全文检索索引（倒排索引 + BM25 打分）
 * <p>
 * 作为混合检索中"关键词全文检索"这一路的默认数据源，无需依赖外部中间件即可运行。
 * 检索语义与 Elasticsearch / MySQL FULLTEXT 等价（关键词精确命中 + 相关度排序），
 * 生产环境可平滑替换为基于外部存储的 {@code DocumentRetriever} 实现。
 * <p>
 * 分词策略：中文按二元切分（bigram），英文/数字按整词切分并统一小写，
 * 无需引入分词器依赖即可获得可用的中文全文检索效果。
 */
public class FullTextSearchIndex {

    /**
     * BM25 词频饱和参数
     */
    private static final double BM25_K1 = 1.2;

    /**
     * BM25 文档长度归一化参数
     */
    private static final double BM25_B = 0.75;

    /**
     * 文档 ID -> 文档
     */
    private final Map<String, Document> documentById = new ConcurrentHashMap<>();

    /**
     * 倒排索引：词条 -> (文档 ID -> 词频)
     */
    private final Map<String, Map<String, Integer>> invertedIndex = new ConcurrentHashMap<>();

    /**
     * 文档 ID -> 文档词条总数（BM25 长度归一化用）
     */
    private final Map<String, Integer> docTermCount = new ConcurrentHashMap<>();

    /**
     * 将文档批量写入索引（与向量库写入保持同步调用，保证多数据源数据一致）
     *
     * @param documents 文档列表
     */
    public synchronized void add(List<Document> documents) {
        if (documents == null) {
            return;
        }
        for (Document document : documents) {
            String text = document.getText();
            if (text == null || text.isBlank() || documentById.containsKey(document.getId())) {
                continue;
            }
            List<String> terms = tokenize(text);
            if (terms.isEmpty()) {
                continue;
            }
            documentById.put(document.getId(), document);
            docTermCount.put(document.getId(), terms.size());
            for (String term : terms) {
                invertedIndex.computeIfAbsent(term, k -> new ConcurrentHashMap<>())
                        .merge(document.getId(), 1, Integer::sum);
            }
        }
    }

    /**
     * 索引中的文档数量
     */
    public int size() {
        return documentById.size();
    }

    /**
     * 关键词全文检索（BM25 相关度排序）
     *
     * @param query          查询文本
     * @param topK           返回文档数量
     * @param metadataFilter 元信息过滤断言（null 表示不过滤）
     * @return 命中文档列表（按相关度降序，score 为 BM25 分数）
     */
    public List<Document> search(String query, int topK, Predicate<Map<String, Object>> metadataFilter) {
        List<String> queryTerms = tokenize(query).stream().distinct().toList();
        if (queryTerms.isEmpty() || documentById.isEmpty()) {
            return List.of();
        }
        int totalDocs = documentById.size();
        double avgDocLength = docTermCount.values().stream()
                .mapToInt(Integer::intValue).average().orElse(1);
        Map<String, Double> scoreByDocId = new HashMap<>();
        for (String term : queryTerms) {
            Map<String, Integer> postings = invertedIndex.get(term);
            if (postings == null || postings.isEmpty()) {
                continue;
            }
            // BM25 逆文档频率：命中文档越少的词条区分度越高
            double idf = Math.log(1 + (totalDocs - postings.size() + 0.5) / (postings.size() + 0.5));
            for (Map.Entry<String, Integer> posting : postings.entrySet()) {
                int tf = posting.getValue();
                double docLength = docTermCount.getOrDefault(posting.getKey(), 1);
                double tfNorm = tf * (BM25_K1 + 1)
                        / (tf + BM25_K1 * (1 - BM25_B + BM25_B * docLength / avgDocLength));
                scoreByDocId.merge(posting.getKey(), idf * tfNorm, Double::sum);
            }
        }
        return scoreByDocId.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(entry -> {
                    Document document = documentById.get(entry.getKey());
                    return document == null ? null : withScore(document, entry.getValue());
                })
                .filter(Objects::nonNull)
                .filter(document -> metadataFilter == null || metadataFilter.test(document.getMetadata()))
                .limit(topK)
                .toList();
    }

    /**
     * 复制文档并附加检索分数
     */
    private Document withScore(Document document, double score) {
        return Document.builder()
                .id(document.getId())
                .text(document.getText())
                .metadata(document.getMetadata())
                .score(score)
                .build();
    }

    /**
     * 分词：中文（HAN 字符）按二元切分，英文/数字按整词切分并小写，其余字符视为分隔符
     *
     * @param text 待切分文本
     * @return 词条列表
     */
    public static List<String> tokenize(String text) {
        List<String> terms = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return terms;
        }
        StringBuilder wordBuffer = new StringBuilder();
        StringBuilder cjkBuffer = new StringBuilder();
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) {
                flushWord(terms, wordBuffer);
                cjkBuffer.appendCodePoint(codePoint);
            } else if (Character.isLetterOrDigit(codePoint)) {
                flushCjk(terms, cjkBuffer);
                wordBuffer.appendCodePoint(codePoint);
            } else {
                flushWord(terms, wordBuffer);
                flushCjk(terms, cjkBuffer);
            }
            i += Character.charCount(codePoint);
        }
        flushWord(terms, wordBuffer);
        flushCjk(terms, cjkBuffer);
        return terms;
    }

    private static void flushWord(List<String> terms, StringBuilder wordBuffer) {
        if (!wordBuffer.isEmpty()) {
            terms.add(wordBuffer.toString().toLowerCase(Locale.ROOT));
            wordBuffer.setLength(0);
        }
    }

    private static void flushCjk(List<String> terms, StringBuilder cjkBuffer) {
        int length = cjkBuffer.length();
        if (length == 1) {
            // 孤立单字直接作为词条
            terms.add(cjkBuffer.toString());
        } else if (length > 1) {
            // 连续中文按二元切分：如 "异地恋" -> ["异地", "地恋"]
            for (int i = 0; i < length - 1; i++) {
                terms.add(cjkBuffer.substring(i, i + 2));
            }
        }
        cjkBuffer.setLength(0);
    }
}
