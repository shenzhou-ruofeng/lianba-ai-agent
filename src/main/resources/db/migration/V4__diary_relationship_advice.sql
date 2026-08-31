-- 情感日记表
CREATE TABLE ai_diary (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    mood INTEGER,
    tags VARCHAR(500),
    ai_analysis TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_deleted INTEGER DEFAULT 0
);
CREATE INDEX idx_diary_user_create ON ai_diary(user_id, create_time DESC);

-- 关系状态变更日志表
CREATE TABLE ai_relationship_log (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    old_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    note VARCHAR(500),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_rel_log_user ON ai_relationship_log(user_id, create_time DESC);

-- 每日情感建议表
CREATE TABLE ai_daily_advice (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    advice_date DATE NOT NULL,
    content TEXT NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, advice_date)
);
CREATE INDEX idx_daily_advice_user_date ON ai_daily_advice(user_id, advice_date DESC);
