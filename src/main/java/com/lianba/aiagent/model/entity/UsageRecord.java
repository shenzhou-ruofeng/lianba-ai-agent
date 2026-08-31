package com.lianba.aiagent.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 用量记录实体（对应数据库表 ai_usage_record）
 */
@Data
@TableName("ai_usage_record")
public class UsageRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户 ID
     */
    private Long userId;

    /**
     * 记录类型：chat_message / tool_call / diary_create / diary_analyze / advice_generate / report_generate
     */
    private String recordType;

    /**
     * 模型名称（如 qwen-plus）
     */
    private String modelName;

    /**
     * Token 消耗量
     */
    private Integer tokenCount;

    /**
     * 创建时间
     */
    private Date createTime;
}
