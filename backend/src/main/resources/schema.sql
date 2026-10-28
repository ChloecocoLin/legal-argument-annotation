-- H2(local) 初始化表结构。每次启动随内存库重建。
DROP TABLE IF EXISTS expert_issue;
DROP TABLE IF EXISTS expert_review;
DROP TABLE IF EXISTS arbiter_custom_annotation;
DROP TABLE IF EXISTS annotation_element;
DROP TABLE IF EXISTS proposition_adjudication_item;
DROP TABLE IF EXISTS adjudication_session;
DROP TABLE IF EXISTS annotation_version;
DROP TABLE IF EXISTS annotation_scope;
DROP TABLE IF EXISTS document_text_version;
DROP TABLE IF EXISTS relation_definition;
DROP TABLE IF EXISTS label_definition;
DROP TABLE IF EXISTS guide_version;
DROP TABLE IF EXISTS guide;

CREATE TABLE guide (
    id              VARCHAR(36)  PRIMARY KEY,
    title           VARCHAR(200) NOT NULL,
    description     VARCHAR(1000),
    status          VARCHAR(20)  NOT NULL,
    record_version  INT          DEFAULT 0,
    created_at      TIMESTAMP,
    updated_at      TIMESTAMP,
    deleted         INT          DEFAULT 0
);

CREATE TABLE guide_version (
    id                  VARCHAR(36)  PRIMARY KEY,
    guide_id            VARCHAR(36)  NOT NULL,
    version_code        VARCHAR(20)  NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    attachment_file_id  VARCHAR(36),
    file_name           VARCHAR(255),
    file_size           BIGINT,
    page_count          INT,
    uploaded_by         VARCHAR(50),
    published_at        TIMESTAMP,
    record_version      INT          DEFAULT 0,
    created_at          TIMESTAMP,
    updated_at          TIMESTAMP,
    deleted             INT          DEFAULT 0
);

CREATE TABLE label_definition (
    id                VARCHAR(36)  PRIMARY KEY,
    guide_version_id  VARCHAR(36)  NOT NULL,
    code              VARCHAR(20)  NOT NULL,
    name              VARCHAR(100) NOT NULL,
    category          VARCHAR(20),
    base_type         VARCHAR(20),
    sort_order        INT,
    definition        CLOB,
    recognition_rules CLOB,
    signal_phrases    CLOB,
    examples          CLOB,
    notes             CLOB,
    record_version    INT          DEFAULT 0,
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP,
    deleted           INT          DEFAULT 0
);

CREATE TABLE relation_definition (
    id                  VARCHAR(36)  PRIMARY KEY,
    guide_version_id    VARCHAR(36)  NOT NULL,
    code                VARCHAR(10)  NOT NULL,
    name                VARCHAR(50)  NOT NULL,
    definition          CLOB,
    directed            BOOLEAN,
    expression_template VARCHAR(255),
    participant_rule    CLOB,
    diagram_rule        CLOB,
    record_version      INT          DEFAULT 0,
    created_at          TIMESTAMP,
    updated_at          TIMESTAMP,
    deleted             INT          DEFAULT 0
);

-- M3 本地联调所需的裁定只读依赖数据。流程写操作仍归其他成员负责。
CREATE TABLE document_text_version (
    id                VARCHAR(36) PRIMARY KEY,
    document_id       VARCHAR(36) NOT NULL,
    version_number    INT NOT NULL,
    normalized_text   CLOB NOT NULL,
    content_sha256    VARCHAR(64),
    status            VARCHAR(20) NOT NULL,
    record_version    INT DEFAULT 0,
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP
);

CREATE TABLE annotation_scope (
    id                        VARCHAR(36) PRIMARY KEY,
    document_text_version_id  VARCHAR(36) NOT NULL,
    scope_type                VARCHAR(20) NOT NULL,
    start_offset              INT NOT NULL,
    end_offset                INT NOT NULL,
    selection_method          VARCHAR(20),
    status                    VARCHAR(20) NOT NULL,
    record_version            INT DEFAULT 0,
    created_at                TIMESTAMP,
    updated_at                TIMESTAMP
);

CREATE TABLE annotation_version (
    id                        VARCHAR(36) PRIMARY KEY,
    display_code              VARCHAR(30) NOT NULL,
    document_text_version_id  VARCHAR(36) NOT NULL,
    annotation_scope_id       VARCHAR(36) NOT NULL,
    guide_version_id          VARCHAR(36) NOT NULL,
    status                    VARCHAR(30) NOT NULL,
    record_version            INT DEFAULT 0,
    created_at                TIMESTAMP,
    updated_at                TIMESTAMP
);

CREATE TABLE adjudication_session (
    id                        VARCHAR(36) PRIMARY KEY,
    task_document_id          VARCHAR(36) NOT NULL,
    status                    VARCHAR(30) NOT NULL,
    source_version_ids        CLOB NOT NULL,
    proposition_completed_at  TIMESTAMP,
    record_version            INT DEFAULT 0,
    created_at                TIMESTAMP,
    updated_at                TIMESTAMP
);

CREATE TABLE proposition_adjudication_item (
    id                     VARCHAR(36) PRIMARY KEY,
    session_id             VARCHAR(36) NOT NULL,
    conflict_type          VARCHAR(20) NOT NULL,
    candidate_element_ids  CLOB NOT NULL,
    status                 VARCHAR(30) NOT NULL,
    record_version         INT DEFAULT 0,
    created_at             TIMESTAMP,
    updated_at             TIMESTAMP
);

-- Element 与裁定者自定义快照属于成员 B 写域。
CREATE TABLE annotation_element (
    id                        VARCHAR(36) PRIMARY KEY,
    owner_type                VARCHAR(30) NOT NULL,
    owner_id                  VARCHAR(36) NOT NULL,
    client_key                VARCHAR(100),
    document_text_version_id  VARCHAR(36) NOT NULL,
    annotation_scope_id       VARCHAR(36) NOT NULL,
    start_offset              INT NOT NULL,
    end_offset                INT NOT NULL,
    text                      CLOB NOT NULL,
    label_definition_id       VARCHAR(36) NOT NULL,
    label_code                VARCHAR(20) NOT NULL,
    is_proposition            BOOLEAN NOT NULL,
    display_no                VARCHAR(30),
    source_element_ids        CLOB NOT NULL,
    record_version            INT DEFAULT 0,
    created_at                TIMESTAMP,
    updated_at                TIMESTAMP
);

CREATE TABLE arbiter_custom_annotation (
    id               VARCHAR(36) PRIMARY KEY,
    session_id       VARCHAR(36) NOT NULL,
    item_kind        VARCHAR(20) NOT NULL,
    item_id          VARCHAR(36) NOT NULL,
    element_ids      CLOB NOT NULL,
    relation_ids     CLOB NOT NULL,
    reason           VARCHAR(500) NOT NULL,
    idempotency_key  VARCHAR(100) NOT NULL UNIQUE,
    request_hash     VARCHAR(64) NOT NULL,
    record_version   INT DEFAULT 0,
    created_at       TIMESTAMP,
    updated_at       TIMESTAMP
);

CREATE INDEX idx_element_owner ON annotation_element(owner_type, owner_id);
CREATE INDEX idx_prop_item_session ON proposition_adjudication_item(session_id);

-- 专家复核（成员B M4）。复核单不存问题正文，问题统一存 expert_issue。
CREATE TABLE expert_review (
    id                     VARCHAR(36) PRIMARY KEY,
    session_id             VARCHAR(36) NOT NULL,
    expert_task_member_id  VARCHAR(36) NOT NULL,
    requested_by_id        VARCHAR(36) NOT NULL,
    status                 VARCHAR(30) NOT NULL,
    opened_at              TIMESTAMP,
    confirmed_at           TIMESTAMP,
    record_version         INT DEFAULT 0,
    created_at             TIMESTAMP,
    updated_at             TIMESTAMP
);

CREATE TABLE expert_issue (
    id                VARCHAR(36) PRIMARY KEY,
    expert_review_id  VARCHAR(36) NOT NULL,
    item_kind         VARCHAR(20) NOT NULL,
    item_id           VARCHAR(36),
    question          CLOB NOT NULL,
    resolution         CLOB,
    guide_basis       CLOB,
    status            VARCHAR(20) NOT NULL,
    resolved_at       TIMESTAMP,
    record_version    INT DEFAULT 0,
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP
);

CREATE INDEX idx_expert_review_expert ON expert_review(expert_task_member_id);
CREATE INDEX idx_expert_review_session ON expert_review(session_id);
CREATE INDEX idx_expert_issue_review ON expert_issue(expert_review_id);
