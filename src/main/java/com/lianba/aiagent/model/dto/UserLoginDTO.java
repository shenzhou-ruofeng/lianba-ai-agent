package com.lianba.aiagent.model.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户登录请求 DTO
 */
@Data
public class UserLoginDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 账号（邮箱）
     */
    private String userAccount;

    /**
     * 密码
     */
    private String userPassword;
}
