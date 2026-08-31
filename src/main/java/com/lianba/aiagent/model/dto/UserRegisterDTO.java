package com.lianba.aiagent.model.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户注册请求 DTO
 */
@Data
public class UserRegisterDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 邮箱（同时作为登录账号）
     */
    private String email;

    /**
     * 邮箱验证码
     */
    private String emailCode;

    /**
     * 密码
     */
    private String userPassword;

    /**
     * 确认密码
     */
    private String checkPassword;
}
