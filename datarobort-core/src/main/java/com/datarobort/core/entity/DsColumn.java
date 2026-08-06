package com.datarobort.core.entity;

import lombok.Data;

/**
 * Column metadata crawled from a business datasource.
 */
@Data
public class DsColumn {

    private Long id;
    private Long tableId;
    private String columnName;
    /** JDBC type name, e.g. VARCHAR / BIGINT / DECIMAL. */
    private String dataType;
    private String columnComment;
    private Boolean isPrimary;
    private Boolean nullable;
}
