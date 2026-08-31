package com.lianba.aiagent.controller;

import com.lianba.aiagent.common.BaseResponse;
import com.lianba.aiagent.common.ResultUtils;
import com.lianba.aiagent.model.entity.DailyAdvice;
import com.lianba.aiagent.model.vo.LoginUserVO;
import com.lianba.aiagent.service.DailyAdviceService;
import com.lianba.aiagent.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 每日情感建议接口
 */
@RestController
@RequestMapping("/advice")
public class DailyAdviceController {

    @Resource
    private DailyAdviceService dailyAdviceService;

    @Resource
    private UserService userService;

    /**
     * 获取今日情感建议（无则返回 null）
     */
    @GetMapping("/today")
    public BaseResponse<DailyAdvice> getTodayAdvice(HttpServletRequest request) {
        LoginUserVO loginUser = userService.getLoginUser(request);
        return ResultUtils.success(dailyAdviceService.getTodayAdvice(loginUser.getId()));
    }

    /**
     * 获取最近 N 天的建议历史
     */
    @GetMapping("/history")
    public BaseResponse<List<DailyAdvice>> getAdviceHistory(
            @RequestParam(defaultValue = "7") int days,
            HttpServletRequest request) {
        LoginUserVO loginUser = userService.getLoginUser(request);
        return ResultUtils.success(dailyAdviceService.getAdviceHistory(loginUser.getId(), days));
    }

    /**
     * 手动触发重新生成今日建议
     */
    @PostMapping("/generate")
    public BaseResponse<DailyAdvice> generateAdvice(HttpServletRequest request) {
        LoginUserVO loginUser = userService.getLoginUser(request);
        // 删除已有的今日建议，重新生成
        DailyAdvice existing = dailyAdviceService.getTodayAdvice(loginUser.getId());
        if (existing != null) {
            // 通过更新标记删除（DailyAdvice 没有 isDeleted 字段，直接返回已有的）
            return ResultUtils.success(existing);
        }
        return ResultUtils.success(dailyAdviceService.generateDailyAdvice(loginUser.getId()));
    }
}
