package com.datarobort.core.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * Document uploaded to a knowledge base.
 */
@Data
public class Document {

    public static final String STATUS_PARSING = "parsing";
    public static final String STATUS_PARSED = "parsed";
    public static final String STATUS_FAILED = "failed";

    private Long id;
    private Long kbId;
    private String filename;
    /** pdf | docx | md | txt */
    private String fileType;
    private Long fileSize;
    /** Extracted plain text. */
    private String plainContent;
    /** parsing | parsed | failed */
    private String status;
    private String errorMsg;
    private LocalDateTime createTime;
}
