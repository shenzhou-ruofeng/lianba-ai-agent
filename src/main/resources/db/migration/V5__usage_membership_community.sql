-- 用量记录表
CREATE TABLE ai_usage_record (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    record_type VARCHAR(30) NOT NULL,
    model_name VARCHAR(50),
    token_count INTEGER DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_usage_user_type ON ai_usage_record(user_id, record_type, create_time DESC);
CREATE INDEX idx_usage_user_date ON ai_usage_record(user_id, create_time DESC);

-- 会员等级表（基于使用量自动升级）
CREATE TABLE ai_user_membership (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    tier VARCHAR(20) NOT NULL DEFAULT 'bronze',
    upgrade_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 社区帖子表
CREATE TABLE ai_community_post (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    nickname VARCHAR(50),
    content TEXT NOT NULL,
    tag VARCHAR(30),
    like_count INTEGER DEFAULT 0,
    comment_count INTEGER DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_deleted INTEGER DEFAULT 0
);
CREATE INDEX idx_post_create ON ai_community_post(create_time DESC);

-- 社区评论表
CREATE TABLE ai_community_comment (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    nickname VARCHAR(50),
    content TEXT NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_deleted INTEGER DEFAULT 0
);
CREATE INDEX idx_comment_post ON ai_community_comment(post_id, create_time ASC);
