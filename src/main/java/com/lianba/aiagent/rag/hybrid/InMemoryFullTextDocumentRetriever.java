package com.lianba.aiagent.rag.hybrid;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 基于内存全文索引的关键词检索器（混合检索的关键词召回路）
 * <p>
 * 实现 Spring AI 的 {@link DocumentRetriever} 接口，
 * 可直接注册到 {@link HybridDocumentRetriever} 中与向量语义检索并行召回。
 */
public class InMemoryFullTextDocumentRetriever implements DocumentRetriever {

    private final FullTextSearchIndex fullTextSearchIndex;

    private final int topK;

    /**
     * 元信息过滤断言（与向量检索路的 Filter.Expression 语义保持一致）
     */
    private final Predicate<Map<String, Object>> metadataFilter;

    public InMemoryFullTextDocumentRetriever(FullTextSearchIndex fullTextSearchIndex, int topK,
                                             Predicate<Map<String, Object>> metadataFilter) {
        this.fullTextSearchIndex = fullTextSearchIndex;
        this.topK = topK;
        this.metadataFilter = metadataFilter;
    }

    @Override
    public List<Document> retrieve(Query query) {
        return fullTextSearchIndex.search(query.text(), topK, metadataFilter);
    }
}
