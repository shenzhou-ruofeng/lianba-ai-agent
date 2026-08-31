package com.lianba.aiagent.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lianba.aiagent.exception.BusinessException;
import com.lianba.aiagent.exception.ErrorCode;
import com.lianba.aiagent.exception.ThrowUtils;
import com.lianba.aiagent.mapper.DiaryMapper;
import com.lianba.aiagent.model.entity.Diary;
import com.lianba.aiagent.model.vo.DiaryVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 情感日记服务：CRUD + AI 情绪分析
 */
@Slf4j
@Service
public class DiaryService {

    private static final String MOOD_ANALYSIS_PROMPT = """
            你是一位温暖的情感顾问。请根据用户的日记内容，给出简短的情绪分析和建议（200字以内）。
            格式：
            【情绪状态】用几个字概括当前情绪
            【分析】简短分析情绪来源和心理状态
            【建议】给出1-2条温暖实用的建议""";

    private static final String[] MOOD_LABELS = {"", "很低落", "有些难过", "一般", "不错", "很棒"};

    @Resource
    private DiaryMapper diaryMapper;

    private final ChatModel chatModel;

    public DiaryService(@Qualifier("dashscopeChatModel") ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 创建日记（创建后自动触发 AI 分析）
     */
    public DiaryVO createDiary(Long userId, String content, Integer mood, List<String> tags) {
        ThrowUtils.throwIf(StrUtil.isBlank(content), ErrorCode.PARAMS_ERROR, "日记内容不能为空");
        ThrowUtils.throwIf(content.length() > 5000, ErrorCode.PARAMS_ERROR, "日记内容不能超过 5000 字");
        if (mood != null) {
            ThrowUtils.throwIf(mood < 1 || mood > 5, ErrorCode.PARAMS_ERROR, "情绪评分需在 1-5 之间");
        }

        Diary diary = new Diary();
        diary.setUserId(userId);
        diary.setContent(content);
        diary.setMood(mood);
        diary.setTags(tags != null ? new JSONArray(tags).toString() : null);
        diary.setCreateTime(new Date());
        diary.setUpdateTime(new Date());
        diaryMapper.insert(diary);

        // 异步触发 AI 分析（失败不影响日记保存）
        try {
            String analysis = analyzeMoodContent(content);
            Diary update = new Diary();
            update.setId(diary.getId());
            update.setAiAnalysis(analysis);
            update.setUpdateTime(new Date());
            diaryMapper.updateById(update);
            diary.setAiAnalysis(analysis);
        } catch (Exception e) {
            log.warn("日记 AI 分析失败，diaryId={}: {}", diary.getId(), e.getMessage());
        }

        return toDiaryVO(diary);
    }

    /**
     * 获取用户日记列表（按时间倒序）
     */
    public List<DiaryVO> listDiaries(Long userId) {
        LambdaQueryWrapper<Diary> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Diary::getUserId, userId)
                .orderByDesc(Diary::getCreateTime);
        List<Diary> diaries = diaryMapper.selectList(wrapper);
        return diaries.stream().map(this::toDiaryVO).toList();
    }

    /**
     * 获取日记详情
     */
    public DiaryVO getDiary(Long userId, Long diaryId) {
        Diary diary = diaryMapper.selectById(diaryId);
        ThrowUtils.throwIf(diary == null || !diary.getUserId().equals(userId), ErrorCode.NOT_FOUND_ERROR, "日记不存在");
        return toDiaryVO(diary);
    }

    /**
     * 删除日记
     */
    public void deleteDiary(Long userId, Long diaryId) {
        Diary diary = diaryMapper.selectById(diaryId);
        ThrowUtils.throwIf(diary == null || !diary.getUserId().equals(userId), ErrorCode.NOT_FOUND_ERROR, "日记不存在");
        diaryMapper.deleteById(diaryId);
    }

    /**
     * 手动重新触发 AI 分析
     */
    public DiaryVO reanalyze(Long userId, Long diaryId) {
        Diary diary = diaryMapper.selectById(diaryId);
        ThrowUtils.throwIf(diary == null || !diary.getUserId().equals(userId), ErrorCode.NOT_FOUND_ERROR, "日记不存在");

        try {
            String analysis = analyzeMoodContent(diary.getContent());
            Diary update = new Diary();
            update.setId(diary.getId());
            update.setAiAnalysis(analysis);
            update.setUpdateTime(new Date());
            diaryMapper.updateById(update);
            diary.setAiAnalysis(analysis);
        } catch (Exception e) {
            log.error("日记 AI 分析失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 分析失败，请稍后重试");
        }
        return toDiaryVO(diary);
    }

    /**
     * 获取用户最近 N 天的日记情绪列表（供每日建议使用）
     */
    public List<Diary> getRecentDiaries(Long userId, int days) {
        LambdaQueryWrapper<Diary> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Diary::getUserId, userId)
                .ge(Diary::getCreateTime, new Date(System.currentTimeMillis() - (long) days * 24 * 60 * 60 * 1000))
                .orderByDesc(Diary::getCreateTime);
        return diaryMapper.selectList(wrapper);
    }

    /**
     * 调用 AI 模型分析日记内容的情绪
     */
    private String analyzeMoodContent(String content) {
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(MOOD_ANALYSIS_PROMPT));
        messages.add(new UserMessage(content));
        ChatResponse response = chatModel.call(new Prompt(messages));
        return response.getResult().getOutput().getText().trim();
    }

    /**
     * 实体转 VO
     */
    private DiaryVO toDiaryVO(Diary diary) {
        DiaryVO vo = new DiaryVO();
        vo.setId(diary.getId());
        vo.setContent(diary.getContent());
        vo.setMood(diary.getMood());
        vo.setMoodLabel(diary.getMood() != null && diary.getMood() >= 1 && diary.getMood() <= 5
                ? MOOD_LABELS[diary.getMood()] : "");
        vo.setAiAnalysis(diary.getAiAnalysis());
        vo.setCreateTime(diary.getCreateTime());
        // 解析 tags JSON
        if (StrUtil.isNotBlank(diary.getTags())) {
            try {
                vo.setTags(new JSONArray(diary.getTags()).toList(String.class));
            } catch (Exception e) {
                vo.setTags(List.of());
            }
        } else {
            vo.setTags(List.of());
        }
        return vo;
    }
}
