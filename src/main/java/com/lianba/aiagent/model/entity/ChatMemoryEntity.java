package com.lianba.aiagent.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 对话记忆实体（对应数据库表 ai_chat_memory，替代 Kryo 文件持久化）
 */
@Data
@TableName("ai_chat_memory")
public class ChatMemoryEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 会话 ID（即 conversationId / chatId）
     */
    private String conversationId;

    /**
     * 消息角色：user / assistant / system
     */
    private String role;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 消息在会话中的顺序编号
     */
    private Integer orderNum;

    /**
     * 创建时间
     */
    private Date createTime;
}
