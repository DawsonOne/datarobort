package com.datarobort.core.entity;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Platform agent: binds datasources, knowledge bases and a custom system
 * prompt. One agent = one runtime-assembled analysis pipeline.
 */
@Data
public class Agent {

    private Long id;
    private String name;
    private String avatar;
    /** Custom system prompt (business background / role definition). */
    private String prompt;
    /** 1 published, 0 draft. */
    private Integer status;
    /** Whether business knowledge recall is enabled. */
    private Boolean businessRecallEnabled;
    /** Whether semantic model recall is enabled. */
    private Boolean semanticRecallEnabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // ---- transient bindings (assembled by AgentService, not DB columns) ----
    // NOTE: kept null until filled, so AgentService can distinguish
    // "bindings not sent in the request" from "empty binding list".
    private List<Long> datasourceIds;
    private List<Long> kbIds;
    private List<String> datasourceNames;
    private List<String> kbNames;

    public static final int STATUS_PUBLISHED = 1;
    public static final int STATUS_DRAFT = 0;
}
