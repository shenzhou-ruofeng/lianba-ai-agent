package com.lianba.aiagent.agent;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.lianba.aiagent.agent.interaction.HumanInteractionRegistry;
import com.lianba.aiagent.agent.model.AgentState;
import com.lianba.aiagent.agent.model.AgentTaskStatus;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 抽象基础代理类，用于管理代理状态和执行流程。
 * <p>
 * 提供状态转换、内存管理和基于步骤的执行循环的基础功能。
 * 子类必须实现step方法。
 */
@Data
@Slf4j
public abstract class BaseAgent {

    // 核心属性
    private String name;

    // 提示词
    private String systemPrompt;
    private String nextStepPrompt;

    // 代理状态
    private AgentState state = AgentState.IDLE;

    // 执行步骤控制
    private int currentStep = 0;
    private int maxSteps = 10;

    // 循环检测：相同响应重复出现的次数阈值，达到即判定为卡住状态
    private int duplicateThreshold = 2;

    // LLM 大模型
    private ChatClient chatClient;

    // Memory 记忆（需要自主维护会话上下文）
    private List<Message> messageList = new ArrayList<>();

    // 任务 ID（由 AgentTaskService 分配，用于任务状态记录与手动停止）
    private String taskId;

    // 手动停止标记（用户请求停止后，在步骤边界优雅退出）
    private volatile boolean stopRequested = false;

    // 任务执行线程池（未设置时降级使用公共线程池）
    private Executor taskExecutor;

    // 任务结束监听器（用于回写任务最终状态）
    private AgentRunListener runListener;

    // 保证监听器只回调一次
    private final AtomicBoolean listenerNotified = new AtomicBoolean(false);

    /**
     * 请求停止智能体：在下一个步骤边界（think/act 之间或步骤结束）优雅退出
     */
    public void requestStop() {
        this.stopRequested = true;
    }

    /**
     * 通知任务结束监听器（仅回调一次）
     */
    private void notifyRunListener(AgentTaskStatus status, String errorMessage) {
        if (runListener != null && taskId != null && listenerNotified.compareAndSet(false, true)) {
            try {
                runListener.onTaskFinished(taskId, status, errorMessage);
            } catch (Exception e) {
                log.warn("任务状态回调失败", e);
            }
        }
    }

    /**
     * 运行代理
     *
     * @param userPrompt 用户提示词
     * @return 执行结果
     */
    public String run(String userPrompt) {
        // 1、基础校验
        if (this.state != AgentState.IDLE) {
            throw new RuntimeException("Cannot run agent from state: " + this.state);
        }
        if (StrUtil.isBlank(userPrompt)) {
            throw new RuntimeException("Cannot run agent with empty user prompt");
        }
        // 2、执行，更改状态
        this.state = AgentState.RUNNING;
        // 记录消息上下文
        messageList.add(new UserMessage(userPrompt));
        // 保存结果列表
        List<String> results = new ArrayList<>();
        try {
            // 执行循环
            for (int i = 0; i < maxSteps && state == AgentState.RUNNING; i++) {
                int stepNumber = i + 1;
                currentStep = stepNumber;
                log.info("Executing step {}/{}", stepNumber, maxSteps);
                // 单步执行
                String stepResult = step();
                String result = "Step " + stepNumber + ": " + stepResult;
                results.add(result);
                // 循环检测：发现智能体陷入重复循环时，注入提示引导其调整策略
                if (state == AgentState.RUNNING && isStuck()) {
                    handleStuckState();
                }
            }
            // 检查是否超出步骤限制
            if (currentStep >= maxSteps) {
                state = AgentState.FINISHED;
                results.add("Terminated: Reached max steps (" + maxSteps + ")");
            }
            return String.join("\n", results);
        } catch (Exception e) {
            state = AgentState.ERROR;
            log.error("error executing agent", e);
            return "执行错误" + e.getMessage();
        } finally {
            // 3、清理资源
            this.cleanup();
        }
    }

    /**
     * 运行代理（流式输出）
     *
     * @param userPrompt 用户提示词
     * @return 执行结果
     */
    public SseEmitter runStream(String userPrompt) {
        // 创建一个超时时间较长的 SseEmitter（需覆盖 askHuman 等待用户回复的时间）
        SseEmitter sseEmitter = new SseEmitter(600000L); // 10 分钟超时
        // 使用专用线程池异步处理（未配置时降级为公共线程池），避免阻塞主线程
        Executor executor = taskExecutor != null ? taskExecutor : ForkJoinPool.commonPool();
        CompletableFuture.runAsync(() -> {
            // 1、基础校验
            try {
                if (this.state != AgentState.IDLE) {
                    sseEmitter.send("错误：无法从状态运行代理：" + this.state);
                    sseEmitter.complete();
                    return;
                }
                if (StrUtil.isBlank(userPrompt)) {
                    sseEmitter.send("错误：不能使用空提示词运行代理");
                    sseEmitter.complete();
                    return;
                }
            } catch (Exception e) {
                sseEmitter.completeWithError(e);
            }
            // 2、执行，更改状态
            this.state = AgentState.RUNNING;
            // 记录消息上下文
            messageList.add(new UserMessage(userPrompt));
            // 跟踪本次运行生成的文件（用于最终统一提供下载按钮）
            List<String[]> generatedFiles = new ArrayList<>();
            // 记录任务执行过程（思考 + 工具结果），用于结束后生成总结
            StringBuilder taskLog = new StringBuilder();
            // 是否执行过工具调用（决定是否需要生成最终总结）
            boolean[] hasToolExecution = {false};
            try {
                // 下发任务 ID，供前端手动停止任务时使用
                if (StrUtil.isNotBlank(taskId)) {
                    sseEmitter.send("{\"type\":\"task_id\",\"taskId\":\"" + escapeJson(taskId) + "\"}");
                }
                // 转换为 ReActAgent 以调用 think/act（只有 ReActAgent 子类会调用 runStream）
                ReActAgent reactAgent = (ReActAgent) this;
                // 执行循环（收到停止请求时在步骤边界退出）
                for (int i = 0; i < maxSteps && state == AgentState.RUNNING && !stopRequested; i++) {
                    int stepNumber = i + 1;
                    currentStep = stepNumber;
                    log.info("Executing step {}/{}", stepNumber, maxSteps);
                    // 发送状态：正在思考
                    sseEmitter.send(buildStatusJson("thinking", "正在思考中..."));
                    // 先思考
                    boolean shouldAct = reactAgent.think();
                    // 手动停止：think 结束后检查停止标记，避免继续执行工具
                    if (stopRequested) {
                        break;
                    }
                    // 本步的思考文本（AI 的推理过程）
                    String thoughtText = reactAgent.getLastThoughtText();
                    if (!shouldAct) {
                        // 无需行动，代理已回答完毕
                        if (getState() == AgentState.ERROR) {
                            String errorMsg = reactAgent.getLastErrorMessage() != null ? reactAgent.getLastErrorMessage() : "未知错误";
                            sseEmitter.send(buildJson("text", stepNumber, null, null, "思考过程遇到错误：" + errorMsg));
                        } else {
                            setState(AgentState.FINISHED);
                            // 未经过工具调用直接回答，发送 AI 的实际文本作为最终回答
                            if (StrUtil.isNotBlank(thoughtText)) {
                                sseEmitter.send(buildJson("text", stepNumber, null, null, thoughtText));
                            } else {
                                // 模型未返回任何文本内容（直接回答但无内容），补发默认回答
                                sseEmitter.send(buildJson("text", stepNumber, null, null,
                                        "已分析完毕，但未能生成有效回复，请尝试重新描述您的问题。"));
                            }
                        }
                        break;
                    }
                    // 需要调用工具：先把 AI 的思考过程发给前端（DeepSeek 风格，可折叠展示）
                    // 如果模型只返回工具调用无思考文本，发送默认思考提示，避免前端缺失思考卡片
                    if (StrUtil.isNotBlank(thoughtText)) {
                        sseEmitter.send(buildJson("think", stepNumber, null, null, thoughtText));
                        taskLog.append("【思考 ").append(stepNumber).append("】").append(thoughtText).append("\n");
                    } else {
                        // 模型未输出思考文本（直接返回工具调用），补发默认思考消息
                        String defaultThink = "正在分析请求，准备调用工具...";
                        sseEmitter.send(buildJson("think", stepNumber, null, null, defaultThink));
                        taskLog.append("【思考 ").append(stepNumber).append("】").append(defaultThink).append("\n");
                    }
                    // 发送工具调用信息（名称 + 参数）
                    if (this instanceof ToolCallAgent toolAgent) {
                        List<String> toolNames = toolAgent.getCurrentToolNames();
                        List<String> toolArgs = toolAgent.getCurrentToolArguments();
                        for (int j = 0; j < toolNames.size(); j++) {
                            String toolName = toolNames.get(j);
                            String arguments = j < toolArgs.size() ? toolArgs.get(j) : "{}";
                            sseEmitter.send(buildJson("tool_call", stepNumber, toolName, arguments, null));
                        }
                    }
                    // 发送状态：正在执行工具
                    if (this instanceof ToolCallAgent toolAgent) {
                        List<String> toolNames = toolAgent.getCurrentToolNames();
                        if (!toolNames.isEmpty()) {
                            String firstTool = toolNames.get(0);
                            String statusMsg = getToolStatusMessage(firstTool);
                            sseEmitter.send(buildStatusJson("executing_tool", statusMsg));
                        }
                    }
                    // askHuman 工具：act() 执行时会阻塞等待用户回复，需先通过 SSE 把问题推送给前端
                    String askHumanInteractionId = null;
                    if (this instanceof ToolCallAgent toolAgent) {
                        int askHumanIndex = toolAgent.getCurrentToolNames().indexOf("askHuman");
                        if (askHumanIndex >= 0) {
                            askHumanInteractionId = HumanInteractionRegistry.createInteraction();
                            HumanInteractionRegistry.bindToCurrentThread(askHumanInteractionId);
                            String inquiry = extractAskHumanInquiry(toolAgent.getCurrentToolArguments(), askHumanIndex);
                            sseEmitter.send(buildAskHumanJson(askHumanInteractionId, stepNumber, inquiry));
                            sseEmitter.send(buildStatusJson("waiting_human", "等待你的回复..."));
                        }
                    }
                    // 再行动
                    String actResult;
                    try {
                        actResult = reactAgent.act();
                    } finally {
                        // 清理人机交互资源，避免泄漏
                        if (askHumanInteractionId != null) {
                            HumanInteractionRegistry.unbindFromCurrentThread();
                            HumanInteractionRegistry.removeInteraction(askHumanInteractionId);
                        }
                    }
                    hasToolExecution[0] = true;
                    // 发送工具执行结果
                    if (this instanceof ToolCallAgent toolAgent) {
                        List<String> toolNames = toolAgent.getCurrentToolNames();
                        // 解析 actResult，为每个工具发送 result
                        if (!toolNames.isEmpty()) {
                            String[] resultParts = actResult.split("\n");
                            for (int j = 0; j < Math.min(toolNames.size(), resultParts.length); j++) {
                                String toolName = toolNames.get(j);
                                // 提取实际结果（去掉 "工具 XXX 返回的结果：" 前缀）
                                String resultData = resultParts[j];
                                sseEmitter.send(buildJson("tool_result", stepNumber, toolName, null, resultData));
                                // 收集本步骤生成的可下载文件
                                collectGeneratedFile(toolName, resultData, generatedFiles);
                                // 如果生成了图片，立即发送图片预览事件
                                if ("generateImage".equals(toolName) && resultData.contains("下载地址：")) {
                                    sendGeneratedImageIfPresent(resultData, sseEmitter);
                                }
                                taskLog.append("【工具 ").append(toolName).append("】").append(resultData).append("\n");
                            }
                        } else {
                            sseEmitter.send(buildJson("tool_result", stepNumber, null, null, actResult));
                        }
                    } else {
                        sseEmitter.send(buildJson("text", stepNumber, null, null, actResult));
                    }
                    // 如果调用了终止工具，状态已变为 FINISHED，立即退出循环，防止后续出现多余消息
                    if (getState() == AgentState.FINISHED) {
                        break;
                    }
                    // 循环检测：发现智能体陷入重复循环时，注入提示引导其调整策略
                    if (isStuck()) {
                        handleStuckState();
                        sseEmitter.send(buildStatusJson("stuck_detected", "检测到重复操作，正在调整策略..."));
                    }
                }
                // 手动停止：发送停止提示，跳过后续总结（前端据此展示停止卡片）
                if (stopRequested) {
                    state = AgentState.FINISHED;
                    sseEmitter.send(buildStatusJson("stopped", "已停止回复"));
                }
                // 检查是否超出步骤限制
                else if (currentStep >= maxSteps) {
                    state = AgentState.FINISHED;
                    sseEmitter.send(buildJson("text", 0, null, null, "执行结束：达到最大步骤（" + maxSteps + "）"));
                }
                // 思考结束后：若执行过工具且未被手动停止，生成最终总结并汇总可下载文件
                if (hasToolExecution[0] && !stopRequested) {
                    String summary = generateSummary(taskLog.toString());
                    if (StrUtil.isNotBlank(summary)) {
                        sseEmitter.send(buildJson("summary", 0, null, null, summary));
                    }
                    if (!generatedFiles.isEmpty()) {
                        sseEmitter.send(buildFilesJson(generatedFiles));
                    }
                }
                // 发送完成标记，让前端关闭 EventSource
                try {
                    sseEmitter.send("[DONE]");
                } catch (IOException e) {
                    log.warn("Failed to send [DONE] marker", e);
                }
                // 正常完成
                sseEmitter.complete();
                // 回写任务最终状态
                notifyRunListener(stopRequested ? AgentTaskStatus.STOPPED : AgentTaskStatus.SUCCEEDED, null);
            } catch (Exception e) {
                state = AgentState.ERROR;
                log.error("error executing agent", e);
                notifyRunListener(AgentTaskStatus.FAILED, e.getMessage());
                try {
                    sseEmitter.send(buildJson("text", 0, null, null, "执行错误：" + e.getMessage()));
                    sseEmitter.send("[DONE]");
                    sseEmitter.complete();
                } catch (IOException ex) {
                    sseEmitter.completeWithError(ex);
                }
            } finally {
                // 3、清理资源
                this.cleanup();
            }
        }, executor);

        // 设置超时回调
        sseEmitter.onTimeout(() -> {
            this.state = AgentState.ERROR;
            this.cleanup();
            notifyRunListener(AgentTaskStatus.FAILED, "SSE 连接超时");
            log.warn("SSE connection timeout");
        });
        // 设置完成回调
        sseEmitter.onCompletion(() -> {
            if (this.state == AgentState.RUNNING) {
                this.state = AgentState.FINISHED;
            }
            this.cleanup();
            notifyRunListener(stopRequested ? AgentTaskStatus.STOPPED : AgentTaskStatus.SUCCEEDED, null);
            log.info("SSE connection completed");
        });
        return sseEmitter;
    }

    /**
     * 构建结构化的 JSON 消息
     */
    private String buildJson(String type, int step, String toolName, String arguments, String result) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"").append(escapeJson(type)).append("\"");
        if (step > 0) {
            sb.append(",\"step\":").append(step);
        }
        if (toolName != null) {
            sb.append(",\"toolName\":\"").append(escapeJson(toolName)).append("\"");
        }
        if (arguments != null) {
            sb.append(",\"arguments\":\"").append(escapeJson(arguments)).append("\"");
        }
        if (result != null) {
            sb.append(",\"result\":\"").append(escapeJson(result)).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * 从工具执行结果中识别生成的可下载文件，并收集到列表（自动去重）。
     * generatedFiles 中每个元素为 [文件名, 下载URL, 类型]。
     */
    private void collectGeneratedFile(String toolName, String result, List<String[]> generatedFiles) {
        if (toolName == null || result == null) {
            return;
        }
        String name = null;
        String url = null;
        String type = null;
        if ("generatePDF".equals(toolName) && result.contains("PDF generated successfully")) {
            // 兼容 OSS 公网绝对地址与本地相对下载地址两种格式：[Download: <url>]
            Matcher m = Pattern.compile("\\[Download: ([^\\s\\]]+)\\]").matcher(result);
            if (m.find()) {
                url = m.group(1);
                // 文件名取 URL 最后一段路径，并去掉签名 URL 可能携带的查询参数
                name = url.replaceAll("^.*/", "");
                int queryIndex = name.indexOf('?');
                if (queryIndex >= 0) {
                    name = name.substring(0, queryIndex);
                }
                type = "pdf";
            }
        } else if ("writeFile".equals(toolName) && result.contains("File written successfully")) {
            Matcher m = Pattern.compile("File written successfully to:\\s*(.+)").matcher(result);
            if (m.find()) {
                String path = m.group(1).trim();
                name = path.replaceAll("^.*[\\\\/]", "");
                url = "/api/files/download/file/" + name;
                type = "file";
            }
        }
        if (name != null && url != null) {
            // 去重：相同 URL 不重复添加
            for (String[] existing : generatedFiles) {
                if (existing[1].equals(url)) {
                    return;
                }
            }
            generatedFiles.add(new String[]{name, url, type});
        }
    }

    /**
     * 构建生成文件列表的 JSON 消息，供前端渲染下载按钮。
     */
    private String buildFilesJson(List<String[]> files) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"files\",\"files\":[");
        for (int i = 0; i < files.size(); i++) {
            String[] f = files.get(i);
            if (i > 0) {
                sb.append(",");
            }
            sb.append("{\"name\":\"").append(escapeJson(f[0])).append("\",")
                    .append("\"url\":\"").append(escapeJson(f[1])).append("\",")
                    .append("\"type\":\"").append(escapeJson(f[2])).append("\"}");
        }
        sb.append("]}");
        return sb.toString();
    }

    /**
     * 从 generateImage 工具结果中提取图片 URL，并通过 SSE 发送预览事件。
     * 优先使用本地持久化的 URL（不会过期），远程 URL 作为降级。
     */
    private void sendGeneratedImageIfPresent(String result, SseEmitter sseEmitter) {
        String imageUrl = null;
        // 优先匹配本地下载地址（不会过期）
        // 排除引号、反引号，防止 AI 模型在 URL 后拼接 Markdown 格式字符
        Matcher mLocal = Pattern.compile("下载地址：(/api/files/download/image/[^\\s\\\\\"'`]+)").matcher(result);
        if (mLocal.find()) {
            imageUrl = stripTrailingQuotes(mLocal.group(1));
        } else {
            // 降级：匹配远程 URL
            Matcher m = Pattern.compile("下载地址：(https?://[^\\s\\\\\"'`]+)").matcher(result);
            if (m.find()) {
                imageUrl = stripTrailingQuotes(m.group(1));
            }
        }
        if (imageUrl != null) {
            try {
                sseEmitter.send(buildGeneratedImageJson(imageUrl));
            } catch (IOException e) {
                log.warn("Failed to send generated image event", e);
            }
        }
    }

    /**
     * 构建生成图片的 JSON 消息，供前端渲染预览和下载按钮。
     */
    private String buildGeneratedImageJson(String imageUrl) {
        return "{\"type\":\"generated_image\",\"url\":\"" + escapeJson(imageUrl) + "\"}";
    }

    /**
     * 剥离 URL 末尾可能被 AI 模型拼接的引号、反引号、括号、标点等非 URL 字符。
     * 防止这些字符传递到前端后，浏览器请求路径包含非法字符导致 500 错误。
     */
    private String stripTrailingQuotes(String url) {
        if (url == null) return null;
        // 去掉末尾的引号、反引号、括号、中文/英文标点等
        while (!url.isEmpty()) {
            char last = url.charAt(url.length() - 1);
            if (last == '"' || last == '\'' || last == '`'
                    || last == ')' || last == ']' || last == '}' || last == ',' || last == ';'
                    || last == '\uFF09' || last == '\uFF1B' || last == '\uFF1A'
                    || last == '\u3001' || last == '\u3002' || last == '.') {
                url = url.substring(0, url.length() - 1);
            } else {
                break;
            }
        }
        // 去掉开头的引号（极少发生，但防御性处理）
        while (url.startsWith("\"") || url.startsWith("'") || url.startsWith("`")) {
            url = url.substring(1);
        }
        return url;
    }

    /**
     * 循环检测（参考 OpenManus 的 is_stuck 设计）：
     * 检查最后一条助手消息是否与之前的助手消息重复出现（文本 + 工具调用签名完全一致），
     * 重复次数达到阈值则判定智能体陷入无限循环。
     *
     * @return 是否处于卡住（循环）状态
     */
    protected boolean isStuck() {
        List<Message> messages = getMessageList();
        if (messages.size() < 2) {
            return false;
        }
        // 找到最后一条助手消息
        AssistantMessage lastAssistantMessage = null;
        int lastIndex = -1;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof AssistantMessage assistantMessage) {
                lastAssistantMessage = assistantMessage;
                lastIndex = i;
                break;
            }
        }
        if (lastAssistantMessage == null) {
            return false;
        }
        String signature = buildAssistantSignature(lastAssistantMessage);
        if (StrUtil.isBlank(signature)) {
            return false;
        }
        // 统计之前相同响应出现的次数
        int duplicateCount = 0;
        for (int i = lastIndex - 1; i >= 0; i--) {
            if (messages.get(i) instanceof AssistantMessage assistantMessage
                    && signature.equals(buildAssistantSignature(assistantMessage))) {
                duplicateCount++;
            }
        }
        return duplicateCount >= duplicateThreshold;
    }

    /**
     * 构建助手消息的内容签名（文本 + 工具调用名称与参数），用于判断两次响应是否重复
     */
    private String buildAssistantSignature(AssistantMessage message) {
        StringBuilder sb = new StringBuilder();
        if (StrUtil.isNotBlank(message.getText())) {
            sb.append(message.getText());
        }
        if (message.getToolCalls() != null) {
            for (AssistantMessage.ToolCall toolCall : message.getToolCalls()) {
                sb.append("|").append(toolCall.name()).append(":").append(toolCall.arguments());
            }
        }
        return sb.toString();
    }

    /**
     * 处理卡住状态（参考 OpenManus 的 handle_stuck_state 设计）：
     * 向下一步提示词前置插入纠偏提示，引导智能体改变策略，避免重复无效操作。
     */
    protected void handleStuckState() {
        String stuckPrompt = "【系统检测】观察到你在重复相同的响应或操作，已陷入循环。请立即调整策略："
                + "不要重复已尝试过的无效操作，尝试新的方法或工具；如确实无法完成任务，"
                + "请调用 doTerminate 结束并向用户坦诚说明原因。";
        // 避免多次触发时重复叠加相同提示
        String nextStepPrompt = getNextStepPrompt() == null ? "" : getNextStepPrompt();
        if (!nextStepPrompt.startsWith(stuckPrompt)) {
            setNextStepPrompt(stuckPrompt + "\n" + nextStepPrompt);
        }
        log.warn("Agent detected stuck state (duplicate threshold {} reached), added strategy-change prompt", duplicateThreshold);
    }

    /**
     * 从 askHuman 工具的调用参数中提取问题文本
     */
    private String extractAskHumanInquiry(List<String> toolArguments, int index) {
        try {
            if (index < toolArguments.size()) {
                String arguments = toolArguments.get(index);
                if (StrUtil.isNotBlank(arguments) && arguments.trim().startsWith("{")) {
                    String inquiry = JSONUtil.parseObj(arguments).getStr("inquiry");
                    if (StrUtil.isNotBlank(inquiry)) {
                        return inquiry;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse askHuman inquiry from arguments", e);
        }
        return "AI 需要你提供更多信息才能继续完成任务，请补充说明。";
    }

    /**
     * 构建 askHuman 提问的 JSON 消息，供前端渲染回复输入框
     */
    private String buildAskHumanJson(String interactionId, int step, String question) {
        return "{\"type\":\"ask_human\",\"step\":" + step
                + ",\"interactionId\":\"" + escapeJson(interactionId) + "\""
                + ",\"question\":\"" + escapeJson(question) + "\"}";
    }

    /**
     * 构建状态更新的 JSON 消息，供前端显示实时状态。
     */
    private String buildStatusJson(String status, String message) {
        return "{\"type\":\"status\",\"status\":\"" + escapeJson(status) + "\",\"message\":\"" + escapeJson(message) + "\"}";
    }

    /**
     * 根据工具名称返回对应的中文状态提示。
     */
    private String getToolStatusMessage(String toolName) {
        return switch (toolName) {
            case "generateImage" -> "正在生成图片...";
            case "searchWeb" -> "正在搜索网页...";
            case "webScraping" -> "正在抓取网页内容...";
            case "generatePDF" -> "正在生成 PDF 文件...";
            case "writeFile" -> "正在写入文件...";
            case "doTerminate" -> "正在完成任务...";
            case "askHuman" -> "正在向你提问，等待回复...";
            default -> "正在执行 " + toolName + "...";
        };
    }

    /**
     * 思考结束后，基于任务执行记录调用 AI 生成一段友好的中文总结。
     * 使用独立的精简上下文（仅包含执行记录文本），避免重放带工具调用的历史导致 API 报错。
     */
    private String generateSummary(String taskLog) {
        try {
            String summaryPrompt = """
                    以下是你在本次任务中的执行记录（包含你的思考和工具调用结果）：

                    %s

                    请用简洁、友好、条理清晰的中文总结你为用户完成的工作和最终成果。要求：
                    1. 概述任务完成情况和关键结论；
                    2. 如果生成了文件（如 PDF、文本文件等），简要说明文件内容；
                    3. 不要原样罗列工具日志，用自然语言表达。
                    """.formatted(taskLog);
            List<Message> summaryMessages = new ArrayList<>();
            summaryMessages.add(new UserMessage(summaryPrompt));
            ChatResponse response = getChatClient()
                    .prompt(new Prompt(summaryMessages))
                    .call()
                    .chatResponse();
            if (response == null || response.getResult() == null) {
                return null;
            }
            return response.getResult().getOutput().getText();
        } catch (Exception e) {
            log.error("生成任务总结失败", e);
            return null;
        }
    }

    /**
     * 定义单个步骤
     *
     * @return
     */
    public abstract String step();

    /**
     * 清理资源
     */
    protected void cleanup() {
        // 子类可以重写此方法来清理资源
    }
}
