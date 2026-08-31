package com.lianba.aiagent.app;

import cn.hutool.core.collection.CollUtil;
import com.lianba.aiagent.advisor.MyLoggerAdvisor;
import com.lianba.aiagent.advisor.ReReadingAdvisor;
import com.lianba.aiagent.app.model.LoveReport;
import com.lianba.aiagent.chatmemory.DbBasedChatMemoryRepository;
import com.lianba.aiagent.mapper.ChatMemoryMapper;
import com.lianba.aiagent.rag.LoveAppContextualQueryAugmenterFactory;
import com.lianba.aiagent.rag.LoveAppDocumentLoader;
import com.lianba.aiagent.rag.LoveAppRagCustomAdvisorFactory;
import com.lianba.aiagent.rag.QueryRewriter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
public class LoveApp {

    private final ChatClient chatClient;

    private final ChatModel chatModel;

    private final ChatMemory chatMemory;

    // 工具调用管理器：用于手动控制工具执行流程，提升可观测性
    private final ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();

    private static final String SYSTEM_PROMPT = """
            你是深耕恋爱心理领域的专家。
            开场向用户表明身份，告知用户可倾诉恋爱难题，开场白中不要推荐课程。
            围绕单身、恋爱、已婚三种状态提问：
            - 单身状态询问社交圈拓展及追求心仪对象的困扰；
            - 恋爱状态询问沟通、习惯差异引发的矛盾；
            - 已婚状态询问家庭责任与亲属关系处理的问题。
            引导用户详述事情经过、对方反应及自身想法，以便给出专属解决方案。
            不要提及"知识库""检索""参考资料""文档"等字眼，直接自然地给出建议。
            关于课程推荐：只有当上下文资料或课程清单中明确包含课程名称和超链接时，才可以推荐。
            推荐时必须使用 Markdown 链接格式输出：推荐课程：[《课程名》](链接)。
            课程名和链接必须与资料/清单中完全一致，逐字复制，禁止编造课程名、禁止修改链接地址、
            禁止输出资料中不存在的链接；若资料中没有课程信息，则不推荐课程。
            回答时请自然、温暖、有同理心。
            """;

    private static final String MATCH_SYSTEM_PROMPT = """
            你是一位经验丰富、热心靠谱的专业红娘。
            你的任务是根据用户的基本情况和择偶偏好，为用户推荐合适的恋爱对象。
            若用户信息不足，主动询问用户的基本情况（年龄、城市、职业）与择偶偏好（期望的年龄段、星座、职业、性格等）。
            推荐时逐条说明匹配理由，只能推荐上下文资料中真实存在的候选人，
            禁止编造资料中不存在的人物或信息。
            不要提及"知识库""检索""参考资料""文档"等字眼，像介绍朋友一样自然表达。
            语气热情、自然、真诚。
            """;

    /**
     * 初始化 ChatClient
     *
     * @param dashscopeChatModel 通义千问 ChatModel
     * @param chatMemoryMapper   对话记忆 Mapper（数据库持久化）
     */
    public LoveApp(ChatModel dashscopeChatModel, ChatMemoryMapper chatMemoryMapper) {
        this.chatModel = dashscopeChatModel;
        // 初始化基于数据库持久化的对话记忆（重启后同 chatId 上下文延续），保留消息窗口裁剪
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new DbBasedChatMemoryRepository(chatMemoryMapper))
                .maxMessages(20)
                .build();
        this.chatMemory = chatMemory;
        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        // 自定义日志 Advisor，可按需开启
                        new MyLoggerAdvisor()
//                        // 自定义推理增强 Advisor，可按需开启
//                       ,new ReReadingAdvisor()
                )
                .build();
    }

    /**
     * AI 基础对话（支持多轮对话记忆）
     *
     * @param message
     * @param chatId
     * @return
     */
    public String doChat(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    /**
     * AI 基础对话（支持多轮对话记忆，SSE 流式传输）
     *
     * @param message
     * @param chatId
     * @return
     */
    public Flux<String> doChatByStream(String message, String chatId) {
        return chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .stream()
                .content();
    }

    /**
     * AI 恋爱报告功能（实战结构化输出）
     *
     * @param message
     * @param chatId
     * @return
     */
    public LoveReport doChatWithReport(String message, String chatId) {
        LoveReport loveReport = chatClient
                .prompt()
                .system(SYSTEM_PROMPT + "请根据本轮及历史对话生成恋爱报告，标题为{用户名}的恋爱报告（若不知道用户名则用“你”代替），内容为 3-5 条具体可执行的建议列表")
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .entity(LoveReport.class);
        log.info("loveReport: {}", loveReport);
        return loveReport;
    }

    // AI 恋爱知识库问答功能

    @Resource
    private VectorStore loveAppVectorStore;

    @Resource
    private Advisor loveAppRagCloudAdvisor;

    @Resource
    private VectorStore pgVectorVectorStore;

    @Resource
    private QueryRewriter queryRewriter;

    @Resource
    private LoveAppDocumentLoader loveAppDocumentLoader;

    /**
     * 课程推荐链接缓存：课程名 -> 链接（懒加载，启动后首次课程相关提问时解析）
     */
    private volatile Map<String, String> courseLinks;

    /**
     * 判断消息是否涉及课程推荐场景
     */
    private boolean isCourseRelated(String message) {
        return message.contains("课程") || message.contains("学习")
                || message.contains("教程") || message.contains("推荐");
    }

    /**
     * 构造课程推荐约束提示：将知识库文档中的课程名与链接清单注入用户消息，
     * 强制模型按文档原文输出准确可访问的链接，杜绝编造网址
     */
    private String buildCoursePrompt(String message) {
        if (!isCourseRelated(message)) {
            return "";
        }
        Map<String, String> links = getCourseLinks();
        if (links.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n【可推荐的课程清单】（如用户需要课程推荐，只能从以下清单中选择课程，")
                .append("并逐字复制课程对应的链接作为 Markdown 链接输出：[《课程名》](链接)，禁止编造或修改链接）\n");
        links.forEach((name, url) -> sb.append("- 《").append(name).append("》：").append(url).append("\n"));
        return sb.toString();
    }

    private Map<String, String> getCourseLinks() {
        if (courseLinks == null) {
            synchronized (this) {
                if (courseLinks == null) {
                    courseLinks = loveAppDocumentLoader.loadCourseLinks();
                    log.info("已加载知识库课程推荐链接 {} 条", courseLinks.size());
                }
            }
        }
        return courseLinks;
    }

    /**
     * 和 RAG 知识库进行对话
     *
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithRag(String message, String chatId) {
        // 查询重写
        String rewrittenMessage = queryRewriter.doQueryRewrite(message);
        ChatResponse chatResponse = chatClient
                .prompt()
                // 使用改写后的查询，附加课程链接约束（涉及课程推荐时注入清单）
                .user(rewrittenMessage + buildCoursePrompt(rewrittenMessage))
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                // 应用 RAG 知识库问答（排除恋爱对象候选人文档，避免咨询时召回候选人资料）
                .advisors(QuestionAnswerAdvisor.builder(loveAppVectorStore)
                        .searchRequest(SearchRequest.builder()
                                .filterExpression("status != '对象'")
                                .build())
                        .build())
                // 应用 RAG 检索增强服务（基于云知识库服务）
//                .advisors(loveAppRagCloudAdvisor)
                // 应用 RAG 检索增强服务（基于 PgVector 向量存储）
//                .advisors(new QuestionAnswerAdvisor(pgVectorVectorStore))
                // 应用自定义的 RAG 检索增强服务（文档查询器 + 上下文增强器）
//                .advisors(
//                        LoveAppRagCustomAdvisorFactory.createLoveAppRagCustomAdvisor(
//                                loveAppVectorStore, "单身"
//                        )
//                )
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    /**
     * 和 RAG 知识库进行流式对话（SSE）
     *
     * @param message
     * @param chatId
     * @return
     */
    public Flux<String> doChatWithRagByStream(String message, String chatId) {
        // 查询重写，提高检索命中率
        String rewrittenMessage = queryRewriter.doQueryRewrite(message);
        return chatClient
                .prompt()
                // 使用改写后的查询，附加课程链接约束（涉及课程推荐时注入清单）
                .user(rewrittenMessage + buildCoursePrompt(rewrittenMessage))
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                // 应用 RAG 知识库检索增强（排除恋爱对象候选人文档）
                .advisors(QuestionAnswerAdvisor.builder(loveAppVectorStore)
                        .searchRequest(SearchRequest.builder()
                                .filterExpression("status != '对象'")
                                .build())
                        .build())
                .stream()
                .content();
    }

    // AI 混合检索 RAG 问答功能（向量语义检索 + 关键词全文检索，多数据源 RRF 融合）

    @Resource
    private Advisor loveAppHybridRagAdvisor;

    /**
     * 和 RAG 知识库进行混合检索对话（同步调用）
     * 基于 RetrievalAugmentationAdvisor：向量语义检索 + 关键词全文检索多路召回，
     * RRF 融合排序，并按元信息过滤掉恋爱对象候选人文档
     *
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithHybridRag(String message, String chatId) {
        // 查询重写，提高检索命中率
        String rewrittenMessage = queryRewriter.doQueryRewrite(message);
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(rewrittenMessage + buildCoursePrompt(rewrittenMessage))
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                // 开启日志，便于观察混合检索效果
                .advisors(new MyLoggerAdvisor())
                // 应用混合检索 RAG 查询增强顾问
                .advisors(loveAppHybridRagAdvisor)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("hybrid rag content: {}", content);
        return content;
    }

    /**
     * 和 RAG 知识库进行混合检索流式对话（SSE）
     *
     * @param message
     * @param chatId
     * @return
     */
    public Flux<String> doChatWithHybridRagByStream(String message, String chatId) {
        // 查询重写，提高检索命中率
        String rewrittenMessage = queryRewriter.doQueryRewrite(message);
        return chatClient
                .prompt()
                .user(rewrittenMessage + buildCoursePrompt(rewrittenMessage))
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                // 应用混合检索 RAG 查询增强顾问
                .advisors(loveAppHybridRagAdvisor)
                .stream()
                .content();
    }

    /**
     * 恋爱对象推荐（基于 RAG 候选人知识库，同步调用）
     *
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithLoveMatch(String message, String chatId) {
        return doChatWithLoveMatch(message, chatId, null);
    }

    /**
     * 恋爱对象推荐（基于 RAG 候选人知识库，同步调用，支持性别硬过滤）
     *
     * @param message
     * @param chatId
     * @param gender 期望对象性别（男/女），为空表示不限
     * @return
     */
    public String doChatWithLoveMatch(String message, String chatId, String gender) {
        // 查询重写，提高候选人检索命中率
        String rewrittenMessage = queryRewriter.doQueryRewrite(message);
        ChatResponse chatResponse = chatClient
                .prompt()
                // 红娘人设覆盖默认恋爱大师提示词
                .system(MATCH_SYSTEM_PROMPT)
                .user(rewrittenMessage)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                // 只检索 status=对象 的候选人文档（指定性别时叠加 gender 硬过滤）
                .advisors(LoveAppRagCustomAdvisorFactory.createLoveMatchAdvisor(loveAppVectorStore, gender))
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("match content: {}", content);
        return content;
    }

    /**
     * 恋爱对象推荐（基于 RAG 候选人知识库，SSE 流式传输）
     *
     * @param message
     * @param chatId
     * @return
     */
    public Flux<String> doChatWithLoveMatchByStream(String message, String chatId) {
        return doChatWithLoveMatchByStream(message, chatId, null);
    }

    /**
     * 恋爱对象推荐（基于 RAG 候选人知识库，SSE 流式传输，支持性别硬过滤）
     *
     * @param message
     * @param chatId
     * @param gender 期望对象性别（男/女），为空表示不限
     * @return
     */
    public Flux<String> doChatWithLoveMatchByStream(String message, String chatId, String gender) {
        // 查询重写，提高候选人检索命中率
        String rewrittenMessage = queryRewriter.doQueryRewrite(message);
        return chatClient
                .prompt()
                // 红娘人设覆盖默认恋爱大师提示词
                .system(MATCH_SYSTEM_PROMPT)
                .user(rewrittenMessage)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                // 只检索 status=对象 的候选人文档（指定性别时叠加 gender 硬过滤）
                .advisors(LoveAppRagCustomAdvisorFactory.createLoveMatchAdvisor(loveAppVectorStore, gender))
                .stream()
                .content();
    }

    // AI 调用工具能力
    @Resource
    private ToolCallback[] allTools;

    /**
     * AI 恋爱报告功能（支持调用工具，手动控制工具执行流程）
     * 关闭 Spring AI 内置的工具执行，使用 ToolCallingManager 显式执行工具调用，
     * 在工具执行前后记录详细日志（工具名称、参数、耗时、结果），提高应用可观测性；
     * 若工具声明 returnDirect = true（如 PDF 生成工具），工具结果直接返回用户，避免额外调用一次 AI 模型
     *
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithTools(String message, String chatId) {
        long chatStart = System.currentTimeMillis();
        // 手动组装上下文：系统提示词 + 历史对话记忆 + 当前用户消息
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        messages.addAll(chatMemory.get(chatId));
        UserMessage userMessage = new UserMessage(message);
        messages.add(userMessage);
        chatMemory.add(chatId, userMessage);
        // 关闭 Spring AI 内置工具执行，由 ToolCallingManager 手动控制工具执行流程
        ChatOptions chatOptions = ToolCallingChatOptions.builder()
                .toolCallbacks(allTools)
                .internalToolExecutionEnabled(false)
                .build();
        Prompt prompt = new Prompt(messages, chatOptions);
        ChatResponse chatResponse = chatModel.call(prompt);
        // 循环执行工具调用，直到模型不再请求工具或工具声明 returnDirect
        int round = 0;
        while (chatResponse.hasToolCalls()) {
            round++;
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            // 记录本轮模型决策的工具调用详情（工具名称 + 参数）
            for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {
                log.info("[工具执行] chatId: {}, 第 {} 轮, 准备调用工具: {}, 参数: {}",
                        chatId, round, toolCall.name(), toolCall.arguments());
            }
            // 显式执行工具调用，并统计执行耗时
            long toolStart = System.currentTimeMillis();
            ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, chatResponse);
            long toolCost = System.currentTimeMillis() - toolStart;
            ToolResponseMessage toolResponseMessage =
                    (ToolResponseMessage) CollUtil.getLast(toolExecutionResult.conversationHistory());
            toolResponseMessage.getResponses().forEach(response ->
                    log.info("[工具执行] chatId: {}, 工具 {} 执行完成, 耗时: {} ms, 结果: {}",
                            chatId, response.name(), toolCost, response.responseData()));
            // 工具声明 returnDirect = true：工具结果直接作为最终回复，不再回传模型
            if (toolExecutionResult.returnDirect()) {
                String directResult = toolResponseMessage.getResponses().stream()
                        .map(ToolResponseMessage.ToolResponse::responseData)
                        .collect(Collectors.joining("\n"));
                AssistantMessage directAssistantMessage = new AssistantMessage(directResult);
                chatMemory.add(chatId, directAssistantMessage);
                log.info("[工具执行] chatId: {}, 工具声明 returnDirect=true, 直接返回工具结果, 共 {} 轮工具调用, 总耗时: {} ms",
                        chatId, round, System.currentTimeMillis() - chatStart);
                return directResult;
            }
            // 将工具执行结果回喂给模型，继续下一轮推理
            prompt = new Prompt(toolExecutionResult.conversationHistory(), chatOptions);
            chatResponse = chatModel.call(prompt);
        }
        AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
        String content = assistantMessage.getText();
        chatMemory.add(chatId, assistantMessage);
        log.info("[工具执行] chatId: {}, 对话完成, 共 {} 轮工具调用, 总耗时: {} ms",
                chatId, round, System.currentTimeMillis() - chatStart);
        log.info("content: {}", content);
        return content;
    }

    // AI 调用 MCP 服务

    @Resource
    private ToolCallbackProvider toolCallbackProvider;

    /**
     * AI 恋爱报告功能（调用 MCP 服务）
     *
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithMcp(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(toolCallbackProvider)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }
}
