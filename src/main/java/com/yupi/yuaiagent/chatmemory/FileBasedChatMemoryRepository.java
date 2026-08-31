package com.yupi.yuaiagent.chatmemory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import lombok.extern.slf4j.Slf4j;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 基于 Kryo 文件持久化的对话记忆仓库
 * 实现 Spring AI 的 ChatMemoryRepository 接口，可接入 MessageWindowChatMemory，
 * 在保留消息窗口裁剪能力的同时，让对话记忆在应用重启后仍然保留。
 * 每个会话对应一个 {conversationId}.kryo 文件。
 */
@Slf4j
public class FileBasedChatMemoryRepository implements ChatMemoryRepository {

    private final String baseDir;

    /** Kryo 非线程安全，使用 ThreadLocal 为每个线程持有独立实例 */
    private static final ThreadLocal<Kryo> KRYO_HOLDER = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(false);
        // 设置实例化策略，支持无默认构造器的消息类
        kryo.setInstantiatorStrategy(new StdInstantiatorStrategy());
        return kryo;
    });

    // 构造对象时，指定文件保存目录
    public FileBasedChatMemoryRepository(String dir) {
        this.baseDir = dir;
        File baseDirFile = new File(dir);
        if (!baseDirFile.exists() && !baseDirFile.mkdirs()) {
            log.warn("FileBasedChatMemoryRepository: failed to create base dir: {}", dir);
        }
    }

    @Override
    public List<String> findConversationIds() {
        File[] files = new File(baseDir).listFiles((dir, name) -> name.endsWith(".kryo"));
        if (files == null) {
            return List.of();
        }
        return Arrays.stream(files)
                .map(file -> file.getName().substring(0, file.getName().length() - ".kryo".length()))
                .toList();
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        File file = getConversationFile(conversationId);
        if (!file.exists()) {
            return new ArrayList<>();
        }
        try (Input input = new Input(new FileInputStream(file))) {
            @SuppressWarnings("unchecked")
            List<Message> messages = KRYO_HOLDER.get().readObject(input, ArrayList.class);
            return messages != null ? messages : new ArrayList<>();
        } catch (Exception e) {
            log.error("FileBasedChatMemoryRepository: failed to read conversation {}", conversationId, e);
            return new ArrayList<>();
        }
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        File file = getConversationFile(conversationId);
        try (Output output = new Output(new FileOutputStream(file))) {
            KRYO_HOLDER.get().writeObject(output, new ArrayList<>(messages));
        } catch (Exception e) {
            log.error("FileBasedChatMemoryRepository: failed to save conversation {}", conversationId, e);
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        File file = getConversationFile(conversationId);
        if (file.exists() && !file.delete()) {
            log.warn("FileBasedChatMemoryRepository: failed to delete conversation file: {}", file);
        }
    }

    private File getConversationFile(String conversationId) {
        return new File(baseDir, sanitize(conversationId) + ".kryo");
    }

    /**
     * 文件名安全过滤：只保留字母、数字、短横线和下划线，防止路径穿越
     */
    private String sanitize(String conversationId) {
        String safe = conversationId == null ? "" : conversationId.replaceAll("[^a-zA-Z0-9_-]", "_");
        return safe.isEmpty() ? "default" : safe;
    }
}
