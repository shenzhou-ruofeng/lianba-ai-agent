package com.lianba.aiagent.controller;

import com.lianba.aiagent.common.BaseResponse;
import com.lianba.aiagent.common.ResultUtils;
import com.lianba.aiagent.model.vo.LoginUserVO;
import com.lianba.aiagent.model.vo.MembershipVO;
import com.lianba.aiagent.service.UsageStatisticsService;
import com.lianba.aiagent.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会员等级控制器
 */
@RestController
@RequestMapping("/membership")
public class MembershipController {

    @Resource
    private UsageStatisticsService usageStatisticsService;

    @Resource
    private UserService userService;

    /**
     * 获取当前用户会员等级 + 进度 + 用量统计
     */
    @GetMapping("/status")
    public BaseResponse<MembershipVO> getMembershipStatus(HttpServletRequest request) {
        LoginUserVO loginUser = userService.getLoginUser(request);
        MembershipVO vo = usageStatisticsService.getUsageSummary(loginUser.getId());
        return ResultUtils.success(vo);
    }
}
