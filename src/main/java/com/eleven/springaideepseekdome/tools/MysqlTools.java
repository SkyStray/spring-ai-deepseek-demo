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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    @Tool(description = "获取当前数据库所有表的元数据结构（包含表名、字段、类型、注释、索引）")
    @Retryable(interceptor = "aiRetryInterceptor",  maxAttempts = 2)  // 执行失败重试
    @Transactional(readOnly = true) // 只读事务
    public String getMySQLTableStructure() {
        try {
            String tableQuery = """
            SELECT TABLE_NAME, TABLE_COMMENT 
            FROM INFORMATION_SCHEMA.TABLES 
            WHERE TABLE_SCHEMA = DATABASE()
            """;
            log.info("\n\n[元数据查询]\nSQL: {}\n", tableQuery);

            List<String> tables = jdbcTemplate.query(tableQuery, (rs, rowNum) -> {
                String tableName = rs.getString("TABLE_NAME");
                String tableComment = rs.getString("TABLE_COMMENT");

                // 获取列信息（原逻辑）
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

                // 新增：获取索引信息
                String indexQuery = """
                SELECT 
                    INDEX_NAME, 
                    COLUMN_NAME, 
                    NON_UNIQUE,
                    INDEX_TYPE
                FROM INFORMATION_SCHEMA.STATISTICS 
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
                ORDER BY INDEX_NAME, SEQ_IN_INDEX
                """;
                List<String> indexes = jdbcTemplate.query(indexQuery,
                        ps -> ps.setString(1, tableName),
                        (idxRs, idxNum) -> {
                            String indexName = idxRs.getString("INDEX_NAME");
                            String columnName = idxRs.getString("COLUMN_NAME");
                            boolean isUnique = !idxRs.getBoolean("NON_UNIQUE");
                            String indexType = idxRs.getString("INDEX_TYPE");

                            return String.format("- %s (%s): %s | 类型: %s",
                                    isUnique ? "唯一索引" : "普通索引",
                                    indexName,
                                    columnName,
                                    indexType
                            );
                        }
                );

                return String.format("## 表名: %s\n注释: %s\n字段结构:\n%s\n索引信息:\n%s\n",
                        tableName,
                        tableComment.isEmpty() ? "无注释" : tableComment,
                        String.join("\n", columns),
                        indexes.isEmpty() ? "无索引" : String.join("\n", indexes)
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
    @Retryable(interceptor = "aiRetryInterceptor", maxAttempts = 3)
    @Transactional(readOnly = true)
    public List<Map<String, Object>> executeQuery(String sql, Object... params) {
        // 打印带参数的完整 SQL
        log.info("\n\n[SQL 执行日志]\n原始 SQL: {}\n参数: {}\n",
                sql,
                Arrays.toString(params));
        // 生成带参数的完整 SQL
        String fullSql = buildFullSql(sql, params);
        log.info("\n\n[SQL 执行日志]\n完整 SQL: {}\n", fullSql);

        // 新增安全校验
        validateQueryOnly(sql);

        return jdbcTemplate.query(sql, params, new ResultSetExtractor<>() {
            @Override
            public List<Map<String, Object>> extractData(ResultSet rs) {
                try {
                    List<Map<String, Object>> result = new ArrayList<>();
                    ResultSetMetaData metaData = rs.getMetaData();
                    int columnCount = metaData.getColumnCount();

                    // 记录结果集大小
                    int rowCount = 0;

                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= columnCount; i++) {
                            String columnName = metaData.getColumnLabel(i);
                            Object value = rs.getObject(i);
                            row.put(columnName, value);
                        }
                        result.add(row);
                        rowCount++;
                    }

                    // 打印执行结果摘要
                    log.info("\n[SQL 执行结果]\n返回行数: {}\n列字段: {}\n",
                            rowCount,
                            getColumnNames(metaData, columnCount));

                    // 打印前5行样本数据（避免大结果集日志膨胀）
                    if (!result.isEmpty()) {
                        log.debug("\n[样本数据预览]\n{}",
                                formatSampleData(result, Math.min(5, result.size())));
                    }

                    return result;
                } catch (Exception e) {
                    throw new RuntimeException("SQL 查询执行失败", e);
                }
            }
        });
    }

    // 辅助方法：构建带参数的完整 SQL（仅用于日志）
    private String buildFullSql(String sql, Object... params) {
        if (params == null || params.length == 0) {
            return sql;
        }

        StringBuilder fullSql = new StringBuilder();
        String[] parts = sql.split("\\?");

        for (int i = 0; i < parts.length; i++) {
            fullSql.append(parts[i]);
            if (i < params.length) {
                fullSql.append(formatSqlParam(params[i]));
            }
        }

        return fullSql.toString();
    }


    // 辅助方法：格式化 SQL 参数
    private String formatSqlParam(Object param) {
        if (param == null) {
            return "NULL";
        }
        if (param instanceof String) {
            return "'" + param + "'";
        }
        if (param instanceof Date) {
            return "'" + new java.sql.Timestamp(((Date) param).getTime()) + "'";
        }
        if (param instanceof java.sql.Date) {
            return "'" + param + "'";
        }
        if (param instanceof java.sql.Timestamp) {
            return "'" + param + "'";
        }
        return param.toString();
    }

    // 辅助方法：获取列名列表
    private List<String> getColumnNames(ResultSetMetaData metaData, int columnCount)
            throws Exception {
        List<String> columns = new ArrayList<>();
        for (int i = 1; i <= columnCount; i++) {
            columns.add(metaData.getColumnLabel(i));
        }
        return columns;
    }

    // 辅助方法：格式化样本数据
    private String formatSampleData(List<Map<String, Object>> result, int sampleSize) {
        StringBuilder sb = new StringBuilder();
        List<String> headers = new ArrayList<>(result.get(0).keySet());

        // 表头
        sb.append("| ").append(String.join(" | ", headers)).append(" |\n");
        sb.append("|").append("---|".repeat(headers.size())).append("\n");

        // 样本行
        for (int i = 0; i < sampleSize; i++) {
            Map<String, Object> row = result.get(i);
            List<String> values = new ArrayList<>();
            for (String header : headers) {
                Object value = row.get(header);
                values.add(value != null ? value.toString() : "NULL");
            }
            sb.append("| ").append(String.join(" | ", values)).append(" |\n");
        }

        if (result.size() > sampleSize) {
            sb.append(String.format("| ... (共 %d 行，显示前 %d 行) |",
                    result.size(), sampleSize));
        }

        return sb.toString();
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

            // 7. 使用正则防止误判
            Pattern pattern = Pattern.compile("\\b(" + String.join("|", forbiddenKeywords) + ")\\b", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(stmt);
            if (matcher.find()) {
                throw new IllegalArgumentException("检测到危险操作: " + stmt);
            }
        }
    }


}
