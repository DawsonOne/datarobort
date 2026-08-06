package com.datarobort.core.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * Semantic model: table / column level synonyms for better Text-to-SQL.
 * Vectors are stored in Redis index {@code semantic_model}.
 */
@Data
public class SemanticModel {

    public static final String VEC_PENDING = "pending";
    public static final String VEC_DONE = "done";
    public static final String VEC_FAILED = "failed";

    private Long id;
    private Long dsId;
    private String tableName;
    /** NULL means table-level synonym; non-NULL means column-level. */
    private String columnName;
    /** Comma-separated synonyms. */
    private String synonyms;
    /** pending | done | failed */
    private String vectorStatus;
    /** Whether recall is enabled. */
    private Boolean recallEnabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
