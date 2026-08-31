package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.agent.model.AgentTaskStatus;

/**
 * 智能体运行结果监听器：任务结束（成功/失败/停止）时回调，
 * 用于将最终状态持久化到任务记录，提升可观测性。
 */
public interface AgentRunListener {

    /**
     * 任务结束回调（保证只回调一次）
     *
     * @param taskId       任务 ID
     * @param finalStatus  最终状态
     * @param errorMessage 失败原因（非 FAILED 时为 null）
     */
    void onTaskFinished(String taskId, AgentTaskStatus finalStatus, String errorMessage);
}
