package com.lianba.aiagent.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 聊天消息实体（对应数据库表 ai_chat_message）
 */
@Data
@TableName("ai_chat_message")
public class ChatMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属会话 ID（关联 ai_session.session_id）
     */
    private String sessionId;

    /**
     * 所属用户 ID
     */
    private Long userId;

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

    /**
     * 是否删除（0-未删除，1-已删除）
     */
    @TableLogic
    private Integer isDeleted;
}
