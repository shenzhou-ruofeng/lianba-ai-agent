package com.lianba.aiagent.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 登录用户视图对象（脱敏后返回给前端 / 存入 Session）
 */
@Data
public class LoginUserVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户 ID
     */
    private Long id;

    /**
     * 账号
     */
    private String userAccount;

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户角色：user / admin
     */
    private String userRole;

    /**
     * 情感状态：single / dating / married（null 表示未设置）
     */
    private String relationshipStatus;

    /**
     * 创建时间
     */
    private Date createTime;
}
