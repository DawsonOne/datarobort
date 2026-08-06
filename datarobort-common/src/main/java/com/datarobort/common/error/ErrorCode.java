package com.datarobort.common.error;

import lombok.Getter;

/**
 * Unified business error codes.
 * Segments: 0 success; 1xxx params/common; 2xxx model; 3xxx datasource;
 * 4xxx knowledge; 5xxx agent; 6xxx sandbox; 9xxx system.
 */
@Getter
public enum ErrorCode {

    SUCCESS("0", "success"),

    PARAM_INVALID("1001", "参数校验失败"),
    PARAM_MISSING("1002", "缺少必要参数"),
    NOT_FOUND("1004", "资源不存在"),

    MODEL_NOT_FOUND("2001", "模型配置不存在"),
    MODEL_CONNECT_FAILED("2002", "模型连通性测试失败"),
    EMBEDDING_DIM_MISMATCH("2003", "向量维度不一致"),

    DS_NOT_FOUND("3001", "数据源不存在"),
    DS_CONNECT_FAILED("3002", "数据源连接失败"),

    KB_NOT_FOUND("4001", "知识库不存在"),
    DOC_PARSE_FAILED("4002", "文档解析失败"),

    AGENT_NOT_FOUND("5001", "智能体不存在"),

    SANDBOX_TIMEOUT("6001", "沙箱执行超时"),
    SANDBOX_ERROR("6002", "沙箱执行失败"),

    SYSTEM_ERROR("9000", "系统内部错误");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
