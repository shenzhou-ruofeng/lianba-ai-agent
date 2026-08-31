package com.yupi.yuaiagent.model.entity;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 用户实体
 */
@Data
public class User implements Serializable {

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
     * 密码（加密存储）
     */
    private String userPassword;

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户角色：user / admin
     */
    private String userRole;

    /**
     * 创建时间
     */
    private Date createTime;
}
