package com.datarobort.core.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * A chat session. Optionally bound to an agent; null agentId means the
 * default pipeline.
 */
@Data
public class Conversation {

    private Long id;
    private Long agentId;
    private String title;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
