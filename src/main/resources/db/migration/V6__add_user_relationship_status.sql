-- V6: ai_user 表补充 relationship_status 字段（Onboarding 情感状态选择功能）
-- 说明：User 实体类与 UserMapper 查询依赖该字段（用于 LoveApp 个性化 Prompt），
--       但 V1 建表时未包含，故通过迁移补齐；软删除等已由既有字段覆盖。

ALTER TABLE ai_user ADD COLUMN IF NOT EXISTS relationship_status VARCHAR(20);
