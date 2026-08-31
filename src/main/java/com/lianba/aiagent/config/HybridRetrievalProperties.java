package com.lianba.aiagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG 混合检索配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "rag.hybrid")
public class HybridRetrievalProperties {

    /**
     * 融合后返回的文档数量
     */
    private int topK = 5;

    /**
     * RRF 融合常数，越大各数据源排名差异被抹平得越多
     */
    private int rrfK = 60;

    /**
     * 向量语义检索路的相似度阈值
     */
    private double similarityThreshold = 0.5;

    /**
     * PostgreSQL 全文检索数据源配置
     */
    private PgFulltext pgFulltext = new PgFulltext();

    @Data
    public static class PgFulltext {

        /**
         * 是否启用 PostgreSQL 全文检索召回路（需配置 spring.datasource）
         */
        private boolean enabled = false;

        /**
         * 全文检索的目标表（与 PgVectorStore 共用）
         */
        private String table = "vector_store";
    }
}
