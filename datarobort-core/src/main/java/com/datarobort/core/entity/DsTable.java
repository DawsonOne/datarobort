package com.datarobort.core.entity;

import lombok.Data;

/**
 * Table metadata crawled from a business datasource.
 */
@Data
public class DsTable {

    private Long id;
    private Long dsId;
    private String tableName;
    private String tableComment;
}
