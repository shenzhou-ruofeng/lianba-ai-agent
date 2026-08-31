package com.lianba.aiagent.task;

import com.lianba.aiagent.service.DailyAdviceService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每日情感建议定时任务：每天 8:00 为活跃用户生成建议
 */
@Slf4j
@Component
public class DailyAdviceTask {

    @Resource
    private DailyAdviceService dailyAdviceService;

    @Scheduled(cron = "0 0 8 * * ?")
    public void execute() {
        log.info("每日情感建议定时任务开始执行");
        try {
            dailyAdviceService.generateAllDailyAdvice();
        } catch (Exception e) {
            log.error("每日情感建议定时任务执行异常: {}", e.getMessage(), e);
        }
    }
}
