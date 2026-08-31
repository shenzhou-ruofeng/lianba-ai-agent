package com.lianba.aiagent.model.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 批量保存聊天消息请求 DTO
 */
@Data
public class ChatMessageBatchSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 所属会话 ID（即 chatId）
     */
    private String sessionId;

    /**
     * 消息列表
     */
    private java.util.List<ChatMessageSaveDTO> messages;
}
