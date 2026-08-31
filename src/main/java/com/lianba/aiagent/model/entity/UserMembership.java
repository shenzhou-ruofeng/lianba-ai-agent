package com.lianba.aiagent.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 用户会员等级实体（对应数据库表 ai_user_membership）
 * 基于累计使用量自动升级，无需付费
 */
@Data
@TableName("ai_user_membership")
public class UserMembership implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户 ID
     */
    private Long userId;

    /**
     * 会员等级：bronze / silver / gold / diamond
     */
    private String tier;

    /**
     * 最近升级时间
     */
    private Date upgradeTime;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;
}
