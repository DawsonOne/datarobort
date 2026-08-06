package com.datarobort.core.datasource;

import com.alibaba.druid.pool.DruidDataSource;
import com.datarobort.core.entity.Datasource;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages one Druid pool per registered business datasource.
 * Pools are created lazily and closed on eviction / shutdown.
 */
@Slf4j
@Component
public class DataSourcePoolManager {

    private final Map<Long, DruidDataSource> pools = new ConcurrentHashMap<>();

    /** Returns (creating if needed) the pool for a datasource. */
    public DruidDataSource getPool(Datasource ds) {
        return pools.computeIfAbsent(ds.getId(), id -> {
            log.info("creating druid pool for datasource {} ({})", ds.getId(), ds.getName());
            DruidDataSource pool = new DruidDataSource();
            pool.setUrl(ds.getJdbcUrl());
            pool.setUsername(ds.getUsername());
            pool.setPassword(ds.getPassword());
            pool.setDriverClassName(driverClass(ds.getType()));
            pool.setInitialSize(1);
            pool.setMinIdle(1);
            pool.setMaxActive(8);
            pool.setMaxWait(5000);
            pool.setValidationQuery("SELECT 1");
            pool.setTestWhileIdle(true);
            pool.setTimeBetweenEvictionRunsMillis(60_000);
            return pool;
        });
    }

    /** Connectivity test: borrow a connection and validate it.
     *  Evicts the pool on failure so a subsequent call rebuilds it. */
    public boolean test(Datasource ds) {
        try (Connection conn = getPool(ds).getConnection()) {
            return conn.isValid(3);
        } catch (Exception e) {
            log.warn("datasource {} connection test failed: {}", ds.getId(), e.getMessage());
            evict(ds.getId());
            return false;
        }
    }

    /** Closes and removes the pool (after update / delete). */
    public void evict(Long datasourceId) {
        DruidDataSource pool = pools.remove(datasourceId);
        if (pool != null) {
            pool.close();
        }
    }

    @PreDestroy
    public void closeAll() {
        pools.values().forEach(DruidDataSource::close);
        pools.clear();
    }

    private String driverClass(String type) {
        if (type == null) {
            return "com.mysql.cj.jdbc.Driver";
        }
        return switch (type.toLowerCase()) {
            case "postgresql", "pg" -> "org.postgresql.Driver";
            default -> "com.mysql.cj.jdbc.Driver";
        };
    }
}
