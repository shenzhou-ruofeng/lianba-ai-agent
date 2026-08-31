package com.lianba.aiagent.tools;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 数据库操作工具单元测试（验证降级提示与 SQL 安全防护，不实际连库）
 */
class DatabaseOperationToolTest {

    @Test
    void returnsFriendlyMessageWhenDatasourceNotConfigured() {
        DatabaseOperationTool tool = new DatabaseOperationTool(null);
        Assertions.assertTrue(tool.executeQuery("select 1").contains("数据库未配置"));
        Assertions.assertTrue(tool.executeUpdate("insert into t values (1)").contains("数据库未配置"));
    }

    @Test
    void executeQueryRejectsNonSelectStatement() {
        // 校验在访问数据库之前完成，无需真实数据源
        DatabaseOperationTool tool = new DatabaseOperationTool(new JdbcTemplate());
        String result = tool.executeQuery("drop table users");
        Assertions.assertTrue(result.contains("只允许执行 SELECT"));
    }

    @Test
    void executeUpdateRejectsDdlStatement() {
        DatabaseOperationTool tool = new DatabaseOperationTool(new JdbcTemplate());
        Assertions.assertTrue(tool.executeUpdate("truncate table users").contains("只允许执行 INSERT、UPDATE、DELETE"));
        Assertions.assertTrue(tool.executeUpdate("alter table users add column age int").contains("只允许执行 INSERT、UPDATE、DELETE"));
    }

    @Test
    void executeUpdateRequiresWhereClauseForUpdateAndDelete() {
        DatabaseOperationTool tool = new DatabaseOperationTool(new JdbcTemplate());
        Assertions.assertTrue(tool.executeUpdate("update users set name = 'a'").contains("必须携带 WHERE 条件"));
        Assertions.assertTrue(tool.executeUpdate("delete from users").contains("必须携带 WHERE 条件"));
    }
}
