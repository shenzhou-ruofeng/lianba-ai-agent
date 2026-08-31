package com.yupi.yuaiagent.rag;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.util.StringUtils;

import java.util.stream.Collectors;

/**
 * 创建上下文查询增强器的工厂
 */
public class LoveAppContextualQueryAugmenterFactory {

    public static ContextualQueryAugmenter createInstance() {
        PromptTemplate emptyContextPromptTemplate = new PromptTemplate("""
                你应该输出下面的内容：
                抱歉，我只能回答恋爱相关的问题，别的没办法帮到您哦，
                有问题可以联系恋爱大师APP客服 https://github.com/shenzhou-ruofeng
                """);
        return ContextualQueryAugmenter.builder()
                .allowEmptyContext(false)
                .emptyContextPromptTemplate(emptyContextPromptTemplate)
                .build();
    }

    /**
     * 创建带有链接真实性约束的上下文查询增强器
     * 强制 LLM 使用文档中的真实课程链接，禁止编造
     */
    public static ContextualQueryAugmenter createLinkAwareInstance() {
        PromptTemplate promptTemplate = new PromptTemplate("""
                以下是相关的参考资料：
                
                {context}
                
                用户的问题：{query}
                
                请基于以上参考资料回答用户问题。
                重要规则：
                - 如果参考资料中包含课程推荐和超链接，必须使用资料中提供的真实课程名称和链接，逐字复制。
                - 禁止编造任何参考资料中不存在的课程名或链接。
                - 禁止修改链接地址。
                - 回答时不要提及"参考资料""知识库""检索""文档"等字眼。
                """);
        PromptTemplate emptyContextPromptTemplate = new PromptTemplate("""
                用户的问题：{query}
                
                你应该输出下面的内容：
                抱歉，我只能回答恋爱相关的问题，别的没办法帮到您哦，
                有问题可以联系恋爱大师APP客服 https://github.com/shenzhou-ruofeng
                """);
        return ContextualQueryAugmenter.builder()
                .promptTemplate(promptTemplate)
                .allowEmptyContext(false)
                .emptyContextPromptTemplate(emptyContextPromptTemplate)
                .build();
    }

    /**
     * 创建恋爱对象推荐专用的上下文查询增强器
     * 强制 LLM 只推荐候选人资料中真实存在的对象，禁止编造
     */
    public static ContextualQueryAugmenter createMatchInstance() {
        PromptTemplate promptTemplate = new PromptTemplate("""
                以下是可推荐的恋爱对象候选人资料：

                {context}

                用户的需求：{query}

                请基于以上候选人资料，为用户推荐 1-3 位最匹配的恋爱对象。
                重要规则：
                - 只能推荐资料中真实存在的候选人，昵称、年龄、星座、职业等信息必须与资料完全一致。
                - 逐条说明每位推荐对象与用户需求的匹配理由（如年龄、星座、职业、性格、兴趣的契合点）。
                - 禁止编造资料中不存在的人物或信息。
                - 回答时不要提及"候选人资料""知识库""检索""文档"等字眼，像热心红娘介绍朋友一样自然表达。
                """);
        PromptTemplate emptyContextPromptTemplate = new PromptTemplate("""
                用户的需求：{query}

                你应该输出下面的内容：
                暂时没有找到合适的推荐对象，请多描述一下你的择偶偏好（如期望的年龄、职业、性格），
                我会继续为你留意合适的人选哦～
                """);
        return ContextualQueryAugmenter.builder()
                .promptTemplate(promptTemplate)
                .allowEmptyContext(false)
                .emptyContextPromptTemplate(emptyContextPromptTemplate)
                // 默认只拼接正文，而候选人昵称/性别/年龄/星座/职业都在标题（title 元数据）里，需一并拼入上下文
                .documentFormatter(documents -> documents.stream()
                        .map(document -> {
                            String title = String.valueOf(document.getMetadata().getOrDefault("title", ""));
                            return StringUtils.hasText(title) ? title + "\n" + document.getText() : document.getText();
                        })
                        .collect(Collectors.joining("\n\n")))
                .build();
    }
}
