package com.eleven.springaideepseekdome.tools;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.*;

/**
 * @program: IntelliJ IDEA / spring-ai-deepseek-demo
 * @className: MysqlTools
 * @description: mysql functioncall工具
 * @author: Sky_Stray
 * @version: 1.0.0
 * @create: 2025/06/11 12:08
 **/
@Slf4j
@Component // 确保被 Spring 管理
@RequiredArgsConstructor // 自动生成构造函数
public class MysqlTools {

    private final JdbcTemplate jdbcTemplate;

    @Tool(description = "获取当前数据库所有表的元数据结构（包含表名、字段、类型、注释）")
    @Retryable(interceptor = "aiRetryInterceptor")
    @Transactional(readOnly = true)
    public String getMySQLTableStructure() {
        try {
            // 1. 获取所有表名及注释
            String tableQuery = """
                SELECT TABLE_NAME, TABLE_COMMENT 
                FROM INFORMATION_SCHEMA.TABLES 
                WHERE TABLE_SCHEMA = DATABASE()
                """;
            // 打印元数据查询 SQL
            log.info("\n\n[元数据查询]\nSQL: {}\n", tableQuery);
            List<String> tables = jdbcTemplate.query(tableQuery, (rs, rowNum) -> {
                String tableName = rs.getString("TABLE_NAME");
                String tableComment = rs.getString("TABLE_COMMENT");

                // 2. 获取每个表的列信息
                String columnQuery = """
                    SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT 
                    FROM INFORMATION_SCHEMA.COLUMNS 
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
                    """;

                List<String> columns = jdbcTemplate.query(columnQuery,
                        ps -> ps.setString(1, tableName),
                        (colRs, colNum) -> String.format("- %s (%s) %s NULL | 注释: %s",
                                colRs.getString("COLUMN_NAME"),
                                colRs.getString("COLUMN_TYPE"),
                                "NO".equals(colRs.getString("IS_NULLABLE")) ? "NOT" : "",
                                colRs.getString("COLUMN_COMMENT")
                        )
                );

                return String.format("## 表名: %s\n注释: %s\n字段结构:\n%s\n",
                        tableName,
                        tableComment.isEmpty() ? "无注释" : tableComment,
                        String.join("\n", columns)
                );
            });

            return "当前数据库表结构:\n\n" + String.join("\n", tables);

        } catch (Exception e) {
            throw new RuntimeException("获取数据库元数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * 执行通用 SQL 查询并返回结构化结果
     * @param sql 要执行的 SQL 语句
     * @param params SQL 参数
     * @return 包含列信息和行数据的 Map 列表
     */
    @Tool(description = "执行 SQL 查询并返回结构化结果")
    @Retryable(interceptor = "aiRetryInterceptor")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> executeQuery(String sql, Object... params) {
        // 打印带参数的完整 SQL
        log.info("\n\n[SQL 执行日志]\n原始 SQL: {}\n参数: {}\n",
                sql,
                Arrays.toString(params));
        // 新增安全校验
        validateQueryOnly(sql);
        return jdbcTemplate.query(sql, params, new ResultSetExtractor<>() {
            @Override
            public List<Map<String, Object>> extractData(ResultSet rs) {
                try {
                    List<Map<String, Object>> result = new ArrayList<>();
                    ResultSetMetaData metaData = rs.getMetaData();
                    int columnCount = metaData.getColumnCount();

                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= columnCount; i++) {
                            String columnName = metaData.getColumnLabel(i);
                            Object value = rs.getObject(i);
                            row.put(columnName, value);
                        }
                        result.add(row);
                    }
                    return result;
                } catch (Exception e) {
                    throw new RuntimeException("SQL 查询执行失败", e);
                }
            }
        });
    }

    /**
     * 将查询结果转换为 Markdown 表格格式
     * @param result 查询结果
     * @return Markdown 表格字符串
     */
    @Tool(description = "将查询结果转换为 Markdown 表格")
    public String toMarkdownTable(List<Map<String, Object>> result) {
        if (result.isEmpty()) {
            return "无数据";
        }

        StringBuilder sb = new StringBuilder();
        // 表头
        Map<String, Object> firstRow = result.get(0);
        sb.append("| ").append(String.join(" | ", firstRow.keySet())).append(" |\n");
        sb.append("|").append("---|".repeat(firstRow.size())).append("\n");

        // 数据行
        for (Map<String, Object> row : result) {
            List<String> values = row.values().stream()
                    .map(v -> v == null ? "NULL" : v.toString())
                    .toList();
            sb.append("| ").append(String.join(" | ", values)).append(" |\n");
        }
        return sb.toString();
    }

    /**
     * 获取数据库元数据 (表/视图列表)
     */
    @Tool(description = "获取数据库元数据 (表/视图列表)")
    public List<String> getDatabaseMetadata() {
        return jdbcTemplate.query("""
            SELECT 
                TABLE_NAME AS name, 
                TABLE_TYPE AS type,
                TABLE_COMMENT AS comment
            FROM INFORMATION_SCHEMA.TABLES 
            WHERE TABLE_SCHEMA = DATABASE()
            """, (rs, rowNum) ->
                String.format("%s [%s] - %s",
                        rs.getString("name"),
                        rs.getString("type"),
                        rs.getString("comment"))
        );
    }

    @Tool(description = "SQL执行前安全校验（仅允许SELECT查询）")
    public void validateQueryOnly(String sql) {
        // 1. 预处理SQL语句
        String processedSql = sql
                .replaceAll("/\\*.*?\\*/", "") // 移除块注释
                .replaceAll("--.*", "")       // 移除行注释
                .trim()                       // 去除前后空格
                .toUpperCase();               // 统一转为大写

        // 2. 分离多条SQL语句
        String[] statements = processedSql.split(";");

        for (String stmt : statements) {
            // 3. 提取首个有效关键字
            String firstKeyword = Arrays.stream(stmt.split("\\s+"))
                    .filter(word -> !word.isEmpty())
                    .findFirst()
                    .orElse("");

            // 4. 定义合法关键字白名单
            Set<String> allowedKeywords = Set.of(
                    "SELECT", "SHOW", "DESC", "DESCRIBE", "EXPLAIN",
                    "WITH", "USE", "HELP"
            );

            // 5. 定义禁止关键字黑名单
            Set<String> forbiddenKeywords = Set.of(
                    "INSERT", "UPDATE", "DELETE", "DROP", "ALTER",
                    "TRUNCATE", "CREATE", "GRANT", "REVOKE", "MERGE",
                    "CALL", "EXECUTE", "REPLACE", "LOCK", "UNLOCK"
            );

            // 6. 校验关键字合法性
            if (firstKeyword.isEmpty()) {
                throw new IllegalArgumentException("空语句");
            }

            if (!allowedKeywords.contains(firstKeyword)) {
                throw new IllegalArgumentException("非法操作类型: " + firstKeyword);
            }

            // 7. 检查黑名单关键字（防止绕过）
            if (forbiddenKeywords.stream().anyMatch(stmt::contains)) {
                throw new IllegalArgumentException("检测到危险操作: " + stmt);
            }
        }
    }

}
