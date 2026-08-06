package com.datarobort.core.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * Business terminology with synonyms. Vectors are stored in Redis index
 * {@code business_knowledge}.
 */
@Data
public class BusinessKnowledge {

    public static final String VEC_PENDING = "pending";
    public static final String VEC_DONE = "done";
    public static final String VEC_FAILED = "failed";

    private Long id;
    /** Business term. */
    private String term;
    /** Comma-separated synonyms. */
    private String synonyms;
    /** pending | done | failed */
    private String vectorStatus;
    /** Whether recall is enabled. */
    private Boolean recallEnabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
