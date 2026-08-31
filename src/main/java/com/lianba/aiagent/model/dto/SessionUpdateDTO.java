package com.lianba.aiagent.model.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 更新会话请求 DTO（修改标题、模式等）
 */
@Data
public class SessionUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 会话 ID（即 chatId）
     */
    private String sessionId;

    /**
     * 新标题
     */
    private String title;

    /**
     * 聊天模式：chat / match
     */
    private String chatMode;

    /**
     * 匹配对象性别偏好：男 / 女
     */
    private String matchGender;
}
