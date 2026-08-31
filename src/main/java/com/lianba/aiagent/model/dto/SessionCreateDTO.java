package com.lianba.aiagent.model.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 创建会话请求 DTO
 */
@Data
public class SessionCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 智能体类型：love / super
     */
    private String agentType;

    /**
     * 会话标题（可选，默认"新会话"）
     */
    private String title;

    /**
     * 聊天模式：chat / match（love 专属，可选）
     */
    private String chatMode;

    /**
     * 匹配对象性别偏好：男 / 女（love 专属，可选）
     */
    private String matchGender;
}
