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
 * Defense in depth: re-validates the SQL (table whitelist, SELECT-only) right
 * before execution, forces LIMIT ≤ MAX_ROWS, and runs on a read-only
 * connection from the pool.
 */
@Slf4j
@Component
public class SqlExecNode implements GraphNode {

    private static final int QUERY_TIMEOUT_SEC = 30;

    private final DataSourcePoolManager poolManager;
    private final DatasourceMapper datasourceMapper;
    private final SqlValidator sqlValidator;

    @Value("${datarobort.crypto-key:datarobort-dev-key-2026}")
    private String cryptoKey;

    /** Row cap applied at execution time (LIMIT rewrite + JDBC maxRows). */
    @Value("${datarobort.security.sql-max-rows:500}")
    private int maxRows;

    public SqlExecNode(DataSourcePoolManager poolManager, DatasourceMapper datasourceMapper,
                       SqlValidator sqlValidator) {
        this.poolManager = poolManager;
        this.datasourceMapper = datasourceMapper;
        this.sqlValidator = sqlValidator;
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
        // Agent-bound datasource: use the first datasource the agent binds;
        // fall back to the legacy default (id=1) when no binding exists.
        Long dsId = AgentDsResolver.firstDsId(state);
        Datasource ds = datasourceMapper.selectById(dsId);
        if (ds == null) {
            state.addTrace("sql-exec", "failed", 0, "no datasource configured");
            state.setFailed(true); state.setErrorMessage("未配置数据源");
            return state;
        }
        // Decrypt password for pool creation
        ds = plainCopy(ds);

        // Second validation right before execution — even if the state was
        // touched upstream (LLM fix retries), the guard still applies.
        // Versioned comments are stripped so the executed text is exactly the
        // text the validator parsed (MySQL executes /*! ... */ content).
        String sql = SqlValidator.stripVersionedComments(state.getGeneratedSql());
        String guardError = sqlValidator.validate(sql, dsId);
        if (guardError != null) {
            state.addTrace("sql-exec", "failed", 0, "guard rejected: " + guardError);
            state.setFailed(true); state.setErrorMessage("SQL 安全校验未通过: " + guardError);
            return state;
        }
        try {
            sql = SqlLimitEnforcer.enforceLimit(sql, maxRows);
        } catch (IllegalArgumentException e) {
            state.addTrace("sql-exec", "failed", 0, e.getMessage());
            state.setFailed(true); state.setErrorMessage(e.getMessage());
            return state;
        }

        try (Connection conn = poolManager.getPool(ds).getConnection();
             Statement stmt = conn.createStatement()) {
            conn.setReadOnly(true);
            stmt.setQueryTimeout(QUERY_TIMEOUT_SEC);
            stmt.setMaxRows(maxRows);
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
            String msg = truncate(e.getMessage());
            state.addTrace("sql-exec", "failed", System.currentTimeMillis() - start, msg);
            state.setFailed(true); state.setErrorMessage("SQL执行失败: " + msg);
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

    private String truncate(String s) {
        if (s == null) return "";
        return s.length() <= SqlValidator.MAX_ERROR_LEN ? s : s.substring(0, SqlValidator.MAX_ERROR_LEN) + "...";
    }
}
