-- DataRobort 平台库表结构（由 Spring Boot 自动初始化）
-- 运行于当前 dataSource，不切换数据库

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
    UNIQUE KEY uk_model_name (name),
    KEY idx_type (type)
) ENGINE = InnoDB COMMENT '模型配置';

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
    PRIMARY KEY (id),
    UNIQUE KEY uk_datasource_name (name)
) ENGINE = InnoDB COMMENT '业务数据源';

CREATE TABLE IF NOT EXISTS ds_table (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    ds_id         BIGINT       NOT NULL COMMENT '数据源 id',
    table_name    VARCHAR(128) NOT NULL,
    table_comment VARCHAR(512) DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_ds (ds_id)
) ENGINE = InnoDB COMMENT '数据源表元数据';

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

-- ============================================================
-- P2：知识增强体系
-- ============================================================

CREATE TABLE IF NOT EXISTS knowledge_base (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    name             VARCHAR(128) NOT NULL COMMENT '知识库名称',
    description      VARCHAR(512) DEFAULT NULL,
    chunk_strategy   VARCHAR(32)  NOT NULL DEFAULT 'fixed' COMMENT 'fixed | heading | delimiter',
    chunk_size       INT          NOT NULL DEFAULT 500 COMMENT '分块大小（字符）',
    chunk_overlap    INT          NOT NULL DEFAULT 50 COMMENT '重叠字符数',
    delimiter        VARCHAR(32)  DEFAULT NULL COMMENT '自定义分隔符（delimiter 策略时使用）',
    embedding_model_id BIGINT     DEFAULT NULL COMMENT '绑定的 Embedding 模型 id',
    recall_enabled   TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否开启召回',
    status           TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 0 停用',
    create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_embedding_model (embedding_model_id)
) ENGINE = InnoDB COMMENT '知识库';

CREATE TABLE IF NOT EXISTS document (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    kb_id         BIGINT       NOT NULL COMMENT '知识库 id',
    filename      VARCHAR(256) NOT NULL COMMENT '原始文件名',
    file_type     VARCHAR(16)  NOT NULL COMMENT 'pdf | docx | md | txt',
    file_size     BIGINT       DEFAULT NULL COMMENT '文件大小（字节）',
    plain_content LONGTEXT     DEFAULT NULL COMMENT '解析后的纯文本',
    status        VARCHAR(16)  NOT NULL DEFAULT 'parsing' COMMENT 'parsing | parsed | failed',
    error_msg     VARCHAR(512) DEFAULT NULL,
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_kb (kb_id)
) ENGINE = InnoDB COMMENT '文档';

CREATE TABLE IF NOT EXISTS chunk (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    doc_id        BIGINT       NOT NULL COMMENT 'document.id',
    kb_id         BIGINT       NOT NULL COMMENT '知识库 id（冗余便于查询）',
    content       TEXT         NOT NULL COMMENT '分块文本',
    chunk_index   INT          NOT NULL COMMENT '分块序号',
    vector_id     VARCHAR(256) DEFAULT NULL COMMENT 'Redis 向量 key',
    vector_status VARCHAR(16)  NOT NULL DEFAULT 'pending' COMMENT 'pending | done | failed',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_doc (doc_id),
    KEY idx_kb (kb_id),
    KEY idx_vector_status (vector_status)
) ENGINE = InnoDB COMMENT '文档分块';

CREATE TABLE IF NOT EXISTS business_knowledge (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    term          VARCHAR(256) NOT NULL COMMENT '业务术语',
    synonyms      TEXT         DEFAULT NULL COMMENT '同义词，逗号分隔',
    vector_status VARCHAR(16)  NOT NULL DEFAULT 'pending' COMMENT 'pending | done | failed',
    recall_enabled TINYINT(1)  NOT NULL DEFAULT 1 COMMENT '是否开启召回',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_term (term)
) ENGINE = InnoDB COMMENT '业务知识（同义词）';

CREATE TABLE IF NOT EXISTS semantic_model (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    ds_id          BIGINT       NOT NULL COMMENT '数据源 id',
    table_name     VARCHAR(128) NOT NULL COMMENT '表名',
    column_name    VARCHAR(128) DEFAULT NULL COMMENT '字段名（NULL 表示表级同义词）',
    synonyms       TEXT         DEFAULT NULL COMMENT '同义词，逗号分隔',
    vector_status  VARCHAR(16)  NOT NULL DEFAULT 'pending' COMMENT 'pending | done | failed',
    recall_enabled TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否开启召回',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ds (ds_id),
    UNIQUE KEY uk_semantic (ds_id, table_name, column_name)
) ENGINE = InnoDB COMMENT '语义模型（表/字段同义词）';

CREATE TABLE IF NOT EXISTS recall_log (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    query_text   VARCHAR(512) NOT NULL COMMENT '查询文本',
    source_type  VARCHAR(32)  NOT NULL COMMENT 'knowledge | business | semantic',
    source_id    BIGINT       DEFAULT NULL COMMENT '知识条目 id',
    source_title VARCHAR(256) DEFAULT NULL COMMENT '知识条目摘要',
    score        DOUBLE       DEFAULT NULL COMMENT '相似度分数',
    recalled     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否被召回（1 是 0 否）',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_query (query_text(128)),
    KEY idx_created (created_at)
) ENGINE = InnoDB COMMENT '召回日志（可观测性）';

-- ============================================================
-- P4：智能体管理 + 会话管理 + MCP
-- ============================================================

CREATE TABLE IF NOT EXISTS agent (
    id                       BIGINT       NOT NULL AUTO_INCREMENT,
    name                     VARCHAR(128) NOT NULL COMMENT '智能体名称',
    avatar                   VARCHAR(512) DEFAULT NULL COMMENT '头像地址',
    prompt                   TEXT         DEFAULT NULL COMMENT '自定义系统 Prompt（业务背景/角色设定）',
    status                   TINYINT      NOT NULL DEFAULT 0 COMMENT '1 已发布 0 草稿',
    business_recall_enabled  TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '业务知识召回开关',
    semantic_recall_enabled  TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '语义模型召回开关',
    create_time              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_name (name)
) ENGINE = InnoDB COMMENT '智能体';

CREATE TABLE IF NOT EXISTS agent_datasource (
    id       BIGINT NOT NULL AUTO_INCREMENT,
    agent_id BIGINT NOT NULL COMMENT 'agent.id',
    ds_id    BIGINT NOT NULL COMMENT 'datasource.id',
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_ds (agent_id, ds_id)
) ENGINE = InnoDB COMMENT '智能体-数据源关联';

CREATE TABLE IF NOT EXISTS agent_knowledge (
    id       BIGINT NOT NULL AUTO_INCREMENT,
    agent_id BIGINT NOT NULL COMMENT 'agent.id',
    kb_id    BIGINT NOT NULL COMMENT 'knowledge_base.id',
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_kb (agent_id, kb_id)
) ENGINE = InnoDB COMMENT '智能体-知识库关联';

CREATE TABLE IF NOT EXISTS conversation (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    agent_id    BIGINT       DEFAULT NULL COMMENT '绑定智能体（NULL 表示默认链路）',
    title       VARCHAR(256) DEFAULT NULL COMMENT '会话标题',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_agent (agent_id)
) ENGINE = InnoDB COMMENT '对话会话';

CREATE TABLE IF NOT EXISTS message (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT       NOT NULL COMMENT 'conversation.id',
    role            VARCHAR(16)  NOT NULL COMMENT 'user | assistant',
    content         TEXT         NOT NULL COMMENT '消息内容',
    sql_text        TEXT         DEFAULT NULL COMMENT '生成的 SQL（assistant）',
    markdown_report LONGTEXT     DEFAULT NULL COMMENT '分析报告 Markdown（assistant）',
    report_file_url VARCHAR(512) DEFAULT NULL COMMENT 'HTML 报告文件地址（assistant）',
    node_traces     LONGTEXT     DEFAULT NULL COMMENT '节点执行轨迹 JSON',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_conversation (conversation_id)
) ENGINE = InnoDB COMMENT '会话消息';
