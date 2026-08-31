package com.lianba.aiagent.exception;

/**
 * 异常抛出工具类，条件成立即抛出业务异常，简化参数校验代码
 */
public class ThrowUtils {

    private ThrowUtils() {
    }

    /**
     * 条件成立则抛出指定运行时异常
     */
    public static void throwIf(boolean condition, RuntimeException runtimeException) {
        if (condition) {
            throw runtimeException;
        }
    }

    /**
     * 条件成立则按错误码抛出业务异常
     */
    public static void throwIf(boolean condition, ErrorCode errorCode) {
        throwIf(condition, new BusinessException(errorCode));
    }

    /**
     * 条件成立则按错误码 + 自定义信息抛出业务异常
     */
    public static void throwIf(boolean condition, ErrorCode errorCode, String message) {
        throwIf(condition, new BusinessException(errorCode, message));
    }
}
