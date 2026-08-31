package com.lianba.aiagent.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 恋爱报告实体（对应数据库表 ai_love_report）
 */
@Data
@TableName("ai_love_report")
public class LoveReportEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户 ID
     */
    private Long userId;

    /**
     * 所属会话 ID（可选）
     */
    private String sessionId;

    /**
     * 报告标题
     */
    private String title;

    /**
     * 恋爱建议（JSON 数组格式：["建议1", "建议2", ...]）
     */
    private String suggestions;

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
