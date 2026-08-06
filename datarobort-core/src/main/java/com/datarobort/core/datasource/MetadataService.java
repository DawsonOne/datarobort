package com.datarobort.core.datasource;

import com.datarobort.common.error.ErrorCode;
import com.datarobort.common.exception.BizException;
import com.datarobort.core.entity.Datasource;
import com.datarobort.core.entity.DsColumn;
import com.datarobort.core.entity.DsTable;
import com.datarobort.core.mapper.DsColumnMapper;
import com.datarobort.core.mapper.DsTableMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Crawls table/column metadata from a business datasource via JDBC
 * {@link DatabaseMetaData} and stores it in ds_table / ds_column.
 */
@Slf4j
@Service
public class MetadataService {

    private final DataSourcePoolManager poolManager;
    private final DsTableMapper tableMapper;
    private final DsColumnMapper columnMapper;

    public MetadataService(DataSourcePoolManager poolManager,
                           DsTableMapper tableMapper,
                           DsColumnMapper columnMapper) {
        this.poolManager = poolManager;
        this.tableMapper = tableMapper;
        this.columnMapper = columnMapper;
    }

    /**
     * Re-crawls the whole schema and replaces stored metadata.
     *
     * @return number of tables found
     */
    @Transactional
    public int refresh(Datasource ds) {
        List<DsTable> tables = new ArrayList<>();
        Map<String, List<DsColumn>> columnsByTable = new LinkedHashMap<>();
        try (Connection conn = poolManager.getPool(ds).getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String catalog = conn.getCatalog();
            // MySQL uses catalog for the database name; PostgreSQL needs the schema.
            String schema = isPostgres(ds.getType()) ? "public" : null;

            try (ResultSet rs = meta.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    DsTable t = new DsTable();
                    t.setDsId(ds.getId());
                    t.setTableName(rs.getString("TABLE_NAME"));
                    t.setTableComment(rs.getString("REMARKS"));
                    tables.add(t);
                }
            }
            for (DsTable t : tables) {
                Set<String> primaryKeys = new HashSet<>();
                try (ResultSet rs = meta.getPrimaryKeys(catalog, schema, t.getTableName())) {
                    while (rs.next()) {
                        primaryKeys.add(rs.getString("COLUMN_NAME"));
                    }
                }
                List<DsColumn> columns = new ArrayList<>();
                try (ResultSet rs = meta.getColumns(catalog, schema, t.getTableName(), "%")) {
                    while (rs.next()) {
                        DsColumn c = new DsColumn();
                        c.setColumnName(rs.getString("COLUMN_NAME"));
                        c.setDataType(rs.getString("TYPE_NAME"));
                        c.setColumnComment(rs.getString("REMARKS"));
                        c.setNullable(rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
                        c.setIsPrimary(primaryKeys.contains(c.getColumnName()));
                        columns.add(c);
                    }
                }
                columnsByTable.put(t.getTableName(), columns);
            }
        } catch (SQLException e) {
            throw new BizException(ErrorCode.DS_CONNECT_FAILED, "schema 抓取失败: " + e.getMessage());
        }

        columnMapper.deleteByDsId(ds.getId());
        tableMapper.deleteByDsId(ds.getId());
        for (DsTable t : tables) {
            tableMapper.insert(t);
            for (DsColumn c : columnsByTable.getOrDefault(t.getTableName(), List.of())) {
                c.setTableId(t.getId());
                columnMapper.insert(c);
            }
        }
        log.info("datasource {} schema refreshed: {} tables", ds.getId(), tables.size());
        return tables.size();
    }

    private boolean isPostgres(String type) {
        if (type == null) return false;
        String t = type.toLowerCase();
        return "postgresql".equals(t) || "pg".equals(t);
    }

    /** Schema tree: tables with their columns, for the frontend tree view. */
    public List<Map<String, Object>> schemaTree(Long dsId) {
        List<DsTable> tables = tableMapper.selectByDsId(dsId);
        List<DsColumn> columns = columnMapper.selectByDsId(dsId);
        Map<Long, List<DsColumn>> byTable = new LinkedHashMap<>();
        for (DsColumn c : columns) {
            byTable.computeIfAbsent(c.getTableId(), k -> new ArrayList<>()).add(c);
        }
        List<Map<String, Object>> tree = new ArrayList<>();
        for (DsTable t : tables) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", t.getId());
            node.put("tableName", t.getTableName());
            node.put("tableComment", t.getTableComment());
            node.put("columns", byTable.getOrDefault(t.getId(), List.of()));
            tree.add(node);
        }
        return tree;
    }
}
