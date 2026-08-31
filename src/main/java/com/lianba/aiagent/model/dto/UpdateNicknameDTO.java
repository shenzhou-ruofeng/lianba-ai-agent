package com.lianba.aiagent.model.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 更新用户昵称请求 DTO
 */
@Data
public class UpdateNicknameDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 新昵称
     */
    private String userName;
}
