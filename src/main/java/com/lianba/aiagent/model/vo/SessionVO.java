package com.lianba.aiagent.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 会话视图对象（返回给前端）
 */
@Data
public class SessionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 会话 ID（即 chatId）
     */
    private String sessionId;

    /**
     * 智能体类型：love / super
     */
    private String agentType;

    /**
     * 会话标题
     */
    private String title;

    /**
     * 聊天模式：chat / match
     */
    private String chatMode;

    /**
     * 匹配对象性别偏好
     */
    private String matchGender;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;
}
