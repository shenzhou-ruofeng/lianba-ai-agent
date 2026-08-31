package com.lianba.aiagent.common;

import com.lianba.aiagent.exception.ErrorCode;

/**
 * 响应结果工具类，快捷构造成功 / 失败响应
 */
public class ResultUtils {

    private ResultUtils() {
    }

    /**
     * 成功
     */
    public static <T> BaseResponse<T> success(T data) {
        return new BaseResponse<>(ErrorCode.SUCCESS.getCode(), data, "ok");
    }

    /**
     * 失败（按错误码）
     */
    public static <T> BaseResponse<T> error(ErrorCode errorCode) {
        return new BaseResponse<>(errorCode);
    }

    /**
     * 失败（自定义状态码与信息）
     */
    public static <T> BaseResponse<T> error(int code, String message) {
        return new BaseResponse<>(code, null, message);
    }

    /**
     * 失败（按错误码，自定义信息）
     */
    public static <T> BaseResponse<T> error(ErrorCode errorCode, String message) {
        return new BaseResponse<>(errorCode.getCode(), null, message);
    }
}
