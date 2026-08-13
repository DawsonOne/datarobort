package com.datarobort.web.agent;

import com.datarobort.ai.graph.AgentState;
import com.datarobort.ai.graph.GraphNode;
import com.datarobort.common.crypto.AesCryptoUtil;
import com.datarobort.core.datasource.DataSourcePoolManager;
import com.datarobort.core.entity.Datasource;
import com.datarobort.core.mapper.DatasourceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.*;

/**
 * Executes the validated SQL against the bound business datasource.
 * Enforces row-count limit and query timeout.
 */
@Slf4j
@Component
public class SqlExecNode implements GraphNode {

    private static final int MAX_ROWS = 500;
    private static final int QUERY_TIMEOUT_SEC = 30;

    private final DataSourcePoolManager poolManager;
    private final DatasourceMapper datasourceMapper;

    @Value("${datarobort.crypto-key:datarobort-dev-key-2026}")
    private String cryptoKey;

    public SqlExecNode(DataSourcePoolManager poolManager, DatasourceMapper datasourceMapper) {
        this.poolManager = poolManager;
        this.datasourceMapper = datasourceMapper;
    }

    @Override
    public AgentState execute(AgentState state) {
        if (state.getGeneratedSql() == null || state.getGeneratedSql().isBlank()) {
            state.addTrace("sql-exec", "done", 0, "no SQL");
            return state;
        }
        if (!state.isIntent("query", "analyze", "report")) {
            state.addTrace("sql-exec", "done", 0, "skipped");
            return state;
        }

        long start = System.currentTimeMillis();
        // FIXME P4: use agent-bound datasource, not hardcoded id=1
        Datasource ds = datasourceMapper.selectById(1L);
        if (ds == null) {
            state.addTrace("sql-exec", "failed", 0, "no datasource configured");
            state.setFailed(true); state.setErrorMessage("未配置数据源");
            return state;
        }
        // Decrypt password for pool creation
        ds = plainCopy(ds);

        try (Connection conn = poolManager.getPool(ds).getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.setQueryTimeout(QUERY_TIMEOUT_SEC);
            stmt.setMaxRows(MAX_ROWS);
            String sql = state.getGeneratedSql();
            if (!sql.toUpperCase().contains("LIMIT")) {
                sql += " LIMIT " + MAX_ROWS;
            }
            try (ResultSet rs = stmt.executeQuery(sql)) {
                ResultSetMetaData rsmd = rs.getMetaData();
                int cols = rsmd.getColumnCount();
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= cols; i++) {
                        row.put(rsmd.getColumnLabel(i), rs.getObject(i));
                    }
                    rows.add(row);
                }
                state.setQueryResult(rows);
                state.setRowCount(rows.size());
                long dur = System.currentTimeMillis() - start;
                state.addTrace("sql-exec", "done", dur, rows.size() + " rows returned");
                log.info("SQL executed: {} rows in {}ms", rows.size(), dur);
            }
        } catch (Exception e) {
            log.error("SQL execution failed", e);
            state.addTrace("sql-exec", "failed", System.currentTimeMillis() - start, e.getMessage());
            state.setFailed(true); state.setErrorMessage("SQL执行失败: " + e.getMessage());
        }
        return state;
    }

    private Datasource plainCopy(Datasource d) {
        Datasource copy = new Datasource();
        copy.setId(d.getId()); copy.setName(d.getName()); copy.setType(d.getType());
        copy.setJdbcUrl(d.getJdbcUrl()); copy.setUsername(d.getUsername());
        // Decrypt password for Druid pool creation
        String pwd = d.getPassword();
        if (pwd != null && !pwd.isEmpty()) {
            copy.setPassword(AesCryptoUtil.decrypt(pwd, cryptoKey));
        }
        copy.setDescription(d.getDescription()); copy.setStatus(d.getStatus());
        return copy;
    }
}
