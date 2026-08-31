package com.yupi.yuaiagent.rag.hybrid;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 混合检索器：整合多数据源的搜索结果（多路并行召回 + RRF 融合排序）
 * <p>
 * 典型的两路召回组合：
 * <ul>
 *   <li>语义检索路：{@code VectorStoreDocumentRetriever}（向量数据库，理解查询语义）</li>
 *   <li>关键词检索路：{@link InMemoryFullTextDocumentRetriever} 或
 *       {@link PostgresFullTextDocumentRetriever}（全文检索，精确命中专有名词/关键词）</li>
 * </ul>
 * 任意实现 {@link DocumentRetriever} 的数据源（如 Elasticsearch、MySQL FULLTEXT、
 * RediSearch）都可以通过 {@link Builder#addRetriever} 注册进来，无需改动融合逻辑。
 * <p>
 * 融合策略采用 RRF（Reciprocal Rank Fusion）：{@code score = Σ 1/(rrfK + rank)}，
 * 只依赖各数据源内部的排名而非分数，天然规避了 BM25 分数与余弦相似度量纲不一致的问题。
 * 单一数据源检索失败时仅降级（丢弃该路结果）而不阻断整体检索。
 */
@Slf4j
public class HybridDocumentRetriever implements DocumentRetriever {

    private final List<DocumentRetriever> retrievers;

    /**
     * 融合后最终返回的文档数量
     */
    private final int topK;

    /**
     * RRF 融合常数，越大各数据源排名差异被抹平得越多，经验值 60
     */
    private final int rrfK;

    private HybridDocumentRetriever(Builder builder) {
        this.retrievers = List.copyOf(builder.retrievers);
        this.topK = builder.topK;
        this.rrfK = builder.rrfK;
    }

    @Override
    public List<Document> retrieve(Query query) {
        // 多数据源并行召回，互不阻塞
        List<CompletableFuture<List<Document>>> futures = retrievers.stream()
                .map(retriever -> CompletableFuture.supplyAsync(() -> safeRetrieve(retriever, query)))
                .toList();
        List<List<Document>> resultLists = futures.stream()
                .map(CompletableFuture::join)
                .toList();
        List<Document> fusedDocuments = fuse(resultLists);
        log.info("混合检索完成: 数据源数={}, 融合后文档数={}", retrievers.size(), fusedDocuments.size());
        return fusedDocuments;
    }

    /**
     * 单一数据源检索失败时降级为空结果，保证其余数据源仍可用
     */
    private List<Document> safeRetrieve(DocumentRetriever retriever, Query query) {
        try {
            return retriever.retrieve(query);
        } catch (Exception e) {
            log.warn("混合检索单路召回失败，已降级: retriever={}", retriever.getClass().getSimpleName(), e);
            return List.of();
        }
    }

    /**
     * RRF 融合：按各数据源内排名累加倒数分，跨数据源去重后取 topK
     */
    private List<Document> fuse(List<List<Document>> resultLists) {
        Map<String, Double> scoreByKey = new LinkedHashMap<>();
        Map<String, Document> documentByKey = new HashMap<>();
        for (List<Document> resultList : resultLists) {
            for (int rank = 0; rank < resultList.size(); rank++) {
                Document document = resultList.get(rank);
                String key = dedupKey(document);
                scoreByKey.merge(key, 1.0 / (rrfK + rank + 1), Double::sum);
                documentByKey.putIfAbsent(key, document);
            }
        }
        return scoreByKey.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(entry -> withScore(documentByKey.get(entry.getKey()), entry.getValue()))
                .toList();
    }

    /**
     * 去重键：不同数据源对同一文档生成的 ID 可能不同（各自独立入库），
     * 因此优先按文本内容去重，无文本时退化为文档 ID
     */
    private String dedupKey(Document document) {
        String text = document.getText();
        if (text == null || text.isBlank()) {
            return document.getId();
        }
        return text.length() + ":" + text.hashCode();
    }

    /**
     * 复制文档并附加 RRF 融合分数
     */
    private Document withScore(Document document, double score) {
        return Document.builder()
                .id(document.getId())
                .text(document.getText())
                .metadata(document.getMetadata())
                .score(score)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final List<DocumentRetriever> retrievers = new ArrayList<>();

        private int topK = 5;

        private int rrfK = 60;

        /**
         * 注册一路检索数据源（向量库 / 全文索引 / ES / MySQL / Redis 等）
         */
        public Builder addRetriever(DocumentRetriever retriever) {
            this.retrievers.add(retriever);
            return this;
        }

        public Builder topK(int topK) {
            this.topK = topK;
            return this;
        }

        public Builder rrfK(int rrfK) {
            this.rrfK = rrfK;
            return this;
        }

        public HybridDocumentRetriever build() {
            if (retrievers.isEmpty()) {
                throw new IllegalStateException("混合检索器至少需要注册一路检索数据源");
            }
            return new HybridDocumentRetriever(this);
        }
    }
}
