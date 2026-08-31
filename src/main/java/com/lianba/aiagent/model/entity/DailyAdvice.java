package com.lianba.aiagent.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 每日情感建议实体（对应数据库表 ai_daily_advice）
 */
@Data
@TableName("ai_daily_advice")
public class DailyAdvice implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户 ID
     */
    private Long userId;

    /**
     * 建议日期
     */
    private Date adviceDate;

    /**
     * 建议内容
     */
    private String content;

    /**
     * 创建时间
     */
    private Date createTime;
}
