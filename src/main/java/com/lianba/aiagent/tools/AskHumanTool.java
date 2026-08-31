package com.lianba.aiagent.tools;

import cn.hutool.core.util.StrUtil;
import com.lianba.aiagent.agent.interaction.HumanInteractionRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.concurrent.TimeoutException;

/**
 * 向人类求助工具（参考 OpenManus 的 AskHuman 设计）：
 * 由 AI 自主决定何时向用户提问，获取缺失信息或让用户做决策，
 * 从而实现交互式执行、优化任务完成效果。
 * <p>
 * 实现原理：智能体在流式执行时会先把问题通过 SSE 推送给前端，
 * 然后本工具在执行线程上阻塞等待用户通过 HTTP 接口提交的回复。
 */
@Slf4j
public class AskHumanTool {

    /**
     * 工具名称（智能体侧据此识别 askHuman 调用）
     */
    public static final String TOOL_NAME = "askHuman";

    /**
     * 等待用户回复的最长秒数
     */
    private static final long REPLY_TIMEOUT_SECONDS = 240;

    @Tool(name = TOOL_NAME, description = """
            Use this tool to ask the human user for help when you are missing key information,
            need the user to make a choice or confirmation, or are unsure how to proceed.
            The inquiry should be a clear, specific question in the same language as the user.
            The tool returns the user's reply, which you should use to continue the task.
            Do NOT overuse this tool: only ask when the information truly cannot be obtained by yourself.
            """)
    public String askHuman(@ToolParam(description = "The question you want to ask the user") String inquiry) {
        String interactionId = HumanInteractionRegistry.getCurrentInteractionId();
        // 非交互式运行环境（如同步调用、单元测试）没有绑定交互，优雅降级
        if (StrUtil.isBlank(interactionId)) {
            log.warn("askHuman called without bound interaction, degrade gracefully");
            return "（当前运行模式不支持向用户实时提问，请基于已有信息做出最合理的假设并继续完成任务。）";
        }
        log.info("askHuman waiting for user reply, interactionId={}, inquiry={}", interactionId, inquiry);
        try {
            String answer = HumanInteractionRegistry.waitForReply(interactionId, REPLY_TIMEOUT_SECONDS);
            log.info("askHuman received user reply: {}", answer);
            return "用户的回复：" + answer;
        } catch (TimeoutException e) {
            log.warn("askHuman wait for reply timeout, interactionId={}", interactionId);
            return "用户在 " + REPLY_TIMEOUT_SECONDS + " 秒内未回复。请基于已有信息做出最合理的假设并继续完成任务，不要再次调用 askHuman。";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "等待用户回复被中断，请基于已有信息继续完成任务。";
        } catch (Exception e) {
            log.error("askHuman failed", e);
            return "获取用户回复失败：" + e.getMessage() + "。请基于已有信息继续完成任务。";
        }
    }
}
