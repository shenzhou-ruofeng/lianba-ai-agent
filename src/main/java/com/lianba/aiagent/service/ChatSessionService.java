package com.lianba.aiagent.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lianba.aiagent.exception.BusinessException;
import com.lianba.aiagent.exception.ErrorCode;
import com.lianba.aiagent.exception.ThrowUtils;
import com.lianba.aiagent.mapper.ChatMessageMapper;
import com.lianba.aiagent.mapper.ChatSessionMapper;
import com.lianba.aiagent.model.dto.ChatMessageBatchSaveDTO;
import com.lianba.aiagent.model.dto.ChatMessageSaveDTO;
import com.lianba.aiagent.model.dto.SessionCreateDTO;
import com.lianba.aiagent.model.dto.SessionUpdateDTO;
import com.lianba.aiagent.model.entity.ChatMessage;
import com.lianba.aiagent.model.entity.ChatSession;
import com.lianba.aiagent.model.vo.ChatMessageVO;
import com.lianba.aiagent.model.vo.SessionVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 会话与消息服务：提供会话 CRUD + 消息保存/查询能力
 */
@Slf4j
@Service
public class ChatSessionService {

    @Resource
    private ChatSessionMapper chatSessionMapper;

    @Resource
    private ChatMessageMapper chatMessageMapper;

    // ==================== 会话 CRUD ====================

    /**
     * 创建新会话
     *
     * @param userId 当前用户 ID
     * @param dto    创建参数
     * @return 会话 VO
     */
    public SessionVO createSession(Long userId, SessionCreateDTO dto) {
        ThrowUtils.throwIf(dto == null || StrUtil.isBlank(dto.getAgentType()),
                ErrorCode.PARAMS_ERROR, "智能体类型不能为空");

        // 生成会话 ID（与前端规则一致：love_xxxx / super_xxxx）
        String prefix = "super".equals(dto.getAgentType()) ? "super" : "love";
        String sessionId = prefix + "_" + Long.toHexString(System.nanoTime()) + Long.toHexString((long) (Math.random() * 1000));

        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setSessionId(sessionId);
        session.setAgentType(dto.getAgentType());
        session.setTitle(StrUtil.isNotBlank(dto.getTitle()) ? dto.getTitle() : "新会话");
        session.setChatMode(StrUtil.isNotBlank(dto.getChatMode()) ? dto.getChatMode() : "chat");
        session.setMatchGender(dto.getMatchGender());
        session.setCreateTime(new Date());
        session.setUpdateTime(new Date());

        chatSessionMapper.insert(session);
        log.info("创建会话: sessionId={}, userId={}, agentType={}", sessionId, userId, dto.getAgentType());
        return toSessionVO(session);
    }

    /**
     * 查询当前用户的会话列表（按更新时间倒序）
     *
     * @param userId    当前用户 ID
     * @param agentType 智能体类型过滤（可选）
     * @return 会话列表
     */
    public List<SessionVO> listSessions(Long userId, String agentType) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getUserId, userId);
        if (StrUtil.isNotBlank(agentType)) {
            wrapper.eq(ChatSession::getAgentType, agentType);
        }
        wrapper.orderByDesc(ChatSession::getUpdateTime);

        List<ChatSession> sessions = chatSessionMapper.selectList(wrapper);
        return sessions.stream().map(this::toSessionVO).toList();
    }

    /**
     * 更新会话信息（标题、模式等）
     *
     * @param userId 当前用户 ID（权限校验）
     * @param dto    更新参数
     * @return 更新后的会话 VO
     */
    public SessionVO updateSession(Long userId, SessionUpdateDTO dto) {
        ThrowUtils.throwIf(dto == null || StrUtil.isBlank(dto.getSessionId()),
                ErrorCode.PARAMS_ERROR, "会话 ID 不能为空");

        ChatSession session = getSessionBySessionId(dto.getSessionId());
        ThrowUtils.throwIf(session == null, ErrorCode.NOT_FOUND_ERROR, "会话不存在");
        ThrowUtils.throwIf(!session.getUserId().equals(userId),
                ErrorCode.NO_AUTH_ERROR, "无权修改他人的会话");

        // 按需更新非空字段
        LambdaUpdateWrapper<ChatSession> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(ChatSession::getId, session.getId());
        if (StrUtil.isNotBlank(dto.getTitle())) {
            updateWrapper.set(ChatSession::getTitle, dto.getTitle());
        }
        if (StrUtil.isNotBlank(dto.getChatMode())) {
            updateWrapper.set(ChatSession::getChatMode, dto.getChatMode());
        }
        if (dto.getMatchGender() != null) {
            updateWrapper.set(ChatSession::getMatchGender, dto.getMatchGender());
        }
        updateWrapper.set(ChatSession::getUpdateTime, new Date());

        chatSessionMapper.update(null, updateWrapper);
        return toSessionVO(chatSessionMapper.selectById(session.getId()));
    }

    /**
     * 删除会话（软删除，同时删除关联消息）
     *
     * @param userId    当前用户 ID
     * @param sessionId 会话 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(Long userId, String sessionId) {
        ThrowUtils.throwIf(StrUtil.isBlank(sessionId), ErrorCode.PARAMS_ERROR, "会话 ID 不能为空");

        ChatSession session = getSessionBySessionId(sessionId);
        ThrowUtils.throwIf(session == null, ErrorCode.NOT_FOUND_ERROR, "会话不存在");
        ThrowUtils.throwIf(!session.getUserId().equals(userId),
                ErrorCode.NO_AUTH_ERROR, "无权删除他人的会话");

        // 软删除会话
        chatSessionMapper.deleteById(session.getId());

        // 软删除关联消息
        LambdaUpdateWrapper<ChatMessage> msgWrapper = new LambdaUpdateWrapper<>();
        msgWrapper.eq(ChatMessage::getSessionId, sessionId)
                .set(ChatMessage::getIsDeleted, 1);
        chatMessageMapper.update(null, msgWrapper);

        log.info("删除会话: sessionId={}, userId={}", sessionId, userId);
    }

    // ==================== 消息保存/查询 ====================

    /**
     * 保存单条消息
     *
     * @param userId 当前用户 ID
     * @param dto    消息参数
     * @return 消息 VO
     */
    public ChatMessageVO saveMessage(Long userId, ChatMessageSaveDTO dto) {
        ThrowUtils.throwIf(dto == null || StrUtil.isBlank(dto.getSessionId()) || StrUtil.isBlank(dto.getRole()),
                ErrorCode.PARAMS_ERROR, "会话 ID 和角色不能为空");

        // 确保会话存在（前端首次对话时可能尚未调用创建接口，自动创建）
        ensureSessionExists(userId, dto.getSessionId());

        ChatMessage message = new ChatMessage();
        message.setSessionId(dto.getSessionId());
        message.setUserId(userId);
        message.setRole(dto.getRole());
        message.setContent(StrUtil.isNotBlank(dto.getContent()) ? dto.getContent() : "");
        message.setMsgType(StrUtil.isNotBlank(dto.getMsgType()) ? dto.getMsgType() : "text");
        message.setCreateTime(new Date());

        chatMessageMapper.insert(message);

        // 更新会话的 updateTime（保持会话列表排序正确）
        LambdaUpdateWrapper<ChatSession> sessionWrapper = new LambdaUpdateWrapper<>();
        sessionWrapper.eq(ChatSession::getSessionId, dto.getSessionId())
                .set(ChatSession::getUpdateTime, new Date());
        chatSessionMapper.update(null, sessionWrapper);

        return toMessageVO(message);
    }

    /**
     * 批量保存消息
     *
     * @param userId 当前用户 ID
     * @param dto    批量消息参数
     * @return 保存的消息 VO 列表
     */
    @Transactional(rollbackFor = Exception.class)
    public List<ChatMessageVO> batchSaveMessages(Long userId, ChatMessageBatchSaveDTO dto) {
        ThrowUtils.throwIf(dto == null || StrUtil.isBlank(dto.getSessionId()),
                ErrorCode.PARAMS_ERROR, "会话 ID 不能为空");
        ThrowUtils.throwIf(dto.getMessages() == null || dto.getMessages().isEmpty(),
                ErrorCode.PARAMS_ERROR, "消息列表不能为空");

        ensureSessionExists(userId, dto.getSessionId());

        List<ChatMessageVO> result = new ArrayList<>();
        for (ChatMessageSaveDTO msgDTO : dto.getMessages()) {
            ChatMessage message = new ChatMessage();
            message.setSessionId(dto.getSessionId());
            message.setUserId(userId);
            message.setRole(StrUtil.isNotBlank(msgDTO.getRole()) ? msgDTO.getRole() : "user");
            message.setContent(StrUtil.isNotBlank(msgDTO.getContent()) ? msgDTO.getContent() : "");
            message.setMsgType(StrUtil.isNotBlank(msgDTO.getMsgType()) ? msgDTO.getMsgType() : "text");
            message.setCreateTime(new Date());
            chatMessageMapper.insert(message);
            result.add(toMessageVO(message));
        }

        // 更新会话的 updateTime
        LambdaUpdateWrapper<ChatSession> sessionWrapper = new LambdaUpdateWrapper<>();
        sessionWrapper.eq(ChatSession::getSessionId, dto.getSessionId())
                .set(ChatSession::getUpdateTime, new Date());
        chatSessionMapper.update(null, sessionWrapper);

        return result;
    }

    /**
     * 查询会话的消息列表（按创建时间正序）
     *
     * @param userId    当前用户 ID
     * @param sessionId 会话 ID
     * @return 消息列表
     */
    public List<ChatMessageVO> listMessages(Long userId, String sessionId) {
        ThrowUtils.throwIf(StrUtil.isBlank(sessionId), ErrorCode.PARAMS_ERROR, "会话 ID 不能为空");

        // 权限校验
        ChatSession session = getSessionBySessionId(sessionId);
        ThrowUtils.throwIf(session == null, ErrorCode.NOT_FOUND_ERROR, "会话不存在");
        ThrowUtils.throwIf(!session.getUserId().equals(userId),
                ErrorCode.NO_AUTH_ERROR, "无权查看他人的消息");

        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getSessionId, sessionId)
                .orderByAsc(ChatMessage::getCreateTime);

        List<ChatMessage> messages = chatMessageMapper.selectList(wrapper);
        return messages.stream().map(this::toMessageVO).toList();
    }

    // ==================== 内部方法 ====================

    /**
     * 按 sessionId 查询会话实体
     */
    private ChatSession getSessionBySessionId(String sessionId) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getSessionId, sessionId);
        return chatSessionMapper.selectOne(wrapper);
    }

    /**
     * 确保会话存在（不存在时自动创建，兼容前端先对话后建会话的场景）
     */
    private void ensureSessionExists(Long userId, String sessionId) {
        if (getSessionBySessionId(sessionId) != null) {
            return;
        }
        // 根据 sessionId 前缀推断 agentType
        String agentType = sessionId.startsWith("super") ? "super" : "love";
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setSessionId(sessionId);
        session.setAgentType(agentType);
        session.setTitle("新会话");
        session.setChatMode("chat");
        session.setCreateTime(new Date());
        session.setUpdateTime(new Date());
        chatSessionMapper.insert(session);
        log.info("自动创建会话: sessionId={}, userId={}", sessionId, userId);
    }

    private SessionVO toSessionVO(ChatSession session) {
        SessionVO vo = new SessionVO();
        vo.setSessionId(session.getSessionId());
        vo.setAgentType(session.getAgentType());
        vo.setTitle(session.getTitle());
        vo.setChatMode(session.getChatMode());
        vo.setMatchGender(session.getMatchGender());
        vo.setCreateTime(session.getCreateTime());
        vo.setUpdateTime(session.getUpdateTime());
        return vo;
    }

    private ChatMessageVO toMessageVO(ChatMessage message) {
        ChatMessageVO vo = new ChatMessageVO();
        vo.setId(message.getId());
        vo.setSessionId(message.getSessionId());
        vo.setRole(message.getRole());
        vo.setContent(message.getContent());
        vo.setMsgType(message.getMsgType());
        vo.setCreateTime(message.getCreateTime());
        return vo;
    }
}
