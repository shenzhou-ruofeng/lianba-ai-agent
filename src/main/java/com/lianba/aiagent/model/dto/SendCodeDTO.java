package com.lianba.aiagent.model.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 发送邮箱验证码请求 DTO
 */
@Data
public class SendCodeDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 接收验证码的邮箱
     */
    private String email;
}
