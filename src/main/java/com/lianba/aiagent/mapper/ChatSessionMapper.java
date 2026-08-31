package com.lianba.aiagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lianba.aiagent.model.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会话 Mapper（MyBatis-Plus）
 */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
}
