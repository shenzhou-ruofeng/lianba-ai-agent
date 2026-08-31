package com.lianba.aiagent.agent.interaction;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 人机交互注册中心：为 AskHuman 工具提供“提问 → 等待用户回复”的桥梁。
 * <p>
 * 工作流程：
 * 1. 智能体在执行 askHuman 工具前，创建一次交互（interactionId）并绑定到当前执行线程；
 * 2. 通过 SSE 将问题与 interactionId 推送给前端；
 * 3. askHuman 工具在执行线程上阻塞等待用户回复；
 * 4. 用户通过 HTTP 接口提交回复，完成对应的 Future，工具继续执行。
 */
@Slf4j
public final class HumanInteractionRegistry {

    /**
     * 待回复的交互：interactionId -> 用户回复的 Future
     */
    private static final Map<String, CompletableFuture<String>> PENDING_INTERACTIONS = new ConcurrentHashMap<>();

    /**
     * 当前执行线程绑定的交互 ID（工具执行与 act() 在同一线程，借助 ThreadLocal 传递）
     */
    private static final ThreadLocal<String> CURRENT_INTERACTION = new ThreadLocal<>();

    private HumanInteractionRegistry() {
    }

    /**
     * 创建一次新的人机交互，返回交互 ID
     */
    public static String createInteraction() {
        String interactionId = UUID.randomUUID().toString().replace("-", "");
        PENDING_INTERACTIONS.put(interactionId, new CompletableFuture<>());
        log.info("Created human interaction: {}", interactionId);
        return interactionId;
    }

    /**
     * 将交互 ID 绑定到当前线程，供 askHuman 工具在同一线程内取用
     */
    public static void bindToCurrentThread(String interactionId) {
        CURRENT_INTERACTION.set(interactionId);
    }

    /**
     * 解绑当前线程的交互 ID
     */
    public static void unbindFromCurrentThread() {
        CURRENT_INTERACTION.remove();
    }

    /**
     * 获取当前线程绑定的交互 ID（未绑定时返回 null）
     */
    public static String getCurrentInteractionId() {
        return CURRENT_INTERACTION.get();
    }

    /**
     * 阻塞等待用户回复
     *
     * @param interactionId  交互 ID
     * @param timeoutSeconds 最长等待秒数
     * @return 用户回复内容
     * @throws TimeoutException 等待超时
     */
    public static String waitForReply(String interactionId, long timeoutSeconds)
            throws TimeoutException, InterruptedException {
        CompletableFuture<String> future = PENDING_INTERACTIONS.get(interactionId);
        if (future == null) {
            throw new IllegalStateException("Interaction not found: " + interactionId);
        }
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new IllegalStateException("Interaction failed: " + interactionId, e);
        }
    }

    /**
     * 用户提交回复，完成对应交互
     *
     * @return 是否成功提交（交互不存在或已完成时返回 false）
     */
    public static boolean submitReply(String interactionId, String answer) {
        CompletableFuture<String> future = PENDING_INTERACTIONS.get(interactionId);
        if (future == null) {
            log.warn("Submit reply failed, interaction not found: {}", interactionId);
            return false;
        }
        boolean completed = future.complete(answer);
        if (completed) {
            log.info("Human reply submitted for interaction: {}", interactionId);
        }
        return completed;
    }

    /**
     * 移除交互，释放资源（执行完毕或超时后调用）
     */
    public static void removeInteraction(String interactionId) {
        PENDING_INTERACTIONS.remove(interactionId);
    }

    /**
     * 判断交互是否存在且未完成（供接口校验）
     */
    public static boolean isPending(String interactionId) {
        CompletableFuture<String> future = PENDING_INTERACTIONS.get(interactionId);
        return future != null && !future.isDone();
    }
}
