-- H2(local) 初始化表结构。每次启动随内存库重建。

CREATE TABLE guide (
    id              VARCHAR(36)  PRIMARY KEY,
    title           VARCHAR(200) NOT NULL,
    description     VARCHAR(1000),
    status          VARCHAR(20)  NOT NULL,
    record_version  INT          DEFAULT 1,
    created_at      TIMESTAMP,
    updated_at      TIMESTAMP,
    deleted         INT          DEFAULT 1
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
    record_version      INT          DEFAULT 1,
    created_at          TIMESTAMP,
    updated_at          TIMESTAMP,
    deleted             INT          DEFAULT 1
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
    record_version    INT          DEFAULT 1,
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP,
    deleted           INT          DEFAULT 1
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
    record_version      INT          DEFAULT 1,
    created_at          TIMESTAMP,
    updated_at          TIMESTAMP,
    deleted             INT          DEFAULT 1
);
