DROP TABLE IF EXISTS expert_issue;
DROP TABLE IF EXISTS expert_review;
DROP TABLE IF EXISTS arbiter_custom_annotation;
DROP TABLE IF EXISTS annotation_element;
DROP TABLE IF EXISTS proposition_adjudication_item;
DROP TABLE IF EXISTS annotation_version;
DROP TABLE IF EXISTS annotation_scope;
DROP TABLE IF EXISTS document_text_version;
-- H2(local) 初始化表结构。每次启动随内存库重建。
-- 顺序：先删子表再删父表（本文件不声明外键，靠约定维护顺序）。

-- 成员 C：教学评分（分工.md §四-7）
DROP TABLE IF EXISTS teaching_exercise;

-- 成员 C：关系差异与裁定（J06）
DROP TABLE IF EXISTS relation_adjudication_item;
DROP TABLE IF EXISTS adjudication_session;

-- 成员 C：关系与论证图（本轮新增）
DROP TABLE IF EXISTS graph_layout;
DROP TABLE IF EXISTS graph_member;
DROP TABLE IF EXISTS graph;
DROP TABLE IF EXISTS relation_participant;
DROP TABLE IF EXISTS relation;
DROP TABLE IF EXISTS mock_proposition;
DROP TABLE IF EXISTS mock_draft;

-- 成员 C：登录/RBAC（本轮仅建表与种子，登录切片再启用）
DROP TABLE IF EXISTS app_user_role;
DROP TABLE IF EXISTS app_role;
DROP TABLE IF EXISTS app_user;

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

-- 成员 C：G07/G15/G16 关系定义。direction_rule / min_participants /
-- max_participants / sort_order 为 G07 就绪契约要求，本轮扩展（表结构变更需全组确认）。
CREATE TABLE relation_definition (
    id                  VARCHAR(36)  PRIMARY KEY,
    guide_version_id    VARCHAR(36)  NOT NULL,
    code                VARCHAR(10)  NOT NULL,
    name                VARCHAR(50)  NOT NULL,
    definition          CLOB,
    directed            BOOLEAN,
    direction_rule      CLOB,
    min_participants    INT,
    max_participants    INT,
    sort_order          INT,
    expression_template VARCHAR(255),
    participant_rule    CLOB,
    diagram_rule        CLOB,
    record_version      INT          DEFAULT 0,
    created_at          TIMESTAMP,
    updated_at          TIMESTAMP,
    deleted             INT          DEFAULT 0
);

-- ─────────────────────────────────────────────────────────────────────
-- 成员 C：标注工作台的临时模拟数据
-- mock_draft / mock_proposition 是 A（文书与范围）、B（要素与命题）正式表就绪前的替身，
-- 只为让 C 的关系校验器能真校验“非命题不能参与关系”“同一快照”等规则。
-- 替换方式：换掉 PropositionSource 的实现即可，校验器与页面不动。
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE mock_draft (
    id                       VARCHAR(36)  PRIMARY KEY,
    task_document_id         VARCHAR(36),
    annotator_id             VARCHAR(36),
    guide_version_id         VARCHAR(36)  NOT NULL,
    document_text_version_id VARCHAR(36),
    annotation_scope_id      VARCHAR(36),
    status                   VARCHAR(20),
    record_version           INT          DEFAULT 0,
    created_at               TIMESTAMP,
    updated_at               TIMESTAMP,
    deleted                  INT          DEFAULT 0
);

CREATE TABLE mock_proposition (
    id                       VARCHAR(36)  PRIMARY KEY,
    draft_id                 VARCHAR(36)  NOT NULL,
    document_text_version_id VARCHAR(36),
    annotation_scope_id      VARCHAR(36),
    start_offset             INT          NOT NULL,
    end_offset               INT          NOT NULL,
    text                     CLOB         NOT NULL,
    label_definition_id      VARCHAR(36),
    label_code               VARCHAR(20)  NOT NULL,
    is_proposition           BOOLEAN      NOT NULL,
    display_no               VARCHAR(10),
    record_version           INT          DEFAULT 0,
    created_at               TIMESTAMP,
    updated_at               TIMESTAMP,
    deleted                  INT          DEFAULT 0
);

-- ─────────────────────────────────────────────────────────────────────
-- 成员 C：关系与论证图（R01–R04 / GR01–GR03）
-- relation 不存文本表达式、不存 from/to：端点只在 relation_participant。
-- graph 只做逻辑快照索引、不存坐标：坐标只在 graph_layout。
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE relation (
    id                     VARCHAR(36) PRIMARY KEY,
    owner_type             VARCHAR(20) NOT NULL,
    owner_id               VARCHAR(36) NOT NULL,
    relation_definition_id VARCHAR(36) NOT NULL,
    relation_code          VARCHAR(10) NOT NULL,
    directed               BOOLEAN     NOT NULL,
    status                 VARCHAR(20) NOT NULL,
    source_relation_ids    CLOB,
    record_version         INT         DEFAULT 0,
    created_at             TIMESTAMP,
    updated_at             TIMESTAMP,
    deleted                INT         DEFAULT 0
);

CREATE TABLE relation_participant (
    id               VARCHAR(36) PRIMARY KEY,
    relation_id      VARCHAR(36) NOT NULL,
    ordinal          INT         NOT NULL,
    role             VARCHAR(20) NOT NULL,
    target_kind      VARCHAR(20) NOT NULL,
    target_id        VARCHAR(36) NOT NULL,
    record_version   INT         DEFAULT 0,
    created_at       TIMESTAMP,
    updated_at       TIMESTAMP,
    deleted          INT         DEFAULT 0,
    CONSTRAINT uq_participant_ordinal UNIQUE (relation_id, ordinal)
);

CREATE TABLE graph (
    id              VARCHAR(36) PRIMARY KEY,
    owner_type      VARCHAR(20) NOT NULL,
    owner_id        VARCHAR(36) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    record_version  INT         DEFAULT 0,
    created_at      TIMESTAMP,
    updated_at      TIMESTAMP,
    deleted         INT         DEFAULT 0
);

CREATE TABLE graph_member (
    id           VARCHAR(36) PRIMARY KEY,
    graph_id     VARCHAR(36) NOT NULL,
    target_kind  VARCHAR(20) NOT NULL,
    target_id    VARCHAR(36) NOT NULL
);

CREATE TABLE graph_layout (
    id              VARCHAR(36) PRIMARY KEY,
    graph_id        VARCHAR(36) NOT NULL,
    layout_version  INT         NOT NULL,
    canvas_width    INT         NOT NULL,
    canvas_height   INT         NOT NULL,
    node_positions  CLOB,
    record_version  INT         DEFAULT 0,
    created_at      TIMESTAMP,
    updated_at      TIMESTAMP,
    deleted         INT         DEFAULT 0
);

-- ─────────────────────────────────────────────────────────────────────
-- 成员 C：登录 / Spring Security / RBAC（公共基础工作，占 C 总工作量 10–15%）
-- 本轮只建表与种子账号；登录切口在下一轮启用（届时再加 spring-boot-starter-security）。
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE app_user (
    id             VARCHAR(36)  PRIMARY KEY,
    username       VARCHAR(50)  NOT NULL,
    password_hash  VARCHAR(100),
    display_name   VARCHAR(50),
    status         VARCHAR(20),
    record_version INT          DEFAULT 0,
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP,
    deleted        INT          DEFAULT 0
);

CREATE TABLE app_role (
    id   VARCHAR(36) PRIMARY KEY,
    code VARCHAR(30) NOT NULL,
    name VARCHAR(50) NOT NULL
);

CREATE TABLE app_user_role (
    id      VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    role_id VARCHAR(36) NOT NULL
);

-- ─────────────────────────────────────────────────────────────────────
-- 成员 C：关系差异与裁定（J06「列出关系差异和一致项」）
--
-- 归属边界（《核心标注与裁定数据模型》对象字典 + 分工.md §八）：
--   · RelationAdjudicationItem 关系裁定项 —— 契约归 **C**；
--   · AdjudicationSession 裁定会话、AdjudicationDecision 裁定决定、
--     ArbiterCustomAnnotation 与整条状态机 —— 归 **D**（J05/J07/J08 写侧）。
-- 因此本表在本轮是 **C 侧只读投影 + 种子**：C 只读 status/current_phase 与
-- proposition_completed_at 来做「命题阶段未冻结就不许开关系裁定」的门禁，
-- 不对会话做任何写入。D 的流程切片落地时接管写侧，列名按同一对象字典保持不变。
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE adjudication_session (
    id                      VARCHAR(36) PRIMARY KEY,
    task_document_id        VARCHAR(36),
    arbiter_assignment_id   VARCHAR(36),
    source_version_ids      CLOB,
    -- 命题裁定冻结出的统一命题集合。真实写入者是 D 的 J05（命题阶段完成）；
    -- B 的命题裁定项（J03/J04，当前 PENDING_MODEL）在其中给出「哪条命题被采纳」。
    -- C 在此只读它，作为关系端点的共同底座。
    unified_snapshot_id     VARCHAR(36),
    status                  VARCHAR(30) NOT NULL,
    current_phase           VARCHAR(30) DEFAULT 'PROPOSITION' NOT NULL,
    proposition_completed_at TIMESTAMP,
    relation_completed_at   TIMESTAMP,
    record_version          INT         DEFAULT 0,
    created_at              TIMESTAMP,
    updated_at              TIMESTAMP,
    deleted                 INT         DEFAULT 0
);

-- relation_adjudication_item 的 conflict_type 取契约五枚举；
-- dimension / reason 是 C 侧**追加式超集**：把分工.md §四-4 的八个差异维度
-- （存在性/类型/方向/参与命题/组合成员/嵌套层级/一方新增/一方遗漏）与中文说明一并落库，
-- 因为契约的五个 conflictType 不足以表达「哪一维度不同」，而界面必须给出中文理由。
CREATE TABLE relation_adjudication_item (
    id                     VARCHAR(36) PRIMARY KEY,
    session_id             VARCHAR(36) NOT NULL,
    conflict_type          VARCHAR(20) NOT NULL,
    candidate_relation_ids CLOB,
    status                 VARCHAR(20) NOT NULL,
    dimension              VARCHAR(30),
    reason                 CLOB,
    decision_id            VARCHAR(36),
    record_version         INT         DEFAULT 0,
    created_at             TIMESTAMP,
    updated_at             TIMESTAMP,
    deleted                INT         DEFAULT 0
);

-- ─────────────────────────────────────────────────────────────────────
-- 成员 C：教学评分题库（分工.md §四-7 五类练习）
--
-- 只存**题目与标准答案**，不存学生数据：作答留档、成绩册、班级统计、进步趋势归 D（§五-6），
-- 由 D 调用 C 的 POST /teaching/exercises/{id}/score 得到同一份评分结果后自行汇总。
--
-- scope_ids / standard_relations 是 JSON（MyBatis-Plus JacksonTypeHandler）：
--   · scope_ids      本题允许使用的命题 ID 列表——评分时两侧在同一范围上建图，
--                    「独立论证图个数」才可比，否则学生少用一个命题会被误判成结构错；
--   · standard_relations 标准答案的**模板**（ref/code/participants），不是渲染后的表达式。
--                    模板经 RelationTemplateCodec 解析，与学生的作答走同一个解析器、同一个
--                    校验器、同一套等价判据；把标准答案存成字符串会让「标准」与「作答」
--                    走在两条代码路径上，评分口径必然漂移。
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE teaching_exercise (
    id                 VARCHAR(36)  PRIMARY KEY,
    exercise_code      VARCHAR(30)  NOT NULL,
    exercise_type      VARCHAR(30)  NOT NULL,
    title              VARCHAR(200) NOT NULL,
    prompt_text        CLOB,
    snapshot_id        VARCHAR(36)  NOT NULL,
    scope_ids          CLOB,
    standard_relations CLOB,
    explanation        CLOB,
    sort_order         INT          DEFAULT 0,
    record_version     INT          DEFAULT 0,
    created_at         TIMESTAMP,
    updated_at         TIMESTAMP,
    deleted            INT          DEFAULT 0
);

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

CREATE INDEX idx_element_owner ON annotation_element(owner_type, owner_id);

CREATE INDEX idx_prop_item_session ON proposition_adjudication_item(session_id);

CREATE INDEX idx_expert_review_expert ON expert_review(expert_task_member_id);

CREATE INDEX idx_expert_review_session ON expert_review(session_id);

CREATE INDEX idx_expert_issue_review ON expert_issue(expert_review_id);
