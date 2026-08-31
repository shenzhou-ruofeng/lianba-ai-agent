package com.lianba.aiagent.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lianba.aiagent.agent.AgentRunListener;
import com.lianba.aiagent.agent.BaseAgent;
import com.lianba.aiagent.agent.model.AgentTask;
import com.lianba.aiagent.agent.model.AgentTaskStatus;
import com.lianba.aiagent.exception.BusinessException;
import com.lianba.aiagent.exception.ErrorCode;
import com.lianba.aiagent.exception.ThrowUtils;
import com.lianba.aiagent.mapper.AgentTaskMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 智能体推理任务服务：记录任务状态（数据库/内存降级）+ 管理运行中的智能体实例。
 * <p>
 * 1. 任务异步化：任务记录先落库再执行，前端可随时查询任务状态，提升可观测性；
 * 2. 手动停止：通过 taskId 找到运行中的智能体实例，请求其在步骤边界优雅停止。
 */
@Slf4j
@Service
public class AgentTaskService implements AgentRunListener {

    /**
     * 消息存储最大长度，超长截断
     */
    private static final int MAX_MESSAGE_LENGTH = 500;

    /**
     * 内存降级存储：taskId -> 任务记录
     */
    private final Map<String, AgentTask> memoryTaskStore = new ConcurrentHashMap<>();

    /**
     * 运行中的智能体注册表：taskId -> 智能体实例（用于手动停止）
     */
    private final Map<String, BaseAgent> runningAgents = new ConcurrentHashMap<>();

    private final AgentTaskMapper agentTaskMapper;

    private volatile boolean databaseAvailable = false;

    public AgentTaskService(ObjectProvider<AgentTaskMapper> agentTaskMapperProvider) {
        this.agentTaskMapper = agentTaskMapperProvider.getIfAvailable();
    }

    /**
     * 启动时检测数据库是否可用；不可用则降级为内存存储
     */
    @PostConstruct
    public void init() {
        if (agentTaskMapper == null) {
            log.info("未配置数据源，智能体任务状态使用内存存储（重启后丢失）");
            return;
        }
        try {
            agentTaskMapper.selectCount(null);
            databaseAvailable = true;
            log.info("数据库连接正常，使用 agent_task 表记录任务状态");
        } catch (Exception e) {
            log.warn("数据库不可用，降级为内存存储: {}", e.getMessage());
        }
    }

    /**
     * 启动一个新任务：生成 taskId、记录 RUNNING 状态、注册智能体实例并绑定结束回调
     *
     * @return 任务 ID
     */
    public String startTask(Long userId, String userAccount, String message, BaseAgent agent) {
        String taskId = IdUtil.simpleUUID();
        AgentTask task = new AgentTask();
        task.setTaskId(taskId);
        task.setUserId(userId);
        task.setUserAccount(userAccount);
        task.setMessage(StrUtil.maxLength(message, MAX_MESSAGE_LENGTH));
        task.setStatus(AgentTaskStatus.RUNNING.name());
        task.setCreateTime(new Date());
        task.setUpdateTime(new Date());
        saveTask(task);
        // 注册运行中的智能体，供手动停止使用
        runningAgents.put(taskId, agent);
        agent.setTaskId(taskId);
        agent.setRunListener(this);
        log.info("智能体任务已创建: taskId={}, user={}", taskId, userAccount);
        return taskId;
    }

    /**
     * 手动停止任务：校验任务归属后，请求智能体在步骤边界优雅停止
     */
    public boolean stopTask(String taskId, Long userId) {
        ThrowUtils.throwIf(StrUtil.isBlank(taskId), ErrorCode.PARAMS_ERROR, "任务 ID 不能为空");
        AgentTask task = getTask(taskId);
        ThrowUtils.throwIf(task == null, ErrorCode.NOT_FOUND_ERROR, "任务不存在");
        // 任务归属校验：只能停止自己发起的任务
        if (task.getUserId() != null && !task.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权停止他人的任务");
        }
        BaseAgent agent = runningAgents.get(taskId);
        if (agent == null) {
            // 任务已结束，不再运行
            return false;
        }
        agent.requestStop();
        log.info("已请求停止智能体任务: taskId={}", taskId);
        return true;
    }

    /**
     * 查询单个任务
     */
    public AgentTask getTask(String taskId) {
        if (databaseAvailable) {
            try {
                return agentTaskMapper.selectById(taskId);
            } catch (Exception e) {
                log.error("查询任务失败: {}", e.getMessage());
                return null;
            }
        }
        return memoryTaskStore.get(taskId);
    }

    /**
     * 查询用户最近的任务列表（按创建时间倒序，最多 20 条）
     */
    public List<AgentTask> listUserTasks(Long userId) {
        if (databaseAvailable) {
            try {
                LambdaQueryWrapper<AgentTask> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(AgentTask::getUserId, userId)
                        .orderByDesc(AgentTask::getCreateTime)
                        .last("LIMIT 20");
                return agentTaskMapper.selectList(wrapper);
            } catch (Exception e) {
                log.error("查询任务列表失败: {}", e.getMessage());
                return List.of();
            }
        }
        return memoryTaskStore.values().stream()
                .filter(task -> userId != null && userId.equals(task.getUserId()))
                .sorted(java.util.Comparator.comparing(AgentTask::getCreateTime).reversed())
                .limit(20)
                .toList();
    }

    /**
     * 智能体任务结束回调：更新任务最终状态并从运行注册表移除
     */
    @Override
    public void onTaskFinished(String taskId, AgentTaskStatus finalStatus, String errorMessage) {
        runningAgents.remove(taskId);
        updateTaskStatus(taskId, finalStatus, errorMessage);
        log.info("智能体任务已结束: taskId={}, status={}", taskId, finalStatus);
    }

    private void saveTask(AgentTask task) {
        if (databaseAvailable) {
            try {
                agentTaskMapper.insert(task);
                return;
            } catch (Exception e) {
                log.error("保存任务记录失败，转存内存: {}", e.getMessage());
            }
        }
        memoryTaskStore.put(task.getTaskId(), task);
    }

    private void updateTaskStatus(String taskId, AgentTaskStatus status, String errorMessage) {
        String truncatedError = StrUtil.maxLength(errorMessage, MAX_MESSAGE_LENGTH);
        if (databaseAvailable) {
            try {
                LambdaUpdateWrapper<AgentTask> wrapper = new LambdaUpdateWrapper<>();
                wrapper.eq(AgentTask::getTaskId, taskId)
                        .set(AgentTask::getStatus, status.name())
                        .set(AgentTask::getErrorMessage, truncatedError)
                        .set(AgentTask::getUpdateTime, new Date());
                agentTaskMapper.update(null, wrapper);
                return;
            } catch (Exception e) {
                log.error("更新任务状态失败: {}", e.getMessage());
            }
        }
        AgentTask task = memoryTaskStore.get(taskId);
        if (task != null) {
            task.setStatus(status.name());
            task.setErrorMessage(truncatedError);
            task.setUpdateTime(new Date());
        }
    }
}
