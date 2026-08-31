package com.lianba.aiagent.model.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 保存聊天消息请求 DTO
 */
@Data
public class ChatMessageSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 所属会话 ID（即 chatId）
     */
    private String sessionId;

    /**
     * 消息角色：user / assistant / system
     */
    private String role;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 消息类型：text / tool_step / thinking / love_report / generated_image 等
     */
    private String msgType;
}
