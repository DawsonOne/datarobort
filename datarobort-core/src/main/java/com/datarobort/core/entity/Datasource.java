package com.datarobort.core.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * External business datasource registered on the platform.
 */
@Data
public class Datasource {

    private Long id;
    /** Display name. */
    private String name;
    /** mysql | postgresql | ... */
    private String type;
    private String jdbcUrl;
    private String username;
    /** AES-encrypted password (never returned to the frontend). */
    private String password;
    /** Optional free-form description. */
    private String description;
    /** 1 enabled, 0 disabled. */
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
