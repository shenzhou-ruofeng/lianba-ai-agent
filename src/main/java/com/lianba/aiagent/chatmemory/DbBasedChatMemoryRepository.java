package com.lianba.aiagent.chatmemory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lianba.aiagent.mapper.ChatMemoryMapper;
import com.lianba.aiagent.model.entity.ChatMemoryEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 基于数据库持久化的对话记忆仓库
 * <p>
 * 实现 Spring AI 的 ChatMemoryRepository 接口，替代原有的 Kryo 文件方案。
 * 每个会话的消息按 order_num 排序存储在 ai_chat_memory 表中，
 * 应用重启后对话记忆仍然保留，且支持多实例部署。
 */
@Slf4j
public class DbBasedChatMemoryRepository implements ChatMemoryRepository {

    private final ChatMemoryMapper chatMemoryMapper;

    public DbBasedChatMemoryRepository(ChatMemoryMapper chatMemoryMapper) {
        this.chatMemoryMapper = chatMemoryMapper;
    }

    @Override
    public List<String> findConversationIds() {
        // 查询所有不同的 conversation_id
        LambdaQueryWrapper<ChatMemoryEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(ChatMemoryEntity::getConversationId)
                .groupBy(ChatMemoryEntity::getConversationId);
        return chatMemoryMapper.selectList(wrapper).stream()
                .map(ChatMemoryEntity::getConversationId)
                .distinct()
                .toList();
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        LambdaQueryWrapper<ChatMemoryEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMemoryEntity::getConversationId, conversationId)
                .orderByAsc(ChatMemoryEntity::getOrderNum);

        List<ChatMemoryEntity> entities = chatMemoryMapper.selectList(wrapper);
        List<Message> messages = new ArrayList<>();
        for (ChatMemoryEntity entity : entities) {
            Message message = toMessage(entity);
            if (message != null) {
                messages.add(message);
            }
        }
        return messages;
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        // 先删除该会话的所有旧记忆
        LambdaQueryWrapper<ChatMemoryEntity> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(ChatMemoryEntity::getConversationId, conversationId);
        chatMemoryMapper.delete(deleteWrapper);

        // 批量插入新消息
        for (int i = 0; i < messages.size(); i++) {
            Message message = messages.get(i);
            String role = resolveRole(message);
            String content = message.getText();

            ChatMemoryEntity entity = new ChatMemoryEntity();
            entity.setConversationId(conversationId);
            entity.setRole(role);
            entity.setContent(content != null ? content : "");
            entity.setOrderNum(i);
            entity.setCreateTime(new Date());

            chatMemoryMapper.insert(entity);
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        LambdaQueryWrapper<ChatMemoryEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMemoryEntity::getConversationId, conversationId);
        chatMemoryMapper.delete(wrapper);
    }

    /**
     * 从数据库实体转换为 Spring AI Message 对象
     */
    private Message toMessage(ChatMemoryEntity entity) {
        return switch (entity.getRole()) {
            case "user" -> new UserMessage(entity.getContent());
            case "assistant" -> new AssistantMessage(entity.getContent());
            case "system" -> new SystemMessage(entity.getContent());
            default -> {
                log.warn("未知的消息角色: {}, 跳过", entity.getRole());
                yield null;
            }
        };
    }

    /**
     * 解析 Spring AI Message 的角色字符串
     */
    private String resolveRole(Message message) {
        if (message instanceof UserMessage) return "user";
        if (message instanceof AssistantMessage) return "assistant";
        if (message instanceof SystemMessage) return "system";
        return "user"; // 默认
    }
}
