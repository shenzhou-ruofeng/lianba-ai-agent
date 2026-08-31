package com.lianba.aiagent.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 社区帖子实体（对应数据库表 ai_community_post）
 */
@Data
@TableName("ai_community_post")
public class CommunityPost implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 发帖用户 ID
     */
    private Long userId;

    /**
     * 匿名昵称（为空时显示"匿名用户"）
     */
    private String nickname;

    /**
     * 帖子内容
     */
    private String content;

    /**
     * 标签：倾诉/求助/分享/讨论
     */
    private String tag;

    /**
     * 点赞数
     */
    private Integer likeCount;

    /**
     * 评论数
     */
    private Integer commentCount;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 是否删除
     */
    private Integer isDeleted;
}
