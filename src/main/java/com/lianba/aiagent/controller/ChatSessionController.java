package com.lianba.aiagent.controller;

import com.lianba.aiagent.common.BaseResponse;
import com.lianba.aiagent.common.ResultUtils;
import com.lianba.aiagent.model.dto.ChatMessageBatchSaveDTO;
import com.lianba.aiagent.model.dto.ChatMessageSaveDTO;
import com.lianba.aiagent.model.dto.SessionCreateDTO;
import com.lianba.aiagent.model.dto.SessionUpdateDTO;
import com.lianba.aiagent.model.vo.ChatMessageVO;
import com.lianba.aiagent.model.vo.LoginUserVO;
import com.lianba.aiagent.model.vo.SessionVO;
import com.lianba.aiagent.service.ChatSessionService;
import com.lianba.aiagent.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 会话与消息接口：提供会话 CRUD + 消息保存/查询能力
 * <p>
 * 支撑前端会话列表管理 + 聊天记录持久化，替代原有前端 localStorage 方案。
 */
@RestController
@RequestMapping("/session")
public class ChatSessionController {

    @Resource
    private ChatSessionService chatSessionService;

    @Resource
    private UserService userService;

    // ==================== 会话接口 ====================

    /**
     * 创建新会话
     */
    @PostMapping("/create")
    public BaseResponse<SessionVO> createSession(@RequestBody SessionCreateDTO dto, HttpServletRequest request) {
        LoginUserVO loginUser = userService.getLoginUser(request);
        return ResultUtils.success(chatSessionService.createSession(loginUser.getId(), dto));
    }

    /**
     * 查询当前用户的会话列表
     *
     * @param agentType 智能体类型过滤（可选：love / super）
     */
    @GetMapping("/list")
    public BaseResponse<List<SessionVO>> listSessions(
            @RequestParam(required = false) String agentType, HttpServletRequest request) {
        LoginUserVO loginUser = userService.getLoginUser(request);
        return ResultUtils.success(chatSessionService.listSessions(loginUser.getId(), agentType));
    }

    /**
     * 更新会话信息（标题、模式等）
     */
    @PostMapping("/update")
    public BaseResponse<SessionVO> updateSession(@RequestBody SessionUpdateDTO dto, HttpServletRequest request) {
        LoginUserVO loginUser = userService.getLoginUser(request);
        return ResultUtils.success(chatSessionService.updateSession(loginUser.getId(), dto));
    }

    /**
     * 删除会话（软删除，同时删除关联消息）
     */
    @DeleteMapping("/delete")
    public BaseResponse<Boolean> deleteSession(@RequestParam String sessionId, HttpServletRequest request) {
        LoginUserVO loginUser = userService.getLoginUser(request);
        chatSessionService.deleteSession(loginUser.getId(), sessionId);
        return ResultUtils.success(true);
    }

    // ==================== 消息接口 ====================

    /**
     * 查询会话的消息列表（按时间正序）
     */
    @GetMapping("/messages")
    public BaseResponse<List<ChatMessageVO>> listMessages(@RequestParam String sessionId, HttpServletRequest request) {
        LoginUserVO loginUser = userService.getLoginUser(request);
        return ResultUtils.success(chatSessionService.listMessages(loginUser.getId(), sessionId));
    }

    /**
     * 保存单条消息
     */
    @PostMapping("/message/save")
    public BaseResponse<ChatMessageVO> saveMessage(@RequestBody ChatMessageSaveDTO dto, HttpServletRequest request) {
        LoginUserVO loginUser = userService.getLoginUser(request);
        return ResultUtils.success(chatSessionService.saveMessage(loginUser.getId(), dto));
    }

    /**
     * 批量保存消息
     */
    @PostMapping("/message/batch_save")
    public BaseResponse<List<ChatMessageVO>> batchSaveMessages(@RequestBody ChatMessageBatchSaveDTO dto,
                                                                HttpServletRequest request) {
        LoginUserVO loginUser = userService.getLoginUser(request);
        return ResultUtils.success(chatSessionService.batchSaveMessages(loginUser.getId(), dto));
    }
}
