package com.lianba.aiagent.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 关系状态变更日志实体（对应数据库表 ai_relationship_log）
 */
@Data
@TableName("ai_relationship_log")
public class RelationshipLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户 ID
     */
    private Long userId;

    /**
     * 变更前状态
     */
    private String oldStatus;

    /**
     * 变更后状态
     */
    private String newStatus;

    /**
     * 备注
     */
    private String note;

    /**
     * 创建时间
     */
    private Date createTime;
}
