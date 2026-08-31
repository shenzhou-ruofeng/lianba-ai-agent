package com.lianba.aiagent.config;

import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 拦截器注册：AI 服务相关接口需要登录后才能访问
 */
@Configuration
public class InterceptorConfig implements WebMvcConfigurer {

    @Resource
    private LoginAuthInterceptor loginAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginAuthInterceptor)
                // AI 对话、图片上传解析、RAG 知识库导入、会话管理均属于 AI 服务，需要登录
                .addPathPatterns("/ai/**", "/images/**", "/rag/**", "/session/**")
                // 文件下载、健康检查等无需登录
                .excludePathPatterns("/health");
    }
}
