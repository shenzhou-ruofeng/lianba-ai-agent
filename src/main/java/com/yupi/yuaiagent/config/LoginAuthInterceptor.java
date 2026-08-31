package com.yupi.yuaiagent.config;

import com.yupi.yuaiagent.exception.BusinessException;
import com.yupi.yuaiagent.exception.ErrorCode;
import com.yupi.yuaiagent.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录鉴权拦截器：只允许登录用户调用 AI 服务
 */
@Slf4j
@Component
public class LoginAuthInterceptor implements HandlerInterceptor {

    private final UserService userService;

    public LoginAuthInterceptor(UserService userService) {
        this.userService = userService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 放行预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (userService.getLoginUserIfPresent(request) == null) {
            log.warn("未登录用户访问受保护接口: {}", request.getRequestURI());
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "请先登录后再使用 AI 服务");
        }
        return true;
    }
}
