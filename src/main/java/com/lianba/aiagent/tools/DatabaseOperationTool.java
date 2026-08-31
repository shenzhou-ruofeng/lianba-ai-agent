package com.lianba.aiagent.tools;

import cn.hutool.core.util.StrUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 数据库操作工具类（提供查询、插入、更新和删除数据的功能）
 * <p>
 * 安全约束：
 * 1. 查询只允许 SELECT 语句，且限制最大返回行数
 * 2. 写操作只允许 INSERT / UPDATE / DELETE，禁止 DROP、TRUNCATE、ALTER 等 DDL
 * 3. UPDATE / DELETE 必须携带 WHERE 条件，避免全表误操作
 * 4. 未配置数据源时优雅降级，返回提示信息而不是让智能体中断
 */
public class DatabaseOperationTool {

    /**
     * 查询结果最大返回行数，避免大结果集撑爆模型上下文
     */
    private static final int MAX_QUERY_ROWS = 50;

    private static final String DATASOURCE_NOT_CONFIGURED = "数据库未配置，请先在 application.yml 中配置 spring.datasource，并移除 DataSourceAutoConfiguration 的排除项";

    private final JdbcTemplate jdbcTemplate;

    public DatabaseOperationTool(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Tool(description = "Execute a SQL SELECT query and return the result rows, at most 50 rows")
    public String executeQuery(@ToolParam(description = "SQL SELECT statement to execute") String sql) {
        if (jdbcTemplate == null) {
            return DATASOURCE_NOT_CONFIGURED;
        }
        String trimmedSql = StrUtil.trim(sql);
        if (!StrUtil.startWithIgnoreCase(trimmedSql, "select")) {
            return "Error executing query: 只允许执行 SELECT 查询语句";
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(trimmedSql);
            if (rows.isEmpty()) {
                return "查询成功，无匹配记录";
            }
            String resultText = rows.stream()
                    .limit(MAX_QUERY_ROWS)
                    .map(Map::toString)
                    .collect(Collectors.joining("\n"));
            String suffix = rows.size() > MAX_QUERY_ROWS
                    ? String.format("\n（结果共 %d 行，仅展示前 %d 行）", rows.size(), MAX_QUERY_ROWS)
                    : "";
            return String.format("查询成功，共 %d 行：\n%s%s", rows.size(), resultText, suffix);
        } catch (Exception e) {
            return "Error executing query: " + e.getMessage();
        }
    }

    @Tool(description = "Execute a SQL INSERT, UPDATE or DELETE statement, DDL like DROP/TRUNCATE/ALTER is forbidden")
    public String executeUpdate(@ToolParam(description = "SQL INSERT/UPDATE/DELETE statement to execute") String sql) {
        if (jdbcTemplate == null) {
            return DATASOURCE_NOT_CONFIGURED;
        }
        String trimmedSql = StrUtil.trim(sql);
        boolean isInsert = StrUtil.startWithIgnoreCase(trimmedSql, "insert");
        boolean isUpdate = StrUtil.startWithIgnoreCase(trimmedSql, "update");
        boolean isDelete = StrUtil.startWithIgnoreCase(trimmedSql, "delete");
        if (!isInsert && !isUpdate && !isDelete) {
            return "Error executing update: 只允许执行 INSERT、UPDATE、DELETE 语句";
        }
        // UPDATE / DELETE 必须带 WHERE 条件，防止全表误操作
        if ((isUpdate || isDelete) && !StrUtil.containsIgnoreCase(trimmedSql, "where")) {
            return "Error executing update: UPDATE / DELETE 语句必须携带 WHERE 条件";
        }
        try {
            int affectedRows = jdbcTemplate.update(trimmedSql);
            return "执行成功，影响行数：" + affectedRows;
        } catch (Exception e) {
            return "Error executing update: " + e.getMessage();
        }
    }
}
