package com.datarobort.core.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * Text chunk produced from a document, to be embedded and stored in Redis vector index.
 */
@Data
public class Chunk {

    public static final String VEC_PENDING = "pending";
    public static final String VEC_DONE = "done";
    public static final String VEC_FAILED = "failed";

    private Long id;
    private Long docId;
    private Long kbId;
    private String content;
    private Integer chunkIndex;
    /** Redis hash key for the stored vector. */
    private String vectorId;
    /** pending | done | failed */
    private String vectorStatus;
    private LocalDateTime createTime;
}
