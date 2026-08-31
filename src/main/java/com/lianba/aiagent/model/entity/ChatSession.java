package com.lianba.aiagent.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 会话实体（对应数据库表 ai_session）
 */
@Data
@TableName("ai_session")
public class ChatSession implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户 ID
     */
    private Long userId;

    /**
     * 会话唯一标识（即前端 chatId，如 love_xxxx / super_xxxx）
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
     * 聊天模式：chat / match（love 专属）
     */
    private String chatMode;

    /**
     * 匹配对象性别偏好：男 / 女 / null（love 专属）
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

    /**
     * 是否删除（0-未删除，1-已删除）
     */
    @TableLogic
    private Integer isDeleted;
}
