package com.lianba.aiagent.agent.model;

/**
 * 智能体推理任务状态
 */
public enum AgentTaskStatus {

    /**
     * 执行中
     */
    RUNNING,

    /**
     * 执行成功
     */
    SUCCEEDED,

    /**
     * 执行失败
     */
    FAILED,

    /**
     * 被用户手动停止
     */
    STOPPED
}
