package com.lianba.aiagent.agent.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lianba.aiagent.agent.BaseAgent;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 智能体推理任务记录（对应数据库表 agent_task）
 */
@Data
@TableName("agent_task")
public class AgentTask implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 任务 ID（UUID，主键）
     */
    @TableId
    private String taskId;

    /**
     * 发起任务的用户 ID
     */
    private Long userId;

    /**
     * 发起任务的用户账号
     */
    private String userAccount;

    /**
     * 用户输入的消息（超长截断存储）
     */
    private String message;

    /**
     * 任务状态：RUNNING / SUCCEEDED / FAILED / STOPPED
     */
    private String status;

    /**
     * 失败原因（仅 FAILED 时有值）
     */
    private String errorMessage;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 最后更新时间
     */
    private Date updateTime;

    /**
     * 非数据库字段：运行中的智能体实例引用（不持久化）
     */
    @TableField(exist = false)
    private transient BaseAgent agentRef;
}
