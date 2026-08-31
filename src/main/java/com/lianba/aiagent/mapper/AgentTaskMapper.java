package com.lianba.aiagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lianba.aiagent.agent.model.AgentTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 智能体任务 Mapper（MyBatis-Plus）
 */
@Mapper
public interface AgentTaskMapper extends BaseMapper<AgentTask> {
}
