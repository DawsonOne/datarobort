-- DataRobort 平台库建表脚本（P1）
-- 目标库：MySQL 8.0，utf8mb4

CREATE DATABASE IF NOT EXISTS datarobort DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE datarobort;

-- 模型配置（chat / embedding，OpenAI 兼容）
CREATE TABLE IF NOT EXISTS model_config (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(128) NOT NULL COMMENT '显示名称',
    type        VARCHAR(16)  NOT NULL COMMENT 'chat | embedding',
    provider    VARCHAR(64)  DEFAULT NULL COMMENT '提供商标识：qwen/deepseek/vllm...',
    base_url    VARCHAR(512) NOT NULL COMMENT 'OpenAI 兼容 base url',
    api_key     VARCHAR(1024) DEFAULT NULL COMMENT 'AES 加密后的 api key',
    model_name  VARCHAR(128) NOT NULL COMMENT '模型名，如 qwen-plus',
    dimension   INT          DEFAULT NULL COMMENT 'embedding 输出维度（保存时自动探测）',
    is_default  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否该类型默认模型',
    params      TEXT         DEFAULT NULL COMMENT '扩展参数 JSON',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 0 停用',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_type (type)
) ENGINE = InnoDB COMMENT '模型配置';

-- 业务数据源
CREATE TABLE IF NOT EXISTS datasource (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(128) NOT NULL COMMENT '显示名称',
    type        VARCHAR(32)  NOT NULL DEFAULT 'mysql' COMMENT 'mysql | postgresql',
    jdbc_url    VARCHAR(512) NOT NULL COMMENT 'JDBC 连接串',
    username    VARCHAR(128) DEFAULT NULL,
    password    VARCHAR(1024) DEFAULT NULL COMMENT 'AES 加密后的密码',
    description VARCHAR(512) DEFAULT NULL,
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 0 停用',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB COMMENT '业务数据源';

-- 数据源表元数据
CREATE TABLE IF NOT EXISTS ds_table (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    ds_id         BIGINT       NOT NULL COMMENT '数据源 id',
    table_name    VARCHAR(128) NOT NULL,
    table_comment VARCHAR(512) DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_ds (ds_id)
) ENGINE = InnoDB COMMENT '数据源表元数据';

-- 数据源字段元数据
CREATE TABLE IF NOT EXISTS ds_column (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    table_id       BIGINT       NOT NULL COMMENT 'ds_table.id',
    column_name    VARCHAR(128) NOT NULL,
    data_type      VARCHAR(64)  DEFAULT NULL COMMENT 'JDBC 类型名',
    column_comment VARCHAR(512) DEFAULT NULL,
    is_primary     TINYINT(1)   NOT NULL DEFAULT 0,
    nullable       TINYINT(1)   NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    KEY idx_table (table_id)
) ENGINE = InnoDB COMMENT '数据源字段元数据';
