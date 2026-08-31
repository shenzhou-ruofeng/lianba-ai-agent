-- V1: 初始化用户表
-- 说明：从原 UserService @PostConstruct 动态建表迁移到 Flyway 版本化管理，
--       新增 update_time / is_deleted 字段，支持软删除和自动填充更新时间。

CREATE TABLE IF NOT EXISTS ai_user (
    id            BIGSERIAL    PRIMARY KEY,
    user_account  VARCHAR(64)  NOT NULL UNIQUE,
    user_password VARCHAR(128) NOT NULL,
    user_name     VARCHAR(64),
    user_role     VARCHAR(16)  NOT NULL DEFAULT 'user',
    create_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted    SMALLINT     NOT NULL DEFAULT 0
);

-- update_time 自动更新触发器（每次 UPDATE 时自动刷新 update_time）
CREATE OR REPLACE FUNCTION update_modified_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.update_time = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_ai_user_update_time ON ai_user;
CREATE TRIGGER trg_ai_user_update_time
    BEFORE UPDATE ON ai_user
    FOR EACH ROW
    EXECUTE FUNCTION update_modified_column();
