package com.lianba.aiagent.rag.hybrid;

import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 元信息过滤规格单元测试（断言语义、过滤表达式转换）
 */
class MetadataFilterSpecTest {

    @Test
    void predicateHandlesEqAndNe() {
        Predicate<Map<String, Object>> predicate = MetadataFilterSpec.toPredicate(List.of(
                MetadataFilterSpec.eq("docType", "恋爱常见问题和回答"),
                MetadataFilterSpec.ne("status", "对象")));

        assertTrue(predicate.test(Map.of("docType", "恋爱常见问题和回答", "status", "单身")));
        // status=对象 被 NE 条件排除
        assertFalse(predicate.test(Map.of("docType", "恋爱常见问题和回答", "status", "对象")));
        // docType 不匹配被 EQ 条件排除
        assertFalse(predicate.test(Map.of("docType", "恋爱对象信息", "status", "单身")));
    }

    @Test
    void nePassesWhenMetadataKeyMissing() {
        Predicate<Map<String, Object>> predicate = MetadataFilterSpec.toPredicate(
                List.of(MetadataFilterSpec.ne("status", "对象")));
        // 元信息中不存在该字段时视为通过
        assertTrue(predicate.test(new HashMap<>()));
        assertTrue(predicate.test(null));
    }

    @Test
    void toFilterExpressionBuildsCombinedExpression() {
        Filter.Expression expression = MetadataFilterSpec.toFilterExpression(List.of(
                MetadataFilterSpec.eq("docType", "恋爱常见问题和回答"),
                MetadataFilterSpec.ne("status", "对象")));
        assertNotNull(expression);
        // 两个条件按 AND 组合
        assertTrue(expression.type() == Filter.ExpressionType.AND);
    }

    @Test
    void toFilterExpressionReturnsNullForEmptySpecs() {
        assertNull(MetadataFilterSpec.toFilterExpression(List.of()));
        assertNull(MetadataFilterSpec.toFilterExpression(null));
    }
}
