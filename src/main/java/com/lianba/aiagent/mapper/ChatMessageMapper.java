package com.lianba.aiagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lianba.aiagent.model.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 聊天消息 Mapper（MyBatis-Plus）
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
