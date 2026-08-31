package com.lianba.aiagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lianba.aiagent.model.entity.ChatMemoryEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 对话记忆 Mapper（MyBatis-Plus）
 */
@Mapper
public interface ChatMemoryMapper extends BaseMapper<ChatMemoryEntity> {
}
