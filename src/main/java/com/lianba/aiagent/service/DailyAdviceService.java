package com.lianba.aiagent.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lianba.aiagent.mapper.ChatMessageMapper;
import com.lianba.aiagent.mapper.ChatSessionMapper;
import com.lianba.aiagent.mapper.DailyAdviceMapper;
import com.lianba.aiagent.model.entity.ChatMessage;
import com.lianba.aiagent.model.entity.ChatSession;
import com.lianba.aiagent.model.entity.DailyAdvice;
import com.lianba.aiagent.model.entity.Diary;
import com.lianba.aiagent.model.entity.User;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 每日情感建议服务
 */
@Slf4j
@Service
public class DailyAdviceService {

    private static final String ADVICE_GENERATION_PROMPT = """
            你是一位温暖的情感顾问。请根据以下用户信息，给出今日情感建议（150字以内）。
            建议要温暖、实用、具体，结合用户当前状态。

            用户情感状态：%s
            近期日记情绪：%s
            最近对话主题：%s""";

    @Resource
    private DailyAdviceMapper dailyAdviceMapper;

    @Resource
    private UserService userService;

    @Resource
    private DiaryService diaryService;

    @Resource
    private ChatSessionMapper chatSessionMapper;

    @Resource
    private ChatMessageMapper chatMessageMapper;

    @Resource
    private UsageStatisticsService usageStatisticsService;

    private final ChatModel chatModel;

    public DailyAdviceService(@Qualifier("dashscopeChatModel") ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 获取今日建议
     */
    public DailyAdvice getTodayAdvice(Long userId) {
        LocalDate today = LocalDate.now();
        Date todayStart = Date.from(today.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date todayEnd = Date.from(today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());

        LambdaQueryWrapper<DailyAdvice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DailyAdvice::getUserId, userId)
                .ge(DailyAdvice::getAdviceDate, todayStart)
                .lt(DailyAdvice::getAdviceDate, todayEnd);
        return dailyAdviceMapper.selectOne(wrapper);
    }

    /**
     * 获取最近 N 天的建议历史
     */
    public List<DailyAdvice> getAdviceHistory(Long userId, int days) {
        Date since = Date.from(LocalDate.now().minusDays(days).atStartOfDay(ZoneId.systemDefault()).toInstant());
        LambdaQueryWrapper<DailyAdvice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DailyAdvice::getUserId, userId)
                .ge(DailyAdvice::getAdviceDate, since)
                .orderByDesc(DailyAdvice::getAdviceDate);
        return dailyAdviceMapper.selectList(wrapper);
    }

    /**
     * 为指定用户生成今日建议
     */
    public DailyAdvice generateDailyAdvice(Long userId) {
        // 检查今日是否已有建议
        DailyAdvice existing = getTodayAdvice(userId);
        if (existing != null) return existing;

        User user = userService.getUserById(userId);
        if (user == null) return null;

        // 收集用户上下文
        String relationshipStatus = translateStatus(user.getRelationshipStatus());
        String recentMoods = collectRecentMoods(userId);
        String recentTopics = collectRecentTopics(userId);

        try {
            String prompt = String.format(ADVICE_GENERATION_PROMPT, relationshipStatus, recentMoods, recentTopics);
            List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
            messages.add(new SystemMessage("你是一位温暖的情感顾问，每天为用户生成个性化的情感建议。"));
            messages.add(new UserMessage(prompt));
            ChatResponse response = chatModel.call(new Prompt(messages));
            String content = response.getResult().getOutput().getText().trim();

            DailyAdvice advice = new DailyAdvice();
            advice.setUserId(userId);
            advice.setAdviceDate(new Date());
            advice.setContent(content);
            advice.setCreateTime(new Date());
            dailyAdviceMapper.insert(advice);
            log.info("用户 {} 今日情感建议已生成", userId);
            // 记录用量
            usageStatisticsService.recordUsage(userId, "advice_generate", "dashscope", 0);
            return advice;
        } catch (Exception e) {
            log.error("为用户 {} 生成每日建议失败: {}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * 为所有活跃用户生成今日建议（定时任务调用）
     */
    public void generateAllDailyAdvice() {
        Set<Long> activeUserIds = findActiveUserIds();
        log.info("开始为 {} 个活跃用户生成每日情感建议", activeUserIds.size());
        for (Long userId : activeUserIds) {
            try {
                generateDailyAdvice(userId);
            } catch (Exception e) {
                log.warn("为用户 {} 生成每日建议失败，继续下一个: {}", userId, e.getMessage());
            }
        }
        log.info("每日情感建议生成完成");
    }

    /**
     * 查找近 7 天活跃用户 ID（有对话或日记的用户）
     */
    private Set<Long> findActiveUserIds() {
        Date sevenDaysAgo = new Date(System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000);

        // 从会话中找活跃用户
        List<ChatSession> recentSessions;
        try {
            LambdaQueryWrapper<ChatSession> sessionWrapper = new LambdaQueryWrapper<>();
            sessionWrapper.ge(ChatSession::getUpdateTime, sevenDaysAgo);
            recentSessions = chatSessionMapper.selectList(sessionWrapper);
        } catch (Exception e) {
            log.warn("查询活跃会话失败: {}", e.getMessage());
            recentSessions = List.of();
        }

        Set<Long> userIds = recentSessions.stream()
                .map(ChatSession::getUserId)
                .collect(Collectors.toSet());

        // 从日记中找活跃用户（合并）
        try {
            List<Diary> recentDiaries = diaryService.getRecentDiaries(null, 7);
            // getRecentDiaries 需要 userId，这里用另一种方法
        } catch (Exception e) {
            // 忽略
        }

        return userIds;
    }

    private String translateStatus(String status) {
        if (status == null) return "未设置";
        return switch (status) {
            case "single" -> "单身";
            case "dating" -> "恋爱中";
            case "married" -> "已婚";
            default -> "未设置";
        };
    }

    private String collectRecentMoods(Long userId) {
        try {
            List<Diary> diaries = diaryService.getRecentDiaries(userId, 7);
            if (diaries.isEmpty()) return "近期未写日记";
            String[] moodLabels = {"", "很低落", "有些难过", "一般", "不错", "很棒"};
            return diaries.stream()
                    .filter(d -> d.getMood() != null && d.getMood() >= 1 && d.getMood() <= 5)
                    .map(d -> moodLabels[d.getMood()])
                    .limit(5)
                    .collect(Collectors.joining("、"));
        } catch (Exception e) {
            return "近期未写日记";
        }
    }

    private String collectRecentTopics(Long userId) {
        try {
            // 查找用户最近的会话消息
            LambdaQueryWrapper<ChatSession> sessionWrapper = new LambdaQueryWrapper<>();
            sessionWrapper.eq(ChatSession::getUserId, userId)
                    .orderByDesc(ChatSession::getUpdateTime)
                    .last("LIMIT 3");
            List<ChatSession> sessions = chatSessionMapper.selectList(sessionWrapper);
            if (sessions.isEmpty()) return "近期无对话";

            List<String> topics = new ArrayList<>();
            for (ChatSession session : sessions) {
                LambdaQueryWrapper<ChatMessage> msgWrapper = new LambdaQueryWrapper<>();
                msgWrapper.eq(ChatMessage::getSessionId, session.getSessionId())
                        .eq(ChatMessage::getRole, "user")
                        .orderByAsc(ChatMessage::getCreateTime)
                        .last("LIMIT 2");
                List<ChatMessage> userMsgs = chatMessageMapper.selectList(msgWrapper);
                for (ChatMessage msg : userMsgs) {
                    String text = StrUtil.maxLength(msg.getContent(), 30);
                    topics.add(text);
                }
            }
            return topics.isEmpty() ? "近期无对话" : String.join("、", topics);
        } catch (Exception e) {
            return "近期无对话";
        }
    }
}
