package com.datarobort.web.agent;

import com.datarobort.ai.graph.AgentState;
import com.datarobort.ai.graph.GraphNode;
import com.datarobort.common.crypto.AesCryptoUtil;
import com.datarobort.core.datasource.DataSourcePoolManager;
import com.datarobort.core.entity.Datasource;
import com.datarobort.core.entity.DsColumn;
import com.datarobort.core.entity.DsTable;
import com.datarobort.core.entity.ModelConfig;
import com.datarobort.core.mapper.DatasourceMapper;
import com.datarobort.core.mapper.DsColumnMapper;
import com.datarobort.core.mapper.DsTableMapper;
import com.datarobort.core.mapper.ModelConfigMapper;
import com.datarobort.web.service.ModelConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.*;

/**
 * Generates SQL from natural language using schema context + knowledge recall.
 */
@Slf4j
@Component
public class SqlGenNode implements GraphNode {

    private final ModelConfigService modelConfigService;
    private final ModelConfigMapper modelConfigMapper;
    private final DsTableMapper tableMapper;
    private final DsColumnMapper columnMapper;
    private final DataSourcePoolManager poolManager;
    private final DatasourceMapper datasourceMapper;

    @Value("${datarobort.crypto-key:datarobort-dev-key-2026}")
    private String cryptoKey;

    public SqlGenNode(ModelConfigService modelConfigService, ModelConfigMapper modelConfigMapper,
                      DsTableMapper tableMapper, DsColumnMapper columnMapper,
                      DataSourcePoolManager poolManager, DatasourceMapper datasourceMapper) {
        this.modelConfigService = modelConfigService;
        this.modelConfigMapper = modelConfigMapper;
        this.tableMapper = tableMapper;
        this.columnMapper = columnMapper;
        this.poolManager = poolManager;
        this.datasourceMapper = datasourceMapper;
    }

    @Override
    public AgentState execute(AgentState state) {
        if (!state.isIntent("query", "analyze", "report")) {
            state.addTrace("sql-gen", "done", 0, "skipped");
            return state;
        }

        long start = System.currentTimeMillis();
        try {
            String schema = buildSchemaContext();
            String recall = state.getRecallContext() != null ? state.getRecallContext() : "";
            String prompt = """
                    你是一个 SQL 专家。根据以下信息生成一条只读的 SELECT 语句。

                    ### 数据库表结构
                    %s

                    ### 相关知识（业务术语/同义词）
                    %s

                    ### 用户问题
                    %s

                    ### 要求
                    - 只生成 SELECT 语句，禁止 INSERT/UPDATE/DELETE/DROP
                    - 只返回 SQL，不要解释，不要 markdown 代码块
                    - 使用 LIMIT 限制最多返回 500 行
                    - 如果问题无法转化为 SQL，回复: NO_SQL

                    SQL:""".formatted(schema, recall, state.getUserQuestion());

            ChatClient client = defaultChatClient();
            String sql = client.prompt().user(prompt).call().content();
            if (sql == null || sql.isBlank() || sql.contains("NO_SQL")) {
                state.addTrace("sql-gen", "done", System.currentTimeMillis() - start, "no SQL needed");
                return state;
            }
            // Strip markdown code fences if present
            sql = sql.replaceAll("```sql\\s*", "").replaceAll("```\\s*", "").trim();
            state.setGeneratedSql(sql);
            state.setSqlRetryCount(0);
            state.addTrace("sql-gen", "done", System.currentTimeMillis() - start, "sql=" + truncate(sql, 100));
            log.info("generated SQL: {}", sql);
        } catch (Exception e) {
            log.error("SQL generation failed", e);
            state.addTrace("sql-gen", "failed", System.currentTimeMillis() - start, e.getMessage());
            state.setFailed(true); state.setErrorMessage("SQL生成失败: " + e.getMessage());
        }
        return state;
    }

    private String buildSchemaContext() {
        // For P3: grab metadata from data source id=1 (demo business DB).
        Long dsId = 1L;
        // Decrypt datasource password for connection
        Datasource ds = datasourceMapper.selectById(dsId);
        if (ds != null && ds.getPassword() != null) {
            ds.setPassword(AesCryptoUtil.decrypt(ds.getPassword(), cryptoKey));
        }

        List<DsTable> tables = tableMapper.selectByDsId(dsId);
        StringBuilder sb = new StringBuilder();
        for (DsTable t : tables) {
            sb.append("表: ").append(t.getTableName());
            if (t.getTableComment() != null) sb.append(" (").append(t.getTableComment()).append(")");
            sb.append("\n");
            List<DsColumn> cols = columnMapper.selectByTableId(t.getId());
            for (DsColumn c : cols) {
                sb.append("  ").append(c.getColumnName())
                        .append(" ").append(c.getDataType() != null ? c.getDataType() : "VARCHAR");
                if (c.getColumnComment() != null) sb.append(" -- ").append(c.getColumnComment());
                if (Boolean.TRUE.equals(c.getIsPrimary())) sb.append(" [PK]");

                // Include DISTINCT values for low-cardinality VARCHAR columns (like status, level, category)
                String type = c.getDataType() != null ? c.getDataType().toLowerCase() : "";
                if ((type.contains("varchar") || type.contains("char")) && ds != null) {
                    List<String> vals = queryDistinctValues(ds, t.getTableName(), c.getColumnName(), 20);
                    if (!vals.isEmpty()) {
                        sb.append(" [可选值: ").append(String.join(", ", vals)).append("]");
                    }
                }
                sb.append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /** Query up to 'limit' distinct values for a column in a table. */
    private List<String> queryDistinctValues(Datasource ds, String table, String column, int limit) {
        try (Connection conn = poolManager.getPool(ds).getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.setQueryTimeout(10);
            String sql = "SELECT DISTINCT `" + column + "` FROM `" + table + "` LIMIT " + limit;
            try (ResultSet rs = stmt.executeQuery(sql)) {
                List<String> vals = new ArrayList<>();
                while (rs.next()) {
                    String v = rs.getString(1);
                    if (v != null && !v.isEmpty()) vals.add(v);
                }
                return vals;
            }
        } catch (Exception e) {
            // Non-critical: skip distinct values on error
            log.debug("distinct values query failed for {}.{}: {}", table, column, e.getMessage());
            return List.of();
        }
    }

    private ChatClient defaultChatClient() {
        ModelConfig mc = modelConfigMapper.selectDefault(ModelConfig.TYPE_CHAT);
        if (mc == null) throw new RuntimeException("未设置默认 Chat 模型");
        return modelConfigService.chatClient(mc);
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
