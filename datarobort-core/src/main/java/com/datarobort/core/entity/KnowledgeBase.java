package com.datarobort.core.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * Knowledge base configuration with chunking strategy and embedding model binding.
 */
@Data
public class KnowledgeBase {

    private Long id;
    private String name;
    private String description;
    /** fixed | heading | delimiter */
    private String chunkStrategy;
    /** Chunk size in characters. */
    private Integer chunkSize;
    /** Overlap characters between adjacent chunks. */
    private Integer chunkOverlap;
    /** Custom delimiter when chunk_strategy = delimiter. */
    private String delimiter;
    /** Bound embedding model id (model_config.id where type=embedding). */
    private Long embeddingModelId;
    /** Whether recall is enabled. */
    private Boolean recallEnabled;
    /** 1 enabled, 0 disabled. */
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
