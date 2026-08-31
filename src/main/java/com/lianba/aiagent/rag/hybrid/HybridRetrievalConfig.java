package com.lianba.aiagent.rag.hybrid;

import com.lianba.aiagent.config.HybridRetrievalProperties;
import com.lianba.aiagent.rag.LoveAppContextualQueryAugmenterFactory;
import com.lianba.aiagent.rag.LoveAppDocumentLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * 混合检索装配配置
 * <p>
 * 组装"向量语义检索 + 关键词全文检索"的多路召回，并基于
 * {@link RetrievalAugmentationAdvisor} 查询增强顾问对外暴露统一的混合 RAG Advisor。
 */
@Slf4j
@Configuration
public class HybridRetrievalConfig {

    /**
     * 恋爱咨询链路的元信息过滤条件：排除恋爱对象候选人文档
     * （与 doChatWithRag 的 status != '对象' 过滤语义保持一致）
     */
    private static final List<MetadataFilterSpec> CONSULT_FILTER_SPECS =
            List.of(MetadataFilterSpec.ne("status", "对象"));

    /**
     * 内存全文检索索引 Bean（关键词召回路的默认数据源）
     * 与向量库加载同一批本地 Markdown 文档，保证两路数据一致；
     * 全文检索只依赖原始文本，无需再次调用 AI 补充关键词元信息。
     */
    @Bean
    public FullTextSearchIndex loveAppFullTextIndex(LoveAppDocumentLoader loveAppDocumentLoader) {
        FullTextSearchIndex fullTextSearchIndex = new FullTextSearchIndex();
        fullTextSearchIndex.add(loveAppDocumentLoader.loadMarkdowns());
        log.info("全文检索索引初始化完成，文档数: {}", fullTextSearchIndex.size());
        return fullTextSearchIndex;
    }

    /**
     * PostgreSQL 全文检索召回路（可选数据源，默认关闭）
     * 开启后与向量检索、内存全文检索一起参与多路召回融合
     */
    @Bean
    @ConditionalOnProperty(prefix = "rag.hybrid.pg-fulltext", name = "enabled", havingValue = "true")
    public PostgresFullTextDocumentRetriever postgresFullTextDocumentRetriever(
            JdbcTemplate jdbcTemplate, HybridRetrievalProperties properties) {
        return new PostgresFullTextDocumentRetriever(
                jdbcTemplate,
                properties.getPgFulltext().getTable(),
                properties.getTopK(),
                CONSULT_FILTER_SPECS);
    }

    /**
     * 混合检索 RAG 查询增强顾问 Bean
     * <p>
     * 检索链路：向量语义检索（元信息 Filter.Expression 过滤）
     * + 关键词全文检索（元信息断言过滤）
     * + 可选的 PostgreSQL 全文检索（元信息 SQL 条件过滤）
     * → RRF 融合排序 → ContextualQueryAugmenter 上下文增强
     */
    @Bean
    public Advisor loveAppHybridRagAdvisor(VectorStore loveAppVectorStore,
                                           FullTextSearchIndex loveAppFullTextIndex,
                                           ObjectProvider<PostgresFullTextDocumentRetriever> pgRetrieverProvider,
                                           HybridRetrievalProperties properties) {
        // 语义检索路：向量库 + 元信息过滤表达式
        DocumentRetriever vectorRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(loveAppVectorStore)
                .filterExpression(MetadataFilterSpec.toFilterExpression(CONSULT_FILTER_SPECS))
                .similarityThreshold(properties.getSimilarityThreshold())
                .topK(properties.getTopK())
                .build();
        // 关键词检索路：内存全文索引 + 相同语义的元信息断言
        DocumentRetriever keywordRetriever = new InMemoryFullTextDocumentRetriever(
                loveAppFullTextIndex, properties.getTopK(),
                MetadataFilterSpec.toPredicate(CONSULT_FILTER_SPECS));
        HybridDocumentRetriever.Builder hybridBuilder = HybridDocumentRetriever.builder()
                .addRetriever(vectorRetriever)
                .addRetriever(keywordRetriever)
                .topK(properties.getTopK())
                .rrfK(properties.getRrfK());
        // 可选的 PostgreSQL 全文检索路（未启用时自动跳过）
        pgRetrieverProvider.ifAvailable(hybridBuilder::addRetriever);
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(hybridBuilder.build())
                .queryAugmenter(LoveAppContextualQueryAugmenterFactory.createLinkAwareInstance())
                .build();
    }
}
