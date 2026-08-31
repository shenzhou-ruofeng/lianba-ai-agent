package com.lianba.aiagent.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianba.aiagent.common.BaseResponse;
import com.lianba.aiagent.common.ResultUtils;
import com.lianba.aiagent.model.entity.CommunityComment;
import com.lianba.aiagent.model.entity.CommunityPost;
import com.lianba.aiagent.model.vo.LoginUserVO;
import com.lianba.aiagent.service.CommunityService;
import com.lianba.aiagent.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 情感轻社区控制器
 */
@RestController
@RequestMapping("/community")
public class CommunityController {

    @Resource
    private CommunityService communityService;

    @Resource
    private UserService userService;

    /**
     * 帖子列表（分页，按时间倒序，支持标签筛选）
     */
    @GetMapping("/posts")
    public BaseResponse<Page<CommunityPost>> listPosts(
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<CommunityPost> result = communityService.listPosts(tag, page, size);
        return ResultUtils.success(result);
    }

    /**
     * 发帖
     */
    @PostMapping("/post")
    public BaseResponse<CommunityPost> createPost(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        LoginUserVO loginUser = userService.getLoginUser(request);
        String content = body.get("content");
        String tag = body.get("tag");
        String nickname = body.get("nickname");
        CommunityPost post = communityService.createPost(loginUser.getId(), content, tag, nickname);
        return ResultUtils.success(post);
    }

    /**
     * 帖子详情 + 评论列表
     */
    @GetMapping("/post/{id}")
    public BaseResponse<Map<String, Object>> getPost(@PathVariable Long id) {
        CommunityPost post = communityService.getPost(id);
        List<CommunityComment> comments = communityService.listComments(id);
        Map<String, Object> result = new HashMap<>();
        result.put("post", post);
        result.put("comments", comments);
        return ResultUtils.success(result);
    }

    /**
     * 评论帖子
     */
    @PostMapping("/post/{id}/comment")
    public BaseResponse<CommunityComment> createComment(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        LoginUserVO loginUser = userService.getLoginUser(request);
        String content = body.get("content");
        String nickname = body.get("nickname");
        CommunityComment comment = communityService.createComment(loginUser.getId(), id, content, nickname);
        return ResultUtils.success(comment);
    }

    /**
     * 点赞帖子
     */
    @PostMapping("/post/{id}/like")
    public BaseResponse<String> likePost(@PathVariable Long id) {
        communityService.likePost(id);
        return ResultUtils.success("点赞成功");
    }

    /**
     * 删除帖子
     */
    @PostMapping("/post/{id}/delete")
    public BaseResponse<String> deletePost(@PathVariable Long id, HttpServletRequest request) {
        LoginUserVO loginUser = userService.getLoginUser(request);
        communityService.deletePost(loginUser.getId(), id);
        return ResultUtils.success("删除成功");
    }
}
