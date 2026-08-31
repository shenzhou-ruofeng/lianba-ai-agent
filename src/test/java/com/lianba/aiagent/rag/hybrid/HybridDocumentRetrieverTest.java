package com.lianba.aiagent.rag.hybrid;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 混合检索器单元测试（RRF 融合排序、跨数据源去重、单路失败降级）
 */
class HybridDocumentRetrieverTest {

    private static final Query QUERY = new Query("异地恋如何维持");

    private Document doc(String text) {
        return new Document(text, Map.of("status", "恋爱"));
    }

    @Test
    void documentHitByMultipleSourcesRanksFirst() {
        Document shared = doc("异地恋维持技巧：定期见面");
        Document vectorOnly = doc("恋爱沟通技巧：换位思考");
        Document keywordOnly = doc("异地恋礼物推荐清单");
        // 两路数据源分别生成不同 ID 的同内容文档，模拟各自独立入库
        DocumentRetriever vectorRetriever = query -> List.of(vectorOnly, doc(shared.getText()));
        DocumentRetriever keywordRetriever = query -> List.of(doc(shared.getText()), keywordOnly);

        HybridDocumentRetriever hybridRetriever = HybridDocumentRetriever.builder()
                .addRetriever(vectorRetriever)
                .addRetriever(keywordRetriever)
                .topK(3)
                .build();
        List<Document> results = hybridRetriever.retrieve(QUERY);

        // 双路命中的文档 RRF 累加分最高，且按内容去重后只出现一次
        assertEquals(3, results.size());
        assertEquals(shared.getText(), results.get(0).getText());
        long sharedCount = results.stream()
                .filter(document -> shared.getText().equals(document.getText()))
                .count();
        assertEquals(1, sharedCount);
    }

    @Test
    void failingSourceDegradesGracefully() {
        Document available = doc("异地恋维持技巧");
        DocumentRetriever failingRetriever = query -> {
            throw new IllegalStateException("模拟 Elasticsearch 连接失败");
        };
        DocumentRetriever workingRetriever = query -> List.of(available);

        HybridDocumentRetriever hybridRetriever = HybridDocumentRetriever.builder()
                .addRetriever(failingRetriever)
                .addRetriever(workingRetriever)
                .build();
        List<Document> results = hybridRetriever.retrieve(QUERY);

        // 单路失败仅降级，另一路结果正常返回
        assertEquals(1, results.size());
        assertEquals(available.getText(), results.get(0).getText());
    }

    @Test
    void respectsTopKLimit() {
        DocumentRetriever retriever = query -> List.of(
                doc("文档一"), doc("文档二"), doc("文档三"), doc("文档四"));

        HybridDocumentRetriever hybridRetriever = HybridDocumentRetriever.builder()
                .addRetriever(retriever)
                .topK(2)
                .build();

        assertEquals(2, hybridRetriever.retrieve(QUERY).size());
    }

    @Test
    void fusedScoreAttachedToDocuments() {
        DocumentRetriever retriever = query -> List.of(doc("文档一"));
        HybridDocumentRetriever hybridRetriever = HybridDocumentRetriever.builder()
                .addRetriever(retriever)
                .rrfK(60)
                .build();

        List<Document> results = hybridRetriever.retrieve(QUERY);
        // RRF 分数：1/(60+1)
        assertTrue(Math.abs(results.get(0).getScore() - 1.0 / 61) < 1e-9);
    }

    @Test
    void builderRequiresAtLeastOneRetriever() {
        assertThrows(IllegalStateException.class, () -> HybridDocumentRetriever.builder().build());
    }
}
