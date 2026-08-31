package com.lianba.aiagent.model.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 更新用户画像请求 DTO（Onboarding 情感状态选择）
 */
@Data
public class UpdateUserProfileDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 情感状态：single / dating / married
     */
    private String relationshipStatus;
}
