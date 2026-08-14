package com.datarobort.core.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * One message inside a conversation. Assistant messages carry the SQL,
 * markdown report, report file URL and node execution traces.
 */
@Data
public class Message {

    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    private Long id;
    private Long conversationId;
    private String role;
    private String content;
    private String sqlText;
    private String markdownReport;
    private String reportFileUrl;
    /** JSON string of node traces. */
    private String nodeTraces;
    private LocalDateTime createTime;
}
