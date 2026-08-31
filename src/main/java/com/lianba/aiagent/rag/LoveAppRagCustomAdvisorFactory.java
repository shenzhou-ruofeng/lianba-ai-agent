package com.lianba.aiagent.rag;

import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.util.StringUtils;

/**
 * 创建自定义的 RAG 检索增强顾问的工厂
 */
public class LoveAppRagCustomAdvisorFactory {

    /**
     * 创建自定义的 RAG 检索增强顾问
     *
     * @param vectorStore 向量存储
     * @param status      状态
     * @return 自定义的 RAG 检索增强顾问
     */
    public static Advisor createLoveAppRagCustomAdvisor(VectorStore vectorStore, String status) {
        // 过滤特定状态的文档
        Filter.Expression expression = new FilterExpressionBuilder()
                .eq("status", status)
                .build();
        // 创建文档检索器
        DocumentRetriever documentRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .filterExpression(expression) // 过滤条件
                .similarityThreshold(0.5) // 相似度阈值
                .topK(3) // 返回文档数量
                .build();
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever)
                .queryAugmenter(LoveAppContextualQueryAugmenterFactory.createInstance())
                .build();
    }

    /**
     * 创建恋爱对象推荐的 RAG 检索增强顾问
     * 只检索 status=对象 的候选人文档，配合推荐专属的上下文增强器
     *
     * @param vectorStore 向量存储
     * @return 恋爱对象推荐的 RAG 检索增强顾问
     */
    public static Advisor createLoveMatchAdvisor(VectorStore vectorStore) {
        return createLoveMatchAdvisor(vectorStore, null);
    }

    /**
     * 创建恋爱对象推荐的 RAG 检索增强顾问（支持性别硬过滤）
     *
     * @param vectorStore 向量存储
     * @param gender      期望对象性别（男/女），为空表示不限
     * @return 恋爱对象推荐的 RAG 检索增强顾问
     */
    public static Advisor createLoveMatchAdvisor(VectorStore vectorStore, String gender) {
        // 只检索恋爱对象候选人文档；指定性别时叠加 gender 硬过滤，从召回层杜绝性别不符的候选人
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        Filter.Expression expression = StringUtils.hasText(gender)
                ? builder.and(builder.eq("status", "对象"), builder.eq("gender", gender)).build()
                : builder.eq("status", "对象").build();
        // 创建文档检索器（择偶描述与候选人资料措辞差异大，放宽阈值；topK 5 给 AI 足够候选池）
        DocumentRetriever documentRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .filterExpression(expression)
                .similarityThreshold(0.3)
                .topK(5)
                .build();
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever)
                .queryAugmenter(LoveAppContextualQueryAugmenterFactory.createMatchInstance())
                .build();
    }
}
