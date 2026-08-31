-- V2: 新建会话表 + 聊天消息表
-- 说明：支撑前端会话列表管理 + 聊天记录持久化，替代原有前端 localStorage 方案。

-- 会话表
CREATE TABLE IF NOT EXISTS ai_session (
    id           BIGSERIAL    PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    session_id   VARCHAR(64)  NOT NULL UNIQUE,          -- 前端 chatId，全局唯一
    agent_type   VARCHAR(32)  NOT NULL DEFAULT 'love',  -- love / super
    title        VARCHAR(128) NOT NULL DEFAULT '新会话',
    chat_mode    VARCHAR(16)  NOT NULL DEFAULT 'chat',  -- chat / match（love 专属）
    match_gender VARCHAR(8),                            -- 男 / 女 / null（love 专属）
    create_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted   SMALLINT     NOT NULL DEFAULT 0
);

-- 按用户 + 更新时间倒序的索引（会话列表查询）
CREATE INDEX idx_ai_session_user_update ON ai_session (user_id, update_time DESC);

-- update_time 自动更新触发器
CREATE OR REPLACE TRIGGER trg_ai_session_update_time
    BEFORE UPDATE ON ai_session
    FOR EACH ROW
    EXECUTE FUNCTION update_modified_column();

-- 聊天消息表
CREATE TABLE IF NOT EXISTS ai_chat_message (
    id          BIGSERIAL    PRIMARY KEY,
    session_id  VARCHAR(64)  NOT NULL,                  -- 关联 ai_session.session_id
    user_id     BIGINT       NOT NULL,
    role        VARCHAR(16)  NOT NULL,                  -- user / assistant / system
    content     TEXT         NOT NULL,
    msg_type    VARCHAR(32)  NOT NULL DEFAULT 'text',   -- text / tool_step / thinking / love_report / generated_image 等
    create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted  SMALLINT     NOT NULL DEFAULT 0
);

-- 按会话 + 创建时间正序的索引（消息列表查询）
CREATE INDEX idx_ai_chat_message_session_time ON ai_chat_message (session_id, create_time ASC);

-- update_time 自动更新触发器（消息表没有 update_time，此处不需要）
