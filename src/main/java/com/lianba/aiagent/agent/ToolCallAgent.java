package com.lianba.aiagent.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.lianba.aiagent.agent.model.AgentState;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 处理工具调用的基础代理类，具体实现了 think 和 act 方法，可以用作创建实例的父类
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class ToolCallAgent extends ReActAgent {

    // 工具结果单条最大字符数，超过则截断
    private static final int MAX_TOOL_RESULT_LENGTH = 4000;
    // 消息历史总字符数警告阈值
    private static final int MAX_MESSAGE_HISTORY_CHARS = 200000;
    // 同一工具连续调用次数上限，超过则注入熔断提示
    private static final int MAX_CONSECUTIVE_SAME_TOOL = 3;

    // 连续调用同一工具的计数（用于检测循环调用）
    private final Map<String, Integer> consecutiveToolCallCount = new HashMap<>();

    // 可用的工具
    private final ToolCallback[] availableTools;

    // 保存工具调用信息的响应结果（要调用那些工具）
    private ChatResponse toolCallChatResponse;

    // 工具调用管理者
    private final ToolCallingManager toolCallingManager;

    // 禁用 Spring AI 内置的工具调用机制，自己维护选项和消息上下文
    private final ChatOptions chatOptions;

    public ToolCallAgent(ToolCallback[] availableTools) {
        super();
        this.availableTools = availableTools;
        this.toolCallingManager = ToolCallingManager.builder().build();
        // 禁用 Spring AI 内置的工具调用机制，自己维护选项和消息上下文
        this.chatOptions = DashScopeChatOptions.builder()
                .withInternalToolExecutionEnabled(false)
                .build();
    }

    /**
     * 处理当前状态并决定下一步行动
     *
     * @return 是否需要执行行动
     */
    @Override
    public boolean think() {
        // 1、校验提示词，拼接用户提示词
        if (StrUtil.isNotBlank(getNextStepPrompt())) {
            UserMessage userMessage = new UserMessage(getNextStepPrompt());
            getMessageList().add(userMessage);
        }
        // 2、调用 AI 大模型之前，检查消息历史大小
        ensureMessageHistorySize();
        // 2.5、校验并清理历史中可能存在非 JSON 参数的工具调用，防止 API 拒绝请求
        validateAndCleanToolCallArguments();
        // 2.6、检测同工具连续调用循环，必要时注入熔断提示
        checkAndBreakToolLoop();
        // 3、调用 AI 大模型，获取工具调用结果
        List<Message> messageList = getMessageList();
        Prompt prompt = new Prompt(messageList, this.chatOptions);
        ChatResponse chatResponse = getChatClient().prompt(prompt)
                .system(getSystemPrompt())
                .toolCallbacks(availableTools)
                .call()
                .chatResponse();
        // 记录响应，用于等下 Act
        this.toolCallChatResponse = chatResponse;
        // 4、解析工具调用结果，获取要调用的工具
        // 助手消息
        AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
        // 获取要调用的工具列表
        List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();
        // 输出提示信息
        String result = assistantMessage.getText();
        setLastThoughtText(result);
        log.info(getName() + "的思考：" + result);
        log.info(getName() + "选择了 " + toolCallList.size() + " 个工具来使用");
        String toolCallInfo = toolCallList.stream()
                .map(toolCall -> String.format("工具名称：%s，参数：%s", toolCall.name(), toolCall.arguments()))
                .collect(Collectors.joining("\n"));
        log.info(toolCallInfo);
        // 如果不需要调用工具，返回 false
        if (toolCallList.isEmpty()) {
            // 只有不调用工具时，才需要手动记录助手消息
            getMessageList().add(assistantMessage);
            return false;
        } else {
            // 需要调用工具时，无需记录助手消息，因为调用工具时会自动记录
            return true;
        }
    }

    /**
     * 执行工具调用并处理结果
     *
     * @return 执行结果
     */
    @Override
    public String act() {
        if (!toolCallChatResponse.hasToolCalls()) {
            return "没有工具需要调用";
        }
        // 调用工具
        Prompt prompt = new Prompt(getMessageList(), this.chatOptions);
        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);
        // 记录消息上下文，conversationHistory 已经包含了助手消息和工具调用返回的结果
        List<Message> conversationHistory = toolExecutionResult.conversationHistory();
        // 截断过长的工具返回结果，防止对话上下文溢出
        conversationHistory = truncateLargeToolResults(conversationHistory);
        setMessageList(conversationHistory);
        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) CollUtil.getLast(conversationHistory);
        // 更新连续调用计数，检测循环
        updateConsecutiveToolCallCount(toolResponseMessage);
        // 判断是否调用了终止工具
        boolean terminateToolCalled = toolResponseMessage.getResponses().stream()
                .anyMatch(response -> response.name().equals("doTerminate"));
        if (terminateToolCalled) {
            // 任务结束，更改状态
            setState(AgentState.FINISHED);
        } else if (toolExecutionResult.returnDirect()) {
            // 工具声明 returnDirect = true（如 PDF 生成工具），工具结果已可直接交付用户，
            // 视为任务完成信号，直接结束流程，避免额外调用 AI 模型
            log.info("工具声明 returnDirect=true，视为任务完成信号，结束执行流程");
            setState(AgentState.FINISHED);
        }
        String results = toolResponseMessage.getResponses().stream()
                .map(response -> "工具 " + response.name() + " 返回的结果：" + response.responseData())
                .collect(Collectors.joining("\n"));
        log.info(results);
        return results;
    }

    /**
     * 获取当前步骤要调用的工具名称列表
     */
    public List<String> getCurrentToolNames() {
        if (toolCallChatResponse == null || !toolCallChatResponse.hasToolCalls()) {
            return List.of();
        }
        AssistantMessage assistantMessage = toolCallChatResponse.getResult().getOutput();
        return assistantMessage.getToolCalls().stream()
                .map(AssistantMessage.ToolCall::name)
                .toList();
    }

    /**
     * 获取当前步骤要调用的工具参数列表
     */
    public List<String> getCurrentToolArguments() {
        if (toolCallChatResponse == null || !toolCallChatResponse.hasToolCalls()) {
            return List.of();
        }
        AssistantMessage assistantMessage = toolCallChatResponse.getResult().getOutput();
        return assistantMessage.getToolCalls().stream()
                .map(AssistantMessage.ToolCall::arguments)
                .toList();
    }

    /**
     * 截断对话历史中过长的工具返回结果，防止单条消息过大导致后续 API 调用失败
     */
    private List<Message> truncateLargeToolResults(List<Message> messages) {
        List<Message> result = new ArrayList<>();
        for (Message msg : messages) {
            if (msg instanceof ToolResponseMessage toolMsg) {
                List<ToolResponseMessage.ToolResponse> responses = toolMsg.getResponses();
                List<ToolResponseMessage.ToolResponse> newResponses = new ArrayList<>();
                boolean modified = false;
                for (ToolResponseMessage.ToolResponse resp : responses) {
                    String data = resp.responseData();
                    if (data != null && data.length() > MAX_TOOL_RESULT_LENGTH) {
                        String truncated = data.substring(0, MAX_TOOL_RESULT_LENGTH)
                                + "\n\n... (原始响应 " + data.length() + " 字符，已截断)";
                        newResponses.add(new ToolResponseMessage.ToolResponse(
                                resp.id(), resp.name(), truncated));
                        modified = true;
                        log.warn("Truncated tool '{}' response from {} to {} chars",
                                resp.name(), data.length(), MAX_TOOL_RESULT_LENGTH);
                    } else {
                        newResponses.add(resp);
                    }
                }
                if (modified) {
                    result.add(new ToolResponseMessage(newResponses, toolMsg.getMetadata()));
                } else {
                    result.add(msg);
                }
            } else {
                result.add(msg);
            }
        }
        return result;
    }

    /**
     * 检查消息历史总大小，防止上下文溢出导致 API 调用失败。
     * 按完整对话轮次删除，保证 AssistantMessage 与 ToolResponseMessage 的配对关系不被破坏。
     * 对话结构: [0]=原始用户问题, [1]=nextStepPrompt, [2]=AssistantMessage, [3]=ToolResponseMessage, ...
     */
    private void ensureMessageHistorySize() {
        List<Message> messages = getMessageList();
        long totalChars = estimateTotalChars(messages);
        if (totalChars > MAX_MESSAGE_HISTORY_CHARS) {
            log.warn("Message history is large ({} chars), may approach API limits", totalChars);
        }
        // 超出阈值时按完整轮次裁剪
        while (estimateTotalChars(messages) > MAX_MESSAGE_HISTORY_CHARS * 2 && messages.size() > 4) {
            int removedFrom = removeOldestCompleteRound(messages);
            if (removedFrom < 0) {
                // 找不到完整轮次时，回退到安全删除：只删索引 1 的消息（保留原始用户问题）
                Message removed = messages.remove(1);
                log.warn("Message history too large, fallback removed message at index 1: type={}",
                        removed.getMessageType());
            }
        }
    }

    /**
     * 移除对话历史中最旧的一个完整轮次。
     * 一个完整轮次 = UserMessage(nextStepPrompt) + AssistantMessage(工具调用) + ToolResponseMessage(工具结果)
     * @return 移除开始的索引，-1 表示未找到完整轮次
     */
    private int removeOldestCompleteRound(List<Message> messages) {
        if (messages.size() < 4) return -1;
        // 从索引 1 开始查找三个连续消息构成的完整轮次
        for (int i = 1; i < messages.size() - 2; i++) {
            Message m1 = messages.get(i);
            Message m2 = messages.get(i + 1);
            Message m3 = messages.get(i + 2);
            if (m1 instanceof UserMessage && m2 instanceof AssistantMessage && m3 instanceof ToolResponseMessage) {
                String toolNames = ((AssistantMessage) m2).getToolCalls().stream()
                        .map(AssistantMessage.ToolCall::name)
                        .collect(Collectors.joining(", "));
                // 保护图片生成轮次不被移除（用户需要 AI 记住生成的图片）
                if (toolNames.contains("generateImage")) {
                    log.info("Skipping removal of generateImage round to preserve image context");
                    continue;
                }
                // 从后往前删除，保持索引不变
                messages.remove(i + 2); // ToolResponseMessage
                messages.remove(i + 1); // AssistantMessage
                messages.remove(i);     // UserMessage(nextStepPrompt)
                log.info("Removed oldest conversation round (tools: {}), remaining total chars: {}",
                        toolNames, estimateTotalChars(messages));
                return i;
            }
        }
        return -1;
    }

    /**
     * 校验并清理消息历史中可能存在非 JSON 参数的工具调用。
     * 如果 AssistantMessage 中某个 ToolCall 的 arguments 不是合法 JSON（判据: 不以 { 开头），
     * 移除该 AssistantMessage 及紧随其后的 ToolResponseMessage（如果存在）。
     * 这防止 API 因历史中包含非法 function.arguments 而拒绝整个请求。
     */
    private void validateAndCleanToolCallArguments() {
        List<Message> messages = getMessageList();
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            if (!(msg instanceof AssistantMessage assistantMsg)) continue;

            List<AssistantMessage.ToolCall> toolCalls = assistantMsg.getToolCalls();
            if (toolCalls == null || toolCalls.isEmpty()) continue;

            boolean hasInvalidArgs = false;
            for (AssistantMessage.ToolCall tc : toolCalls) {
                String args = tc.arguments();
                // 参数为空 或 不是以 { 开头的 JSON 对象
                if (args == null || args.isBlank() || !args.trim().startsWith("{")) {
                    log.warn("Found invalid tool call arguments for tool '{}': args='{}', will remove this message round",
                            tc.name(), args);
                    hasInvalidArgs = true;
                    break;
                }
            }

            if (hasInvalidArgs) {
                // 移除 AssistantMessage
                messages.remove(i);
                // 检查现在位置 i 的消息是否为对应的 ToolResponseMessage，一并移除
                if (i < messages.size() && messages.get(i) instanceof ToolResponseMessage) {
                    messages.remove(i);
                }
                log.warn("Cleaned corrupted AssistantMessage at index {}, remaining messages: {}", i, messages.size());
            }
        }
    }

    /**
     * 估算消息列表的总字符数（用于判断是否接近 API 限制）
     */
    private long estimateTotalChars(List<Message> messages) {
        long total = 0;
        for (Message msg : messages) {
            if (msg instanceof ToolResponseMessage toolMsg) {
                for (ToolResponseMessage.ToolResponse resp : toolMsg.getResponses()) {
                    String data = resp.responseData();
                    if (data != null) {
                        total += data.length();
                    }
                }
            } else {
                String text = msg.getText();
                if (text != null) {
                    total += text.length();
                }
            }
        }
        return total;
    }

    /**
     * 检测同一工具是否被连续调用超过阈值，若是则注入熔断提示，防止 AI 陷入循环调用。
     * 在 think() 阶段 API 调用前执行。
     */
    private void checkAndBreakToolLoop() {
        for (Map.Entry<String, Integer> entry : consecutiveToolCallCount.entrySet()) {
            if (entry.getValue() >= MAX_CONSECUTIVE_SAME_TOOL) {
                String toolName = entry.getKey();
                log.warn("Circuit breaker: tool '{}' called {} times consecutively, injecting break prompt",
                        toolName, entry.getValue());
                getMessageList().add(new UserMessage(
                        "【系统提示】你已连续 " + entry.getValue() + " 次调用 " + toolName
                                + " 工具但未获得满意结果。请立即停止调用该工具，改用其他工具完成任务，"
                                + "或根据已有信息直接给出当前最佳回答。"));
                consecutiveToolCallCount.clear();
                break;
            }
        }
    }

    /**
     * 更新同一工具的连续调用次数。连续中断（本步骤未调用该工具）会被自动清零。
     */
    private void updateConsecutiveToolCallCount(ToolResponseMessage toolResponseMessage) {
        Set<String> calledThisStep = new HashSet<>();
        for (ToolResponseMessage.ToolResponse resp : toolResponseMessage.getResponses()) {
            calledThisStep.add(resp.name());
        }
        // 对每个本步骤调用的工具，增加计数
        for (String name : calledThisStep) {
            consecutiveToolCallCount.merge(name, 1, Integer::sum);
        }
        // 清除本步骤未调用的工具计数（连续已中断）
        consecutiveToolCallCount.keySet().retainAll(calledThisStep);
        log.debug("Consecutive tool call counts: {}", consecutiveToolCallCount);
    }
}
