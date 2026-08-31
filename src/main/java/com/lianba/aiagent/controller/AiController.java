package com.lianba.aiagent.controller;

import com.lianba.aiagent.agent.Manus;
import com.lianba.aiagent.agent.interaction.HumanInteractionRegistry;
import com.lianba.aiagent.agent.model.AgentTask;
import com.lianba.aiagent.app.LoveApp;
import com.lianba.aiagent.app.model.LoveReport;
import com.lianba.aiagent.common.BaseResponse;
import com.lianba.aiagent.common.ResultUtils;
import com.lianba.aiagent.exception.ErrorCode;
import com.lianba.aiagent.exception.ThrowUtils;
import com.lianba.aiagent.model.vo.LoginUserVO;
import com.lianba.aiagent.service.AgentTaskService;
import com.lianba.aiagent.service.LoveReportExportService;
import com.lianba.aiagent.service.MiMoVisionService;
import com.lianba.aiagent.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ai")
public class AiController {

    @Resource
    private LoveApp loveApp;

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ChatModel dashscopeChatModel;

    @Resource
    private MiMoVisionService miMoVisionService;

    @Resource
    private LoveReportExportService loveReportExportService;

    @Resource
    private UserService userService;

    @Resource
    private AgentTaskService agentTaskService;

    @Resource
    @Qualifier("agentTaskExecutor")
    private ThreadPoolTaskExecutor agentTaskExecutor;

    /**
     * 同步调用 AI 恋爱大师应用
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping("/love_app/chat/sync")
    public String doChatWithLoveAppSync(String message, String chatId) {
        return loveApp.doChat(message, chatId);
    }

    /**
     * SSE 流式调用 AI 恋爱大师应用
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping(value = "/love_app/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithLoveAppSSE(String message, String chatId) {
        return loveApp.doChatByStream(message, chatId);
    }

    /**
     * SSE 流式调用 AI 恋爱大师应用（集成 RAG 知识库检索增强）
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping(value = "/love_app/chat/rag_sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithLoveAppRagSSE(String message, String chatId) {
        return loveApp.doChatWithRagByStream(message, chatId);
    }

    /**
     * 同步调用 AI 恋爱大师应用（混合检索 RAG：向量语义检索 + 关键词全文检索，多数据源 RRF 融合）
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping("/love_app/chat/hybrid_rag/sync")
    public String doChatWithLoveAppHybridRagSync(String message, String chatId) {
        return loveApp.doChatWithHybridRag(message, chatId);
    }

    /**
     * SSE 流式调用 AI 恋爱大师应用（混合检索 RAG：向量语义检索 + 关键词全文检索，多数据源 RRF 融合）
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping(value = "/love_app/chat/hybrid_rag_sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithLoveAppHybridRagSSE(String message, String chatId) {
        return loveApp.doChatWithHybridRagByStream(message, chatId);
    }

    /**
     * 同步调用恋爱对象推荐（基于 RAG 候选人知识库）
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping("/love_app/match/sync")
    public String doChatWithLoveAppMatchSync(String message, String chatId,
                                              @RequestParam(required = false) String gender) {
        return loveApp.doChatWithLoveMatch(message, chatId, gender);
    }

    /**
     * SSE 流式调用恋爱对象推荐（基于 RAG 候选人知识库）
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping(value = "/love_app/match/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithLoveAppMatchSSE(String message, String chatId,
                                                  @RequestParam(required = false) String gender) {
        return loveApp.doChatWithLoveMatchByStream(message, chatId, gender);
    }

    /**
     * 生成恋爱报告（结构化输出，同步调用）
     * 基于当前会话的历史对话生成报告，返回 {title, suggestions} JSON
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping("/love_app/chat/report")
    public LoveReport doChatWithLoveAppReport(String message, String chatId) {
        return loveApp.doChatWithReport(message, chatId);
    }

    /**
     * 导出恋爱报告为可下载文件（前端传入已生成的报告内容，不重复调用 AI）
     *
     * @param format 导出格式：pdf / word / md
     * @param report 报告内容 {title, suggestions}
     * @return 文件字节流（Content-Disposition attachment）
     */
    @PostMapping("/love_app/report/export")
    public ResponseEntity<byte[]> exportLoveReport(@RequestParam String format, @RequestBody LoveReport report) throws IOException {
        byte[] data;
        String extension;
        MediaType mediaType;
        switch (format == null ? "" : format.toLowerCase()) {
            case "pdf" -> {
                data = loveReportExportService.exportToPdf(report);
                extension = ".pdf";
                mediaType = MediaType.APPLICATION_PDF;
            }
            case "word" -> {
                data = loveReportExportService.exportToWord(report);
                extension = ".doc";
                mediaType = MediaType.parseMediaType("application/msword");
            }
            case "md" -> {
                data = loveReportExportService.exportToMarkdown(report);
                extension = ".md";
                mediaType = MediaType.parseMediaType("text/markdown;charset=UTF-8");
            }
            default -> {
                return ResponseEntity.badRequest().build();
            }
        }
        // 文件名取报告标题，中文用 RFC 5987 编码，兼容各浏览器下载
        String title = (report != null && report.title() != null && !report.title().isBlank()) ? report.title() : "恋爱报告";
        String encodedName = URLEncoder.encode(title + extension, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .contentType(mediaType)
                .body(data);
    }

    /**
     * 导出通用会话记录为可下载文件（前端传入会话标题与消息列表，不重复调用 AI）
     * 与恋爱报告导出共享同一套 PDF / Word / Markdown 生成能力，兼容聊天记录系统的会话数据
     *
     * @param format 导出格式：pdf / word / md
     * @param body   JSON body：{title, messages: [{role, content}]}
     * @return 文件字节流（Content-Disposition attachment）
     */
    @PostMapping("/export/chat")
    public ResponseEntity<byte[]> exportChat(@RequestParam String format, @RequestBody Map<String, Object> body) throws IOException {
        String title = (String) body.getOrDefault("title", "对话记录");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawMessages = (List<Map<String, Object>>) body.getOrDefault("messages", List.of());
        List<LoveReportExportService.ChatMessage> messages = rawMessages.stream()
                .map(m -> new LoveReportExportService.ChatMessage(
                        String.valueOf(m.getOrDefault("role", "ai")),
                        String.valueOf(m.getOrDefault("content", ""))))
                .toList();

        byte[] data;
        String extension;
        MediaType mediaType;
        switch (format == null ? "" : format.toLowerCase()) {
            case "pdf" -> {
                data = loveReportExportService.exportChatToPdf(title, messages);
                extension = ".pdf";
                mediaType = MediaType.APPLICATION_PDF;
            }
            case "word" -> {
                data = loveReportExportService.exportChatToWord(title, messages);
                extension = ".doc";
                mediaType = MediaType.parseMediaType("application/msword");
            }
            case "md" -> {
                data = loveReportExportService.exportChatToMarkdown(title, messages);
                extension = ".md";
                mediaType = MediaType.parseMediaType("text/markdown;charset=UTF-8");
            }
            default -> {
                return ResponseEntity.badRequest().build();
            }
        }
        String safeTitle = (title == null || title.isBlank()) ? "对话记录" : title;
        String encodedName = URLEncoder.encode(safeTitle + extension, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .contentType(mediaType)
                .body(data);
    }

    /**
     * SSE 流式调用 AI 恋爱大师应用
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping(value = "/love_app/chat/server_sent_event")
    public Flux<ServerSentEvent<String>> doChatWithLoveAppServerSentEvent(String message, String chatId) {
        return loveApp.doChatByStream(message, chatId)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build());
    }

    /**
     * SSE 流式调用 AI 恋爱大师应用
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping(value = "/love_app/chat/sse_emitter")
    public SseEmitter doChatWithLoveAppServerSseEmitter(String message, String chatId) {
        // 创建一个超时时间较长的 SseEmitter
        SseEmitter sseEmitter = new SseEmitter(180000L); // 3 分钟超时
        // 获取 Flux 响应式数据流并且直接通过订阅推送给 SseEmitter
        loveApp.doChatByStream(message, chatId)
                .subscribe(chunk -> {
                    try {
                        sseEmitter.send(chunk);
                    } catch (IOException e) {
                        sseEmitter.completeWithError(e);
                    }
                }, sseEmitter::completeWithError, sseEmitter::complete);
        // 返回
        return sseEmitter;
    }

    /**
     * 流式调用 Manus 超级智能体（任务异步化：先登记任务再执行，支持手动停止与状态查询）
     *
     * @param message
     * @return
     */
    @GetMapping("/manus/chat")
    public SseEmitter doChatWithManus(String message, HttpServletRequest request) {
        Manus Manus = new Manus(allTools, dashscopeChatModel);
        prepareManusTask(Manus, message, request);
        return Manus.runStream(message);
    }

    /**
     * 手动停止智能体任务（配合前端停止按钮中断 SSE 输出）
     *
     * @param taskId 任务 ID（由 SSE task_id 事件下发）
     * @return 是否成功发出停止请求（任务已结束时返回 false）
     */
    @PostMapping("/manus/stop")
    public BaseResponse<Boolean> stopManusTask(@RequestParam String taskId, HttpServletRequest request) {
        LoginUserVO loginUser = userService.getLoginUser(request);
        boolean stopped = agentTaskService.stopTask(taskId, loginUser.getId());
        return ResultUtils.success(stopped);
    }

    /**
     * 查询单个智能体任务状态
     */
    @GetMapping("/manus/task")
    public BaseResponse<AgentTask> getManusTask(@RequestParam String taskId, HttpServletRequest request) {
        LoginUserVO loginUser = userService.getLoginUser(request);
        AgentTask task = agentTaskService.getTask(taskId);
        ThrowUtils.throwIf(task == null, ErrorCode.NOT_FOUND_ERROR, "任务不存在");
        ThrowUtils.throwIf(task.getUserId() != null && !task.getUserId().equals(loginUser.getId()),
                ErrorCode.NO_AUTH_ERROR, "无权查看他人的任务");
        return ResultUtils.success(task);
    }

    /**
     * 查询当前用户最近的智能体任务列表（可观测性）
     */
    @GetMapping("/manus/task/list")
    public BaseResponse<List<AgentTask>> listManusTasks(HttpServletRequest request) {
        LoginUserVO loginUser = userService.getLoginUser(request);
        return ResultUtils.success(agentTaskService.listUserTasks(loginUser.getId()));
    }

    /**
     * 为智能体绑定异步任务：登记任务状态记录 + 绑定专用线程池
     */
    private void prepareManusTask(Manus Manus, String message, HttpServletRequest request) {
        LoginUserVO loginUser = userService.getLoginUser(request);
        agentTaskService.startTask(loginUser.getId(), loginUser.getUserAccount(), message, Manus);
        Manus.setTaskExecutor(agentTaskExecutor);
    }

    /**
     * 提交用户对智能体 askHuman 提问的回复（交互式执行）
     *
     * @param interactionId 交互 ID（由 SSE ask_human 事件下发）
     * @param answer        用户的回复内容
     * @return 是否提交成功
     */
    @PostMapping("/manus/human_reply")
    public BaseResponse<Boolean> submitHumanReply(@RequestParam String interactionId, @RequestParam String answer) {
        ThrowUtils.throwIf(interactionId == null || interactionId.isBlank(), ErrorCode.PARAMS_ERROR, "交互 ID 不能为空");
        ThrowUtils.throwIf(answer == null || answer.isBlank(), ErrorCode.PARAMS_ERROR, "回复内容不能为空");
        ThrowUtils.throwIf(!HumanInteractionRegistry.isPending(interactionId), ErrorCode.NOT_FOUND_ERROR, "提问已失效或已回复，请等待 AI 继续执行");
        boolean submitted = HumanInteractionRegistry.submitReply(interactionId, answer);
        return ResultUtils.success(submitted);
    }

    /**
     * 支持图片视觉理解的流式调用（POST JSON body）
     * 流程：接收 message + imageUrls + 可选的 imageUnderstandings（前端选图时已预解析）
     * → 若未预解析则调用 MIMO-v2.5 视觉理解 → 拼接理解结果与图片地址 → 喂给智能体 → 流式输出
     * 图片地址会注入上下文，供智能体在用户要求修改图片时作为 generateImage 的参考图使用。
     *
     * @param body JSON body，包含 message（文本）、imageUrls（图片 URL 列表）、imageUnderstandings（可选，预解析结果列表）
     * @return SSE 流式响应
     */
    @PostMapping("/manus/chat/vision")
    public SseEmitter doChatWithManusVision(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String message = (String) body.getOrDefault("message", "");
        @SuppressWarnings("unchecked")
        List<String> imageUrls = (List<String>) body.getOrDefault("imageUrls", List.of());
        @SuppressWarnings("unchecked")
        List<String> imageUnderstandings = (List<String>) body.getOrDefault("imageUnderstandings", List.of());

        // 优先使用前端选图时预解析的结果，避免重复解析；无预解析结果时实时调用 MIMO
        String understanding = "";
        if (imageUnderstandings != null && !imageUnderstandings.isEmpty()) {
            understanding = String.join("\n\n", imageUnderstandings);
        } else if (imageUrls != null && !imageUrls.isEmpty()) {
            understanding = miMoVisionService.understandImages(imageUrls, null);
        }

        // 将图片理解结果拼接到用户消息中
        String enhancedMessage = miMoVisionService.buildVisionEnhancedMessage(message, understanding);

        // 附加图片地址上下文，供智能体图生图时作为参考图
        if (imageUrls != null && !imageUrls.isEmpty()) {
            enhancedMessage = enhancedMessage + "\n\n[用户上传的图片地址]\n" + String.join("\n", imageUrls)
                    + "\n（如果用户要求基于上传图片修改/重绘/生成新图，请调用 generateImage 工具并将上述图片地址作为 referenceImageUrl 参数传入）";
        }

        // 使用增强后的消息走正常的 Manus 流式流程（同样登记异步任务）
        Manus Manus = new Manus(allTools, dashscopeChatModel);
        prepareManusTask(Manus, message, request);
        return Manus.runStream(enhancedMessage);
    }
}
