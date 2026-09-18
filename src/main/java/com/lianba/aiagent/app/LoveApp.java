package com.lianba.aiagent.app;

import cn.hutool.core.collection.CollUtil;
import com.lianba.aiagent.advisor.MyLoggerAdvisor;
import com.lianba.aiagent.advisor.ReReadingAdvisor;
import com.lianba.aiagent.app.model.LoveReport;
import com.lianba.aiagent.chatmemory.DbBasedChatMemoryRepository;
import com.lianba.aiagent.mapper.ChatMemoryMapper;
import com.lianba.aiagent.mapper.ChatSessionMapper;
import com.lianba.aiagent.model.entity.ChatSession;
import com.lianba.aiagent.model.entity.User;
import com.lianba.aiagent.rag.LoveAppContextualQueryAugmenterFactory;
import com.lianba.aiagent.rag.LoveAppDocumentLoader;
import com.lianba.aiagent.rag.LoveAppRagCustomAdvisorFactory;
import com.lianba.aiagent.rag.QueryRewriter;
import com.lianba.aiagent.service.DeepSeekChatService;
import com.lianba.aiagent.service.UsageStatisticsService;
import com.lianba.aiagent.service.UserService;
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
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Slf4j
public class LoveApp {

    private final ChatClient chatClient;

    private final ChatModel chatModel;

    private final ChatMemory chatMemory;

    // 工具调用管理器：用于手动控制工具执行流程，提升可观测性
    private final ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();

    @Resource
    private ChatSessionMapper chatSessionMapper;

    @Resource
    private UserService userService;

    @Resource
    private UsageStatisticsService usageStatisticsService;

    // DeepSeek 服务：图文多模态理解（DeepSeek-Flash）+ 深度思考推理（thinking 模式）
    @Resource
    private DeepSeekChatService deepSeekChatService;

    private static final String SYSTEM_PROMPT = """
            你是深耕恋爱心理领域的专家，同时也是一位贴心的生活助手。
            开场向用户表明身份，告知用户可倾诉恋爱难题，开场白中不要推荐课程。
            围绕单身、恋爱、已婚三种状态提问：
            - 单身状态询问社交圈拓展及追求心仪对象的困扰；
            - 恋爱状态询问沟通、习惯差异引发的矛盾；
            - 已婚状态询问家庭责任与亲属关系处理的问题。
            引导用户详述事情经过、对方反应及自身想法，以便给出专属解决方案。
            不要提及“知识库”“检索”“参考资料”“文档”等字眼，直接自然地给出建议。
                
            除了情感咨询，你还可以帮用户处理恋爱生活中的实际任务。当用户提出以下类型的需求时，请主动调用相应工具：
            - 约会规划（如“帮我做一份约会计划”）：调用搜索和 PDF 生成工具
            - 图片创作（如“画一张情侣头像”“帮我把这张照片调成暖色调”）：调用图片生成工具
            - 信息检索（如“附近有什么好的餐厅”）：调用联网搜索工具
            - 文档处理（如“帮我写一封信”）：调用文件操作和邮件工具
                
            使用工具时注意：
            - 用温暖、有同理心的语气包装工具结果，不要机械地返回原始数据
            - 生成图片时，根据用户描述构造详细的中文或英文 prompt
            - 如果用户上传图片并想修改，将图片 URL 作为 referenceImageUrl 参数传入
            - 生成 PDF 或文件后，用友好的语言告知用户并引导下载
                
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
                // 应用混合检索 RAG 增强（向量语义 + 关键词全文，RRF 融合排序）
                .advisors(loveAppHybridRagAdvisor)
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

    // ========== 工具调用流式对话（融合超级智能体工具能力） ==========

    @Resource
    private Executor agentTaskExecutor;

    /**
     * 带工具调用的流式对话（SSE），融合超级智能体工具能力到恋爱大师。
     * 手动控制工具执行流程，通过 SSE 实时推送工具调用步骤和结果给前端。
     * 支持可选的图片 URL 列表（图片编辑/理解场景）。
     *
     * @param message    用户消息
     * @param chatId     会话 ID
     * @param imageUrls  用户上传的图片 URL 列表（可为 null）
     * @return SseEmitter 流式响应
     */
    public SseEmitter doChatWithToolsStream(String message, String chatId, List<String> imageUrls) {
        return doChatWithToolsStream(message, chatId, imageUrls, false, false);
    }

    /**
     * 带工具调用的流式对话（SSE，支持深度思考/联网搜索开关）。
     * - deepThink=true：先调用 DeepSeek deepseek-flash 模型（thinking 模式）流式输出推理过程（thinking 事件），再进入主对话链路
     * - webSearch=true：注入联网搜索指令，引导模型优先调用 searchWeb 工具检索最新信息
     * - 含图片时：调用 DeepSeek-Flash 多模态模型直接理解图文内容（替代原 MIMO 视觉服务）
     *
     * @param message    用户消息
     * @param chatId     会话 ID
     * @param imageUrls  用户上传的图片 URL 列表（可为 null）
     * @param deepThink  是否开启深度思考
     * @param webSearch  是否开启联网搜索
     * @return SseEmitter 流式响应
     */
    public SseEmitter doChatWithToolsStream(String message, String chatId, List<String> imageUrls,
                                            boolean deepThink, boolean webSearch) {
        SseEmitter sseEmitter = new SseEmitter(300000L);

        CompletableFuture.runAsync(() -> {
            try {
                // 1. 构建消息上下文：个性化系统提示词 + 历史对话记忆 + 当前用户消息
                List<Message> messages = new ArrayList<>();
                messages.add(new SystemMessage(buildPersonalizedSystemPrompt(chatId)));
                messages.addAll(chatMemory.get(chatId));

                // 构建用户消息（如有图片则交给 DeepSeek-Flash 多模态理解，并附加 URL 上下文供图生图引用）
                String userText = message;
                if (imageUrls != null && !imageUrls.isEmpty()) {
                    // DeepSeek-Flash 直接处理图文请求：结合用户问题对图片进行多模态理解
                    String visionPrompt = "用户上传了图片并提问：" + message
                            + "\n请先详细描述图片内容（场景、人物、物体、文字、氛围等），再结合用户问题给出针对性回答。";
                    String visionResult = deepSeekChatService.chatWithImages(visionPrompt, imageUrls);
                    if (visionResult != null && !visionResult.isBlank()) {
                        userText += "\n\n[DeepSeek 图片理解与图文分析结果]\n" + visionResult;
                    }
                    userText += "\n\n[用户上传的图片地址]\n" + String.join("\n", imageUrls)
                            + "\n（如果用户要求基于上传图片修改/重绘/生成新图，请调用 generateImage 工具并将上述图片地址作为 referenceImageUrl 参数传入）";
                }

                // 联网搜索开关：注入指令引导模型优先调用 searchWeb 工具
                String modelInputText = userText;
                if (webSearch) {
                    modelInputText += "\n\n[系统指令] 用户已开启联网搜索，请先调用 searchWeb 工具检索与用户问题相关的最新网络信息，再结合搜索结果回答；若问题与时效信息无关，可简要检索后直接回答。";
                }
                UserMessage userMessage = new UserMessage(modelInputText);
                messages.add(userMessage);
                // 记忆保存原始消息（不含注入的系统指令与图片理解内容，避免污染历史上下文）
                chatMemory.add(chatId, new UserMessage(message));

                // 2. 深度思考：调用 deepseek-flash（thinking 模式）流式推送推理过程，推理结论作为参考注入主对话
                if (deepThink) {
                    sendSse(sseEmitter, buildStatusJson("deep_thinking", "正在深度思考中..."));
                    String reasoning = deepSeekChatService.streamDeepThink(userText,
                            chunk -> sendSse(sseEmitter, buildThinkingJson(chunk)));
                    if (reasoning != null && !reasoning.isBlank()) {
                        // 将推理分析结论注入主模型上下文，提升最终回答质量
                        String analysis = reasoning.length() > 2000 ? reasoning.substring(reasoning.length() - 2000) : reasoning;
                        messages.add(new SystemMessage("[深度思考分析参考]（请在回答中吸收以下分析结论，但不要把分析过程原文展示给用户）\n" + analysis));
                    }
                }

                // 3. 通知前端：正在思考
                sendSse(sseEmitter, buildStatusJson("thinking", "正在思考中..."));

                // 4. 调用模型（禁用内置工具执行，手动控制流程）
                ChatOptions chatOptions = ToolCallingChatOptions.builder()
                        .toolCallbacks(allTools)
                        .internalToolExecutionEnabled(false)
                        .build();
                Prompt prompt = new Prompt(messages, chatOptions);
                // 首轮调用改为流式：文本分片实时推送前端（AI 回答逐字输出）
                ChatResponse chatResponse = callModelStreaming(prompt, 0, sseEmitter);
                // 首轮即返回纯文本（无工具调用）时，最终回复已流式推送，无需重复发送
                boolean finalTextStreamed = !chatResponse.hasToolCalls();

                // 5. 工具调用循环
                int round = 0;
                List<String[]> generatedFiles = new ArrayList<>();
                while (chatResponse.hasToolCalls()) {
                    round++;
                    AssistantMessage assistantMessage = chatResponse.getResult().getOutput();

                    // 发送工具调用信息
                    for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {
                        sendSse(sseEmitter, buildToolCallJson(round, toolCall.name(), toolCall.arguments()));
                    }

                    // 发送状态：正在执行工具
                    if (!assistantMessage.getToolCalls().isEmpty()) {
                        String firstTool = assistantMessage.getToolCalls().get(0).name();
                        sendSse(sseEmitter, buildStatusJson("executing_tool", getToolStatusMessage(firstTool)));
                    }

                    // 执行工具
                    long toolStart = System.currentTimeMillis();
                    ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, chatResponse);
                    long toolCost = System.currentTimeMillis() - toolStart;

                    ToolResponseMessage toolResponseMessage =
                            (ToolResponseMessage) CollUtil.getLast(toolExecutionResult.conversationHistory());

                    // 发送工具执行结果
                    for (ToolResponseMessage.ToolResponse response : toolResponseMessage.getResponses()) {
                        log.info("[工具执行] chatId: {}, 工具 {} 执行完成, 耗时: {} ms", chatId, response.name(), toolCost);
                        sendSse(sseEmitter, buildToolResultJson(round, response.name(), response.responseData()));
                        // 收集生成的文件
                        collectGeneratedFile(response.name(), response.responseData(), generatedFiles);
                        // 如果生成了图片，发送图片预览事件
                        if ("generateImage".equals(response.name())) {
                            sendGeneratedImageIfPresent(response.responseData(), sseEmitter);
                        }
                    }

                    // returnDirect = true：工具结果直接返回，不再调用模型
                    if (toolExecutionResult.returnDirect()) {
                        String directResult = toolResponseMessage.getResponses().stream()
                                .map(ToolResponseMessage.ToolResponse::responseData)
                                .collect(Collectors.joining("\n"));
                        AssistantMessage directMsg = new AssistantMessage(directResult);
                        chatMemory.add(chatId, directMsg);
                        // 发送文件列表
                        if (!generatedFiles.isEmpty()) {
                            sendSse(sseEmitter, buildFilesJson(generatedFiles));
                        }
                        sendSse(sseEmitter, "[DONE]");
                        sseEmitter.complete();
                        return;
                    }

                    // 将工具结果回喂模型，继续下一轮推理
                    prompt = new Prompt(toolExecutionResult.conversationHistory(), chatOptions);
                    chatResponse = chatModel.call(prompt);
                }

                // 6. 模型不再调用工具，处理最终回复（首轮流式调用时文本已实时推送，此处仅发送工具轮后的完整回复）
                AssistantMessage finalMessage = chatResponse.getResult().getOutput();
                String content = finalMessage.getText();
                chatMemory.add(chatId, finalMessage);
                log.info("[工具对话] chatId: {}, 共 {} 轮工具调用, 最终回复长度: {}", chatId, round, content != null ? content.length() : 0);

                if (!finalTextStreamed && content != null && !content.isEmpty()) {
                    sendSse(sseEmitter, buildTextJson(round, content));
                }

                // 发送生成的文件列表
                if (!generatedFiles.isEmpty()) {
                    sendSse(sseEmitter, buildFilesJson(generatedFiles));
                }

                // 记录用量（chat_message）
                Long userId = resolveUserIdFromChatId(chatId);
                if (userId != null) {
                    usageStatisticsService.recordUsage(userId, "chat_message", "dashscope", 0);
                    if (round > 0) {
                        usageStatisticsService.recordUsage(userId, "tool_call", "dashscope", 0);
                    }
                }

                sendSse(sseEmitter, "[DONE]");
                sseEmitter.complete();
            } catch (Exception e) {
                log.error("[工具对话] chatId: {} 异常", chatId, e);
                try {
                    sendSse(sseEmitter, buildTextJson(0, "抱歉，处理过程中出现了异常：" + e.getMessage()));
                    sendSse(sseEmitter, "[DONE]");
                    sseEmitter.complete();
                } catch (Exception ex) {
                    sseEmitter.completeWithError(ex);
                }
            }
        }, agentTaskExecutor);

        sseEmitter.onTimeout(() -> {
            log.warn("[工具对话] chatId: {} SSE 连接超时", chatId);
            sseEmitter.complete();
        });
        sseEmitter.onCompletion(() -> log.info("[工具对话] chatId: {} SSE 连接完成", chatId));

        return sseEmitter;
    }

    /**
     * 流式调用模型：文本分片实时推送前端（AI 回答逐字输出），并聚合为完整 ChatResponse 返回。
     * 若响应包含工具调用（流式分片按增量拼接聚合），交由上层工具循环处理。
     *
     * @param prompt     请求
     * @param round      当前工具调用轮次（用于文本消息的 step 标识）
     * @param sseEmitter SSE 推送器
     * @return 聚合后的完整 ChatResponse
     */
    private ChatResponse callModelStreaming(Prompt prompt, int round, SseEmitter sseEmitter) {
        StringBuilder textBuilder = new StringBuilder();
        // 工具调用分片聚合：id 非空按 id 归并；id 为空时带 name 视为新调用，否则并入最后一个调用
        List<String> toolIds = new ArrayList<>();
        List<String> toolNames = new ArrayList<>();
        List<StringBuilder> toolArgs = new ArrayList<>();

        // doOnNext：每个文本分片到达时立即推送 SSE（流式逐字输出），不能先 collectList 再遍历（会等全部生成完才发）
        chatModel.stream(prompt)
                .doOnNext(chunk -> {
                    if (chunk == null || chunk.getResult() == null || chunk.getResult().getOutput() == null) {
                        return;
                    }
                    AssistantMessage output = chunk.getResult().getOutput();
                    String text = output.getText();
                    if (text != null && !text.isEmpty()) {
                        textBuilder.append(text);
                        // 文本分片实时推送（工具调用响应一般无文本）
                        sendSse(sseEmitter, buildTextJson(round, text));
                    }
                    for (AssistantMessage.ToolCall toolCall : output.getToolCalls()) {
                        String id = toolCall.id();
                        int idx;
                        if (id != null && !id.isEmpty()) {
                            idx = toolIds.indexOf(id);
                        } else if (toolCall.name() != null && !toolCall.name().isEmpty()) {
                            idx = -1;
                        } else {
                            idx = toolNames.size() - 1;
                        }
                        if (idx < 0) {
                            toolIds.add(id == null ? "" : id);
                            toolNames.add(toolCall.name() == null ? "" : toolCall.name());
                            toolArgs.add(new StringBuilder(toolCall.arguments() == null ? "" : toolCall.arguments()));
                        } else {
                            if (toolNames.get(idx).isEmpty() && toolCall.name() != null && !toolCall.name().isEmpty()) {
                                toolNames.set(idx, toolCall.name());
                            }
                            if (toolCall.arguments() != null) {
                                toolArgs.get(idx).append(toolCall.arguments());
                            }
                        }
                    }
                })
                .blockLast();

        List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
        for (int i = 0; i < toolNames.size(); i++) {
            if (!toolNames.get(i).isEmpty()) {
                toolCalls.add(new AssistantMessage.ToolCall(toolIds.get(i), "function", toolNames.get(i), toolArgs.get(i).toString()));
            }
        }
        AssistantMessage aggregatedMessage = new AssistantMessage(textBuilder.toString(), Map.of(), toolCalls);
        return new ChatResponse(List.of(new Generation(aggregatedMessage)));
    }

    /**
     * 通过 chatId 反查 userId
     */
    private Long resolveUserIdFromChatId(String chatId) {
        try {
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatSession> wrapper =
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            wrapper.eq(ChatSession::getSessionId, chatId).last("LIMIT 1");
            ChatSession session = chatSessionMapper.selectOne(wrapper);
            return session != null ? session.getUserId() : null;
        } catch (Exception e) {
            log.warn("通过 chatId 反查 userId 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 根据用户画像构建个性化系统提示词。
     * 通过 chatId 反查会话 -> 用户 -> 情感状态，在基础 Prompt 后追加个性化段落。
     * 查询失败时回退到基础 SYSTEM_PROMPT。
     */
    private String buildPersonalizedSystemPrompt(String chatId) {
        try {
            // 通过 chatId 查询会话获取 userId
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatSession> wrapper =
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            wrapper.eq(ChatSession::getSessionId, chatId).last("LIMIT 1");
            ChatSession session = chatSessionMapper.selectOne(wrapper);
            if (session == null || session.getUserId() == null) {
                return SYSTEM_PROMPT;
            }
            // 查询用户画像
            User user = userService.getUserById(session.getUserId());
            if (user == null || user.getRelationshipStatus() == null || user.getRelationshipStatus().isBlank()) {
                return SYSTEM_PROMPT;
            }
            // 根据情感状态追加个性化段落
            String status = user.getRelationshipStatus();
            String personalization = switch (status) {
                case "single" -> """

                    【用户画像】该用户当前处于单身状态。
                    请重点关注：社交圈拓展、追求心仪对象的策略、自我提升与吸引力建设。
                    开场时侧重询问是否有喜欢的人、社交圈情况，语气轻松鼓励。
                    """;
                case "dating" -> """

                    【用户画像】该用户当前处于恋爱状态。
                    请重点关注：沟通技巧、习惯差异磨合、感情升温与信任建立。
                    开场时侧重询问恋爱中的困惑或矛盾，语气温柔有同理心。
                    """;
                case "married" -> """

                    【用户画像】该用户当前处于已婚状态。
                    请重点关注：家庭责任分工、婆媳/翁婿关系、婚姻生活保鲜。
                    开场时侧重询问家庭生活中的烦恼，语气成熟稳重、理解包容。
                    """;
                default -> "";
            };
            return SYSTEM_PROMPT + personalization;
        } catch (Exception e) {
            log.warn("构建个性化 Prompt 失败，回退到基础 Prompt: {}", e.getMessage());
            return SYSTEM_PROMPT;
        }
    }

    /**
     * 安全发送 SSE 消息
     */
    private void sendSse(SseEmitter sseEmitter, String data) {
        try {
            sseEmitter.send(data);
        } catch (IOException e) {
            log.warn("SSE 发送失败", e);
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String buildStatusJson(String status, String message) {
        return "{\"type\":\"status\",\"status\":\"" + escapeJson(status)
                + "\",\"message\":\"" + escapeJson(message) + "\"}";
    }

    private String buildToolCallJson(int step, String toolName, String arguments) {
        return "{\"type\":\"tool_call\",\"step\":" + step
                + ",\"toolName\":\"" + escapeJson(toolName) + "\""
                + ",\"arguments\":\"" + escapeJson(arguments) + "\"}";
    }

    private String buildToolResultJson(int step, String toolName, String result) {
        return "{\"type\":\"tool_result\",\"step\":" + step
                + ",\"toolName\":\"" + escapeJson(toolName) + "\""
                + ",\"result\":\"" + escapeJson(result) + "\"}";
    }

    private String buildTextJson(int step, String content) {
        return "{\"type\":\"text\",\"step\":" + step
                + ",\"content\":\"" + escapeJson(content) + "\"}";
    }

    /**
     * 构建深度思考推理内容事件（前端以可折叠思考卡片展示）
     */
    private String buildThinkingJson(String content) {
        return "{\"type\":\"thinking\",\"content\":\"" + escapeJson(content) + "\"}";
    }

    private String buildFilesJson(List<String[]> files) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"files\",\"files\":[");
        for (int i = 0; i < files.size(); i++) {
            String[] f = files.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"name\":\"").append(escapeJson(f[0])).append("\",\"")
                    .append("url\":\"").append(escapeJson(f[1])).append("\",\"")
                    .append("type\":\"").append(escapeJson(f[2])).append("\"}");
        }
        sb.append("]}");
        return sb.toString();
    }

    /**
     * 从工具执行结果中识别生成的可下载文件并收集
     */
    private void collectGeneratedFile(String toolName, String result, List<String[]> generatedFiles) {
        if (toolName == null || result == null) return;
        String name = null, url = null, type = null;
        if ("generatePDF".equals(toolName) && result.contains("PDF generated successfully")) {
            Matcher m = Pattern.compile("\\[Download: ([^\\s\\]]+)\\]").matcher(result);
            if (m.find()) {
                url = m.group(1);
                name = url.replaceAll("^.*/", "");
                int qi = name.indexOf('?');
                if (qi >= 0) name = name.substring(0, qi);
                type = "pdf";
            }
        } else if ("writeFile".equals(toolName) && result.contains("File written successfully")) {
            Matcher m = Pattern.compile("File written successfully to:\\s*(.+)").matcher(result);
            if (m.find()) {
                name = m.group(1).trim().replaceAll("^.*[\\\\/]", "");
                url = "/api/files/download/file/" + name;
                type = "file";
            }
        }
        if (name != null && url != null) {
            for (String[] existing : generatedFiles) {
                if (existing[1].equals(url)) return;
            }
            generatedFiles.add(new String[]{name, url, type});
        }
    }

    /**
     * 从 generateImage 工具结果中提取图片 URL 并发送 SSE 预览事件
     */
    private void sendGeneratedImageIfPresent(String result, SseEmitter sseEmitter) {
        String imageUrl = null;
        Matcher mLocal = Pattern.compile("下载地址：(/api/files/download/image/[^\\s\\\\\"'`]+)").matcher(result);
        if (mLocal.find()) {
            imageUrl = stripTrailingChars(mLocal.group(1));
        } else {
            Matcher m = Pattern.compile("下载地址：(https?://[^\\s\\\\\"'`]+)").matcher(result);
            if (m.find()) imageUrl = stripTrailingChars(m.group(1));
        }
        if (imageUrl != null) {
            sendSse(sseEmitter, "{\"type\":\"generated_image\",\"url\":\"" + escapeJson(imageUrl) + "\"}");
        }
    }

    private String stripTrailingChars(String url) {
        if (url == null) return null;
        while (!url.isEmpty()) {
            char last = url.charAt(url.length() - 1);
            if (last == '"' || last == '\'' || last == '`' || last == ')' || last == ']'
                    || last == '}' || last == ',' || last == ';' || last == '。' || last == '.') {
                url = url.substring(0, url.length() - 1);
            } else break;
        }
        return url;
    }

    private String getToolStatusMessage(String toolName) {
        return switch (toolName) {
            case "generateImage" -> "正在生成图片...";
            case "searchWeb" -> "正在搜索网页...";
            case "webScraping" -> "正在抓取网页内容...";
            case "generatePDF" -> "正在生成 PDF 文件...";
            case "writeFile" -> "正在写入文件...";
            case "sendEmail" -> "正在发送邮件...";
            default -> "正在执行 " + toolName + "...";
        };
    }
}
