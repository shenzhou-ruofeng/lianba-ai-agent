package com.lianba.aiagent.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianba.aiagent.exception.ErrorCode;
import com.lianba.aiagent.exception.ThrowUtils;
import com.lianba.aiagent.mapper.CommunityCommentMapper;
import com.lianba.aiagent.mapper.CommunityPostMapper;
import com.lianba.aiagent.model.entity.CommunityComment;
import com.lianba.aiagent.model.entity.CommunityPost;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * 情感轻社区服务：帖子 CRUD + 评论 + 点赞
 */
@Slf4j
@Service
public class CommunityService {

    private static final String[] VALID_TAGS = {"倾诉", "求助", "分享", "讨论"};

    @Resource
    private CommunityPostMapper communityPostMapper;

    @Resource
    private CommunityCommentMapper communityCommentMapper;

    /**
     * 发帖
     */
    public CommunityPost createPost(Long userId, String content, String tag, String nickname) {
        ThrowUtils.throwIf(StrUtil.isBlank(content), ErrorCode.PARAMS_ERROR, "帖子内容不能为空");
        ThrowUtils.throwIf(content.length() > 2000, ErrorCode.PARAMS_ERROR, "帖子内容不能超过 2000 字");
        ThrowUtils.throwIf(tag != null && !List.of(VALID_TAGS).contains(tag), ErrorCode.PARAMS_ERROR, "标签不合法");

        CommunityPost post = new CommunityPost();
        post.setUserId(userId);
        post.setContent(content);
        post.setTag(tag);
        post.setNickname(StrUtil.isNotBlank(nickname) ? nickname.trim() : null);
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setCreateTime(new Date());
        post.setIsDeleted(0);
        communityPostMapper.insert(post);
        return post;
    }

    /**
     * 帖子分页列表（按时间倒序，支持标签筛选）
     */
    public Page<CommunityPost> listPosts(String tag, int page, int size) {
        LambdaQueryWrapper<CommunityPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CommunityPost::getIsDeleted, 0);
        if (StrUtil.isNotBlank(tag)) {
            wrapper.eq(CommunityPost::getTag, tag);
        }
        wrapper.orderByDesc(CommunityPost::getCreateTime);
        Page<CommunityPost> pageParam = new Page<>(page, size);
        return communityPostMapper.selectPage(pageParam, wrapper);
    }

    /**
     * 帖子详情
     */
    public CommunityPost getPost(Long postId) {
        CommunityPost post = communityPostMapper.selectById(postId);
        ThrowUtils.throwIf(post == null || post.getIsDeleted() == 1, ErrorCode.NOT_FOUND_ERROR, "帖子不存在");
        return post;
    }

    /**
     * 删除帖子（仅作者可删）
     */
    public void deletePost(Long userId, Long postId) {
        CommunityPost post = getPost(postId);
        ThrowUtils.throwIf(!post.getUserId().equals(userId), ErrorCode.FORBIDDEN_ERROR, "只能删除自己的帖子");
        CommunityPost update = new CommunityPost();
        update.setId(postId);
        update.setIsDeleted(1);
        communityPostMapper.updateById(update);
    }

    /**
     * 评论帖子
     */
    public CommunityComment createComment(Long userId, Long postId, String content, String nickname) {
        ThrowUtils.throwIf(StrUtil.isBlank(content), ErrorCode.PARAMS_ERROR, "评论内容不能为空");
        ThrowUtils.throwIf(content.length() > 500, ErrorCode.PARAMS_ERROR, "评论内容不能超过 500 字");

        // 确保帖子存在
        getPost(postId);

        CommunityComment comment = new CommunityComment();
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setContent(content);
        comment.setNickname(StrUtil.isNotBlank(nickname) ? nickname.trim() : null);
        comment.setCreateTime(new Date());
        comment.setIsDeleted(0);
        communityCommentMapper.insert(comment);

        // 更新帖子评论数
        CommunityPost postUpdate = new CommunityPost();
        postUpdate.setId(postId);
        postUpdate.setCommentCount(getCommentCount(postId));
        communityPostMapper.updateById(postUpdate);

        return comment;
    }

    /**
     * 获取帖子评论列表
     */
    public List<CommunityComment> listComments(Long postId) {
        LambdaQueryWrapper<CommunityComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CommunityComment::getPostId, postId)
                .eq(CommunityComment::getIsDeleted, 0)
                .orderByAsc(CommunityComment::getCreateTime);
        return communityCommentMapper.selectList(wrapper);
    }

    /**
     * 点赞帖子（+1）
     */
    public void likePost(Long postId) {
        CommunityPost post = getPost(postId);
        CommunityPost update = new CommunityPost();
        update.setId(postId);
        update.setLikeCount((post.getLikeCount() != null ? post.getLikeCount() : 0) + 1);
        communityPostMapper.updateById(update);
    }

    private int getCommentCount(Long postId) {
        LambdaQueryWrapper<CommunityComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CommunityComment::getPostId, postId)
                .eq(CommunityComment::getIsDeleted, 0);
        return Math.toIntExact(communityCommentMapper.selectCount(wrapper));
    }
}
