package com.lianba.aiagent.exception;

import com.lianba.aiagent.common.BaseResponse;
import com.lianba.aiagent.common.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.nio.file.InvalidPathException;

/**
 * 全局异常处理器：将未捕获的异常统一转换为 BaseResponse 结构化响应，
 * 防止 Whitelabel Error Page 500 错误页暴露给用户，提高项目健壮性。
 * 同时保留合适的 HTTP 状态码，便于前端 axios 按错误分支处理。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常，按业务错误码映射 HTTP 状态
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<BaseResponse<?>> handleBusinessException(BusinessException e) {
        log.warn("BusinessException: code={}, message={}", e.getCode(), e.getMessage());
        return ResponseEntity.status(mapHttpStatus(e.getCode()))
                .body(ResultUtils.error(e.getCode(), e.getMessage()));
    }

    /**
     * 处理请求参数缺失
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<BaseResponse<?>> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("Missing request parameter: {}", e.getParameterName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ResultUtils.error(ErrorCode.PARAMS_ERROR, "缺少请求参数：" + e.getParameterName()));
    }

    /**
     * 处理路径包含非法字符的请求（如文件名含引号）
     */
    @ExceptionHandler(InvalidPathException.class)
    public ResponseEntity<BaseResponse<?>> handleInvalidPath(InvalidPathException e) {
        log.warn("Invalid path requested: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ResultUtils.error(ErrorCode.PARAMS_ERROR, "请求路径包含非法字符，请检查 URL"));
    }

    /**
     * 处理资源未找到（包括静态资源 404）
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<BaseResponse<?>> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ResultUtils.error(ErrorCode.NOT_FOUND_ERROR, "请求的资源不存在"));
    }

    /**
     * 处理未预期的运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<BaseResponse<?>> handleRuntimeException(RuntimeException e) {
        log.error("RuntimeException: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统内部异常，请稍后重试"));
    }

    /**
     * 处理所有其他未捕获的异常，兜底返回通用 500 响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse<?>> handleGenericException(Exception e) {
        log.error("Unhandled exception: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ResultUtils.error(ErrorCode.SYSTEM_ERROR, "服务器内部错误，请稍后重试"));
    }

    /**
     * 业务错误码 → HTTP 状态码映射（40000 段映射 4xx，其余归为 500）
     */
    private HttpStatus mapHttpStatus(int code) {
        if (code == ErrorCode.PARAMS_ERROR.getCode()) {
            return HttpStatus.BAD_REQUEST;
        }
        if (code == ErrorCode.NOT_LOGIN_ERROR.getCode() || code == ErrorCode.NO_AUTH_ERROR.getCode()) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (code == ErrorCode.FORBIDDEN_ERROR.getCode()) {
            return HttpStatus.FORBIDDEN;
        }
        if (code == ErrorCode.NOT_FOUND_ERROR.getCode()) {
            return HttpStatus.NOT_FOUND;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
