package com.datarobort.core.datasource;

import com.alibaba.druid.wall.WallConfig;
import com.alibaba.druid.wall.WallFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Druid WallFilter for the dynamic business-datasource pools.
 * DML/DDL/multi-statement/file-export are all rejected; a hard SELECT row
 * ceiling backs up the executor's LIMIT enforcement. Function checks stay off
 * (the pipeline's SqlValidator owns the function blacklist, so analytical
 * functions like DATE_FORMAT are never blocked here).
 *
 * <p>Note: this filter is wired only into {@link DataSourcePoolManager} pools
 * (business query path). The platform datasource is untouched — it legitimately
 * runs INSERT/UPDATE for its own persistence.
 */
@Configuration
public class SqlSecurityConfig {

    @Bean
    public WallFilter sqlWallFilter() {
        WallConfig config = new WallConfig();
        config.setMultiStatementAllow(false);
        config.setSelectIntoOutfileAllow(false);
        config.setSelectIntoAllow(false);
        config.setInsertAllow(false);
        config.setUpdateAllow(false);
        config.setDeleteAllow(false);
        config.setDropTableAllow(false);
        config.setAlterTableAllow(false);
        config.setCreateTableAllow(false);
        config.setTruncateAllow(false);
        config.setNoneBaseStatementAllow(false);
        config.setSelectLimit(500);
        config.setFunctionCheck(false);

        WallFilter filter = new WallFilter();
        filter.setConfig(config);
        filter.setDbType("mysql");
        return filter;
    }
}
