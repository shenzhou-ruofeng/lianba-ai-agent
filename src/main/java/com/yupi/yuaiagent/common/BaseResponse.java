package com.yupi.yuaiagent.common;

import com.yupi.yuaiagent.exception.ErrorCode;
import lombok.Data;

import java.io.Serializable;

/**
 * 通用接口响应封装类
 *
 * @param <T> 数据类型
 */
@Data
public class BaseResponse<T> implements Serializable {

    /**
     * 状态码（0 表示成功）
     */
    private int code;

    /**
     * 响应数据
     */
    private T data;

    /**
     * 提示信息
     */
    private String message;

    public BaseResponse(int code, T data, String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }

    public BaseResponse(int code, T data) {
        this(code, data, "");
    }

    public BaseResponse(ErrorCode errorCode) {
        this(errorCode.getCode(), null, errorCode.getMessage());
    }
}
