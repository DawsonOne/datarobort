package com.datarobort.core.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Model configuration (chat or embedding), OpenAI-compatible.
 */
@Data
public class ModelConfig {

    public static final String TYPE_CHAT = "chat";
    public static final String TYPE_EMBEDDING = "embedding";

    private Long id;
    /** Display name. */
    private String name;
    /** chat | embedding */
    private String type;
    /** Provider label, e.g. qwen / deepseek / vllm. */
    private String provider;
    /** OpenAI-compatible base url. */
    private String baseUrl;
    /** AES-encrypted api key (never returned to the frontend). */
    private String apiKey;
    /** Model name passed to the provider, e.g. qwen-plus. */
    private String modelName;
    /** Embedding output dimension, probed automatically on save. */
    private Integer dimension;
    /** Default model flag, unique per type. */
    private Boolean isDefault;
    /** Extra options as JSON string (temperature, top_p, ...). */
    private String params;
    /** 1 enabled, 0 disabled. */
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
