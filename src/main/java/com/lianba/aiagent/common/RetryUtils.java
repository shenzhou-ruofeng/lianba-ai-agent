package com.lianba.aiagent.common;

import com.github.rholder.retry.Attempt;
import com.github.rholder.retry.RetryListener;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Guava Retrying 的通用重试工具类
 * <p>
 * 用于增强提供给 AI 的各个工具的健壮性：网络请求等易失败操作
 * 统一使用「最多 3 次尝试 + 指数退避等待」策略，并在每次失败时记录日志。
 */
@Slf4j
public class RetryUtils {

    /**
     * 默认最大尝试次数（含首次调用）
     */
    private static final int DEFAULT_MAX_ATTEMPTS = 3;

    private RetryUtils() {
    }

    /**
     * 使用默认重试策略执行任务
     *
     * @param taskName 任务名称（用于日志）
     * @param callable 需要执行的任务
     * @return 任务返回值
     * @throws Exception 重试耗尽后抛出最后一次的异常
     */
    public static <T> T executeWithRetry(String taskName, Callable<T> callable) throws Exception {
        return executeWithRetry(taskName, DEFAULT_MAX_ATTEMPTS, callable);
    }

    /**
     * 使用指定最大尝试次数的重试策略执行任务
     *
     * @param taskName    任务名称（用于日志）
     * @param maxAttempts 最大尝试次数（含首次调用）
     * @param callable    需要执行的任务
     * @return 任务返回值
     * @throws Exception 重试耗尽后抛出最后一次的异常
     */
    public static <T> T executeWithRetry(String taskName, int maxAttempts, Callable<T> callable) throws Exception {
        Retryer<T> retryer = RetryerBuilder.<T>newBuilder()
                // 任意异常都触发重试
                .retryIfException()
                // 指数退避：1s、2s、4s...，单次等待封顶 10s
                .withWaitStrategy(WaitStrategies.exponentialWait(1000, 10, TimeUnit.SECONDS))
                .withStopStrategy(StopStrategies.stopAfterAttempt(maxAttempts))
                .withRetryListener(new RetryListener() {
                    @Override
                    public <V> void onRetry(Attempt<V> attempt) {
                        if (attempt.hasException()) {
                            log.warn("任务 [{}] 第 {} 次尝试失败: {}", taskName,
                                    attempt.getAttemptNumber(), attempt.getExceptionCause().getMessage());
                        }
                    }
                })
                .build();
        try {
            return retryer.call(callable);
        } catch (Exception e) {
            // 统一还原为原始异常，方便调用方处理
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.error("任务 [{}] 重试 {} 次后仍然失败: {}", taskName, maxAttempts, cause.getMessage());
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw e;
        }
    }
}
