-- V3: 智能体任务表迁移 + 恋爱报告表 + 对话记忆表
-- 说明：
--   1. agent_task 表从 AgentTaskService @PostConstruct 迁移到 Flyway 版本化管理
--   2. 新建 ai_love_report 表，持久化恋爱报告
--   3. 新建 ai_chat_memory 表，替代 Kryo 文件持久化的对话记忆

-- ==================== 1. 智能体任务表 ====================
CREATE TABLE IF NOT EXISTS agent_task (
    task_id       VARCHAR(64)  PRIMARY KEY,
    user_id       BIGINT,
    user_account  VARCHAR(64),
    message       VARCHAR(1000),
    status        VARCHAR(16)  NOT NULL,
    error_message VARCHAR(1000),
    create_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- update_time 自动更新触发器
DROP TRIGGER IF EXISTS trg_agent_task_update_time ON agent_task;
CREATE TRIGGER trg_agent_task_update_time
    BEFORE UPDATE ON agent_task
    FOR EACH ROW
    EXECUTE FUNCTION update_modified_column();

-- ==================== 2. 恋爱报告表 ====================
CREATE TABLE IF NOT EXISTS ai_love_report (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    session_id  VARCHAR(64),
    title       VARCHAR(256),
    suggestions TEXT         NOT NULL,           -- JSON 数组格式：["建议1", "建议2", ...]
    create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted  SMALLINT     NOT NULL DEFAULT 0
);

CREATE INDEX idx_ai_love_report_user ON ai_love_report (user_id, create_time DESC);

-- ==================== 3. 对话记忆表（替代 Kryo 文件） ====================
CREATE TABLE IF NOT EXISTS ai_chat_memory (
    id              BIGSERIAL    PRIMARY KEY,
    conversation_id VARCHAR(64)  NOT NULL,       -- 即 sessionId / chatId
    role            VARCHAR(16)  NOT NULL,       -- user / assistant / system
    content         TEXT         NOT NULL,
    order_num       INT          NOT NULL,        -- 消息在会话中的顺序
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ai_chat_memory_conv ON ai_chat_memory (conversation_id, order_num ASC);
