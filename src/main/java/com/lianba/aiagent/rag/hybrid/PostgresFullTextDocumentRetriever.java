package com.lianba.aiagent.rag.hybrid;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 PostgreSQL 的关键词全文检索器（混合检索的外部数据源召回路）
 * <p>
 * 直接复用 PgVector 向量库所在的 PostgreSQL 实例，对 {@code vector_store} 表的
 * content 列做关键词匹配召回，再在 Java 端按命中词条数与词条长度重排。
 * 元信息过滤翻译为 {@code metadata->>'key'} 的 JSONB 查询条件下推到数据库执行。
 * <p>
 * 通过 rag.hybrid.pg-fulltext.enabled=true 开启（需配置 spring.datasource）。
 * 如需替换为 MySQL FULLTEXT / Elasticsearch / RediSearch，只需按相同方式
 * 实现 {@link DocumentRetriever} 并注册到混合检索器即可。
 */
@Slf4j
public class PostgresFullTextDocumentRetriever implements DocumentRetriever {

    /**
     * 参与 SQL 匹配的最大词条数，避免拼接过长的查询
     */
    private static final int MAX_QUERY_TERMS = 8;

    /**
     * 数据库侧粗召回的候选文档上限，精排在 Java 端完成
     */
    private static final int CANDIDATE_LIMIT = 100;

    private final JdbcTemplate jdbcTemplate;

    /**
     * 全文检索的目标表（与 PgVectorStore 共用），来源于应用配置而非用户输入
     */
    private final String tableName;

    private final int topK;

    private final List<MetadataFilterSpec> metadataFilters;

    public PostgresFullTextDocumentRetriever(JdbcTemplate jdbcTemplate, String tableName, int topK,
                                             List<MetadataFilterSpec> metadataFilters) {
        this.jdbcTemplate = jdbcTemplate;
        this.tableName = tableName;
        this.topK = topK;
        this.metadataFilters = metadataFilters == null ? List.of() : metadataFilters;
    }

    @Override
    public List<Document> retrieve(Query query) {
        List<String> terms = FullTextSearchIndex.tokenize(query.text()).stream()
                .distinct()
                .limit(MAX_QUERY_TERMS)
                .toList();
        if (terms.isEmpty()) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder("SELECT id::text AS id, content, metadata::text AS metadata FROM ")
                .append(tableName)
                .append(" WHERE (");
        List<Object> args = new ArrayList<>();
        for (int i = 0; i < terms.size(); i++) {
            if (i > 0) {
                sql.append(" OR ");
            }
            sql.append("content ILIKE ?");
            args.add("%" + terms.get(i) + "%");
        }
        sql.append(")");
        // 元信息过滤下推到数据库（字段名来源于代码内部定义，不存在注入风险）
        for (MetadataFilterSpec spec : metadataFilters) {
            if (spec.operator() == MetadataFilterSpec.Operator.EQ) {
                sql.append(" AND metadata->>'").append(spec.key()).append("' = ?");
            } else {
                sql.append(" AND (metadata->>'").append(spec.key())
                        .append("' IS NULL OR metadata->>'").append(spec.key()).append("' <> ?)");
            }
            args.add(String.valueOf(spec.value()));
        }
        sql.append(" LIMIT ").append(CANDIDATE_LIMIT);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        // Java 端精排：按命中词条的总长度打分（命中的词条越多、越长，相关度越高）
        return rows.stream()
                .map(row -> toScoredDocument(row, terms))
                .filter(document -> document.getScore() != null && document.getScore() > 0)
                .sorted(Comparator.comparing(Document::getScore).reversed())
                .limit(topK)
                .toList();
    }

    private Document toScoredDocument(Map<String, Object> row, List<String> terms) {
        String content = String.valueOf(row.get("content"));
        String lowerContent = content.toLowerCase();
        double score = terms.stream()
                .filter(term -> lowerContent.contains(term))
                .mapToDouble(String::length)
                .sum();
        return Document.builder()
                .id(String.valueOf(row.get("id")))
                .text(content)
                .metadata(parseMetadata(row.get("metadata")))
                .score(score)
                .build();
    }

    private Map<String, Object> parseMetadata(Object metadataJson) {
        if (metadataJson == null) {
            return new HashMap<>();
        }
        try {
            return new HashMap<>(JSONUtil.parseObj(String.valueOf(metadataJson)));
        } catch (Exception e) {
            log.warn("PostgreSQL 全文检索解析 metadata 失败: {}", metadataJson, e);
            return new HashMap<>();
        }
    }
}
