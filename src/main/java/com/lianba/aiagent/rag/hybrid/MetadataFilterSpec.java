package com.lianba.aiagent.rag.hybrid;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 元信息过滤规格（混合检索中各数据源统一的过滤条件描述）
 * <p>
 * 同一份过滤条件需要同时作用于多个数据源：
 * 向量库使用 {@link Filter.Expression}，内存全文索引使用 {@link Predicate}，
 * PostgreSQL 全文检索翻译为 SQL WHERE 条件。
 * 通过该规格统一描述，保证多路召回的过滤语义一致。
 *
 * @param key      元信息字段名
 * @param operator 比较操作符
 * @param value    比较值
 */
public record MetadataFilterSpec(String key, Operator operator, Object value) {

    /**
     * 支持的比较操作符
     */
    public enum Operator {
        EQ, NE
    }

    public static MetadataFilterSpec eq(String key, Object value) {
        return new MetadataFilterSpec(key, Operator.EQ, value);
    }

    public static MetadataFilterSpec ne(String key, Object value) {
        return new MetadataFilterSpec(key, Operator.NE, value);
    }

    /**
     * 转换为向量库的过滤表达式（多个条件之间为 AND 关系）
     *
     * @param specs 过滤规格列表
     * @return 过滤表达式，列表为空时返回 null（表示不过滤）
     */
    public static Filter.Expression toFilterExpression(List<MetadataFilterSpec> specs) {
        if (specs == null || specs.isEmpty()) {
            return null;
        }
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        FilterExpressionBuilder.Op combined = null;
        for (MetadataFilterSpec spec : specs) {
            FilterExpressionBuilder.Op current = spec.operator() == Operator.EQ
                    ? builder.eq(spec.key(), spec.value())
                    : builder.ne(spec.key(), spec.value());
            combined = combined == null ? current : builder.and(combined, current);
        }
        return combined.build();
    }

    /**
     * 转换为基于元信息 Map 的断言（多个条件之间为 AND 关系）
     * NE 语义：元信息中不存在该字段时视为通过（与向量库过滤行为保持一致）
     *
     * @param specs 过滤规格列表
     * @return 元信息断言
     */
    public static Predicate<Map<String, Object>> toPredicate(List<MetadataFilterSpec> specs) {
        return metadata -> {
            if (specs == null || specs.isEmpty()) {
                return true;
            }
            for (MetadataFilterSpec spec : specs) {
                Object actual = metadata == null ? null : metadata.get(spec.key());
                boolean equalsValue = actual != null
                        && String.valueOf(actual).equals(String.valueOf(spec.value()));
                if (spec.operator() == Operator.EQ && !equalsValue) {
                    return false;
                }
                if (spec.operator() == Operator.NE && equalsValue) {
                    return false;
                }
            }
            return true;
        };
    }
}
