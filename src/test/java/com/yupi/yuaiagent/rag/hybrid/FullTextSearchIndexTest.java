package com.yupi.yuaiagent.rag.hybrid;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 全文检索索引单元测试（分词、BM25 排序、元信息过滤）
 */
class FullTextSearchIndexTest {

    @Test
    void tokenizeMixedChineseAndEnglish() {
        List<String> terms = FullTextSearchIndex.tokenize("异地恋LDR 100天，如何维持？");
        // 中文按二元切分
        assertTrue(terms.contains("异地"));
        assertTrue(terms.contains("地恋"));
        assertTrue(terms.contains("维持"));
        // 英文/数字整词切分且小写
        assertTrue(terms.contains("ldr"));
        assertTrue(terms.contains("100"));
    }

    @Test
    void searchRanksKeywordMatchedDocumentFirst() {
        FullTextSearchIndex index = new FullTextSearchIndex();
        Document ldrDoc = new Document("异地恋如何维持感情：定期见面、保持沟通、建立信任", Map.of("status", "恋爱"));
        Document quarrelDoc = new Document("情侣吵架后如何和好：主动道歉、换位思考", Map.of("status", "恋爱"));
        Document inLawDoc = new Document("婆媳关系处理技巧：保持边界、丈夫居中协调", Map.of("status", "已婚"));
        index.add(List.of(ldrDoc, quarrelDoc, inLawDoc));

        List<Document> results = index.search("异地恋如何维持", 2, null);
        assertEquals(2, results.size());
        // 关键词"异地恋""维持"命中最多的文档排第一（另一篇仅命中"如何"）
        assertEquals(ldrDoc.getText(), results.get(0).getText());
        assertTrue(results.get(0).getScore() > 0);
    }

    @Test
    void searchAppliesMetadataFilter() {
        FullTextSearchIndex index = new FullTextSearchIndex();
        Document candidateDoc = new Document("恋爱对象候选人小美：25岁，喜欢旅行和摄影", Map.of("status", "对象"));
        Document adviceDoc = new Document("如何跟喜欢旅行的恋爱对象相处：多规划共同出行", Map.of("status", "恋爱"));
        index.add(List.of(candidateDoc, adviceDoc));

        // 元信息过滤：排除 status=对象 的候选人文档
        Predicate<Map<String, Object>> excludeCandidates =
                MetadataFilterSpec.toPredicate(List.of(MetadataFilterSpec.ne("status", "对象")));
        List<Document> results = index.search("喜欢旅行的恋爱对象", 5, excludeCandidates);

        assertEquals(1, results.size());
        assertEquals(adviceDoc.getText(), results.get(0).getText());
    }

    @Test
    void searchReturnsEmptyWhenNoTermMatches() {
        FullTextSearchIndex index = new FullTextSearchIndex();
        index.add(List.of(new Document("恋爱沟通技巧", Map.of())));
        assertTrue(index.search("Kubernetes", 5, null).isEmpty());
    }
}
