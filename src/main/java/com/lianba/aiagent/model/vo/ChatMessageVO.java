package com.lianba.aiagent.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 聊天消息视图对象（返回给前端）
 */
@Data
public class ChatMessageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息 ID
     */
    private Long id;

    /**
     * 所属会话 ID
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

    /**
     * 创建时间
     */
    private Date createTime;
}
