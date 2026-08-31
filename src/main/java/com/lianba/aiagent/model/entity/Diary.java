package com.lianba.aiagent.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 情感日记实体（对应数据库表 ai_diary）
 */
@Data
@TableName("ai_diary")
public class Diary implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户 ID
     */
    private Long userId;

    /**
     * 日记内容
     */
    private String content;

    /**
     * 情绪评分（1-5，1=很差 5=很好）
     */
    private Integer mood;

    /**
     * 标签（JSON 数组格式：["标签1","标签2"]）
     */
    private String tags;

    /**
     * AI 情绪分析结果
     */
    private String aiAnalysis;

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
