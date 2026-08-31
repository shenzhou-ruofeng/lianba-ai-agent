package com.lianba.aiagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lianba.aiagent.mapper.UsageRecordMapper;
import com.lianba.aiagent.mapper.UserMembershipMapper;
import com.lianba.aiagent.model.entity.UsageRecord;
import com.lianba.aiagent.model.entity.UserMembership;
import com.lianba.aiagent.model.vo.MembershipVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用量统计服务：记录用量 + 会员等级自动升级
 */
@Slf4j
@Service
public class UsageStatisticsService {

    /**
     * 会员等级配置：等级 -> {label, icon, 所需对话数, 所需日记数, 所需报告数}
     */
    private static final Map<String, TierConfig> TIER_CONFIGS = new LinkedHashMap<>();

    static {
        TIER_CONFIGS.put("bronze", new TierConfig("铜牌会员", "🥉", 0, 0, 0));
        TIER_CONFIGS.put("silver", new TierConfig("银牌会员", "🥈", 20, 3, 0));
        TIER_CONFIGS.put("gold", new TierConfig("金牌会员", "🥇", 100, 10, 5));
        TIER_CONFIGS.put("diamond", new TierConfig("钻石会员", "💎", 500, 50, 20));
    }

    @Resource
    private UsageRecordMapper usageRecordMapper;

    @Resource
    private UserMembershipMapper userMembershipMapper;

    /**
     * 记录一次用量（异步安全，失败不影响主流程）
     */
    public void recordUsage(Long userId, String recordType, String modelName, int tokenCount) {
        try {
            UsageRecord record = new UsageRecord();
            record.setUserId(userId);
            record.setRecordType(recordType);
            record.setModelName(modelName);
            record.setTokenCount(tokenCount);
            record.setCreateTime(new Date());
            usageRecordMapper.insert(record);

            // 记录后检查是否需要升级会员等级
            checkAndUpgradeTier(userId);
        } catch (Exception e) {
            log.warn("记录用量失败: userId={}, type={}, error={}", userId, recordType, e.getMessage());
        }
    }

    /**
     * 查询今日某类型使用量
     */
    public long getTodayUsage(Long userId, String recordType) {
        Date todayStart = Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant());
        LambdaQueryWrapper<UsageRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UsageRecord::getUserId, userId)
                .eq(UsageRecord::getRecordType, recordType)
                .ge(UsageRecord::getCreateTime, todayStart);
        return usageRecordMapper.selectCount(wrapper);
    }

    /**
     * 获取累计某类型使用量
     */
    public long getTotalUsage(Long userId, String recordType) {
        LambdaQueryWrapper<UsageRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UsageRecord::getUserId, userId)
                .eq(UsageRecord::getRecordType, recordType);
        return usageRecordMapper.selectCount(wrapper);
    }

    /**
     * 获取用量汇总（供前端展示）
     */
    public MembershipVO getUsageSummary(Long userId) {
        MembershipVO vo = new MembershipVO();

        // 累计统计
        vo.setTotalChats(getTotalUsage(userId, "chat_message"));
        vo.setTotalDiaries(getTotalUsage(userId, "diary_create"));
        vo.setTotalReports(getTotalUsage(userId, "report_generate"));

        // 今日统计
        vo.setTodayChats(getTodayUsage(userId, "chat_message"));
        vo.setTodayDiaries(getTodayUsage(userId, "diary_create"));

        // 会员等级
        UserMembership membership = getOrCreateMembership(userId);
        String tier = membership.getTier();
        vo.setTier(tier);

        TierConfig config = TIER_CONFIGS.get(tier);
        vo.setTierLabel(config.label);
        vo.setTierIcon(config.icon);

        // 计算下一等级和进度
        String[] tierOrder = {"bronze", "silver", "gold", "diamond"};
        int currentIndex = java.util.Arrays.asList(tierOrder).indexOf(tier);
        if (currentIndex < tierOrder.length - 1) {
            String nextTier = tierOrder[currentIndex + 1];
            vo.setNextTier(nextTier);
            TierConfig nextConfig = TIER_CONFIGS.get(nextTier);

            // 进度取三个维度中最接近的那个
            int chatProgress = nextConfig.requiredChats > 0
                    ? (int) Math.min(100, vo.getTotalChats() * 100 / nextConfig.requiredChats) : 100;
            int diaryProgress = nextConfig.requiredDiaries > 0
                    ? (int) Math.min(100, vo.getTotalDiaries() * 100 / nextConfig.requiredDiaries) : 100;
            int reportProgress = nextConfig.requiredReports > 0
                    ? (int) Math.min(100, vo.getTotalReports() * 100 / nextConfig.requiredReports) : 100;
            vo.setProgress(Math.min(Math.min(chatProgress, diaryProgress), reportProgress));
        } else {
            vo.setNextTier(null);
            vo.setProgress(100);
        }

        return vo;
    }

    /**
     * 检查并自动升级会员等级
     */
    public void checkAndUpgradeTier(Long userId) {
        try {
            long totalChats = getTotalUsage(userId, "chat_message");
            long totalDiaries = getTotalUsage(userId, "diary_create");
            long totalReports = getTotalUsage(userId, "report_generate");

            UserMembership membership = getOrCreateMembership(userId);
            String currentTier = membership.getTier();

            // 从高到低检查，找到最高满足条件的等级
            String targetTier = "bronze";
            for (Map.Entry<String, TierConfig> entry : TIER_CONFIGS.entrySet()) {
                TierConfig config = entry.getValue();
                if (totalChats >= config.requiredChats
                        && totalDiaries >= config.requiredDiaries
                        && totalReports >= config.requiredReports) {
                    targetTier = entry.getKey();
                }
            }

            // 如果等级提升，更新记录
            if (!targetTier.equals(currentTier)) {
                UserMembership update = new UserMembership();
                update.setId(membership.getId());
                update.setTier(targetTier);
                update.setUpgradeTime(new Date());
                update.setUpdateTime(new Date());
                userMembershipMapper.updateById(update);
                log.info("用户 {} 会员等级升级: {} -> {}", userId, currentTier, targetTier);
            }
        } catch (Exception e) {
            log.warn("检查会员升级失败: userId={}, error={}", userId, e.getMessage());
        }
    }

    /**
     * 获取或创建会员记录（首次访问自动创建 bronze）
     */
    private UserMembership getOrCreateMembership(Long userId) {
        LambdaQueryWrapper<UserMembership> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserMembership::getUserId, userId);
        UserMembership membership = userMembershipMapper.selectOne(wrapper);
        if (membership == null) {
            membership = new UserMembership();
            membership.setUserId(userId);
            membership.setTier("bronze");
            membership.setCreateTime(new Date());
            membership.setUpdateTime(new Date());
            membership.setUpgradeTime(new Date());
            userMembershipMapper.insert(membership);
        }
        return membership;
    }

    /**
     * 等级配置
     */
    private record TierConfig(String label, String icon, long requiredChats, long requiredDiaries, long requiredReports) {
    }
}
