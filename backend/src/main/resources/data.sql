-- H2(local) 初始演示数据，对齐原型 assets/data.js。

-- 指南容器（原型列表三行，每个指南含一个版本）
INSERT INTO guide (id, title, description, status, record_version, created_at, updated_at, deleted) VALUES
('g-12', '中文裁判文书说理部分法律论证结构标注与图示指南', '现行版本，供新建标注任务选择。', 'ACTIVE', 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('g-11', '中文裁判文书说理部分法律论证结构标注与图示指南', '历史版本，已归档。', 'ARCHIVED', 0, TIMESTAMP '2026-03-02 10:00:00', TIMESTAMP '2026-03-02 10:00:00', 0),
('g-10', '裁判理由法律论证标注规范（试行）', '早期试行规范，已归档。', 'ARCHIVED', 0, TIMESTAMP '2025-11-20 10:00:00', TIMESTAMP '2025-11-20 10:00:00', 0);

-- 指南版本
INSERT INTO guide_version (id, guide_id, version_code, status, attachment_file_id, file_name, file_size, page_count, uploaded_by, published_at, record_version, created_at, updated_at, deleted) VALUES
('v-g-12', 'g-12', '1.2', 'PUBLISHED', 'f-v12', '中文裁判文书说理部分法律论证结构标注与图示指南_v1.2.docx', 377856, 42, '王慧', TIMESTAMP '2026-08-14 10:00:00', 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('v-g-11', 'g-11', '1.1', 'ARCHIVED', 'f-v11', '中文裁判文书说理部分法律论证结构标注与图示指南_v1.1.docx', 319488, 37, '王慧', TIMESTAMP '2026-03-02 10:00:00', 0, TIMESTAMP '2026-03-02 10:00:00', TIMESTAMP '2026-03-02 10:00:00', 0),
('v-g-10', 'g-10', '1.0', 'ARCHIVED', 'f-v10', '裁判理由法律论证标注规范（试行）_v1.0.docx', 253952, 29, '王慧', TIMESTAMP '2025-11-20 10:00:00', 0, TIMESTAMP '2025-11-20 10:00:00', TIMESTAMP '2025-11-20 10:00:00', 0);

-- v1.2 标签定义（非命题6 + GM5 + SM2 + GF/SF = 15）。
-- 合同及合同解释属于 SM-C；正式 v1.2 不启用原型中的 GM-C。
INSERT INTO label_definition (id, guide_version_id, code, name, category, base_type, sort_order, definition, recognition_rules, signal_phrases, examples, notes, record_version, created_at, updated_at, deleted) VALUES
('l12-is',    'v-g-12', 'IS',    '争议焦点',         'NON_PROPOSITION', NULL, 1,  NULL, '[]', '[]', '[]', NULL, 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('l12-as',    'v-g-12', 'AS',    '态度声明',         'NON_PROPOSITION', NULL, 2,  NULL, '[]', '[]', '[]', NULL, 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('l12-ss',    'v-g-12', 'SS',    '总结性陈述',       'NON_PROPOSITION', NULL, 3,  NULL, '[]', '[]', '[]', NULL, 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('l12-jg',    'v-g-12', 'JG',    '判决依据',         'NON_PROPOSITION', NULL, 4,  NULL, '[]', '[]', '[]', NULL, 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('l12-jd',    'v-g-12', 'JD',    '判决结果',         'NON_PROPOSITION', NULL, 5,  NULL, '[]', '[]', '[]', NULL, 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('l12-nono',  'v-g-12', 'Non-O', '其它非论证成分',   'NON_PROPOSITION', NULL, 6,  NULL, '[]', '[]', '[]', NULL, 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('l12-gml',   'v-g-12', 'GM-L',  '法律条文',         'PROPOSITION', 'GM', 10, NULL, '[]', '[]', '[]', NULL, 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('l12-gmi',   'v-g-12', 'GM-I',  '法律解释',         'PROPOSITION', 'GM', 11, NULL, '[]', '[]', '[]', NULL, 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('l12-gmu',   'v-g-12', 'GM-U',  '习惯与行业惯例',   'PROPOSITION', 'GM', 13, NULL, '[]', '[]', '[]', NULL, 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('l12-gmm',   'v-g-12', 'GM-M',  '道德与价值观念',   'PROPOSITION', 'GM', 14, NULL, '[]', '[]', '[]', NULL, 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('l12-gmo',   'v-g-12', 'GM-O',  '其他规范判断',     'PROPOSITION', 'GM', 15, NULL, '[]', '[]', '[]', NULL, 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('l12-sm',    'v-g-12', 'SM',    '普通个别规范判断', 'PROPOSITION', 'SM', 20, NULL, '[]', '[]', '[]', NULL, 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('l12-smc',   'v-g-12', 'SM-C',  '合同及合同解释',   'PROPOSITION', 'SM', 21, NULL, '[]', '[]', '[]', NULL, 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('l12-gf',    'v-g-12', 'GF',    '一般事实判断',     'PROPOSITION', 'GF', 30, NULL, '[]', '[]', '[]', NULL, 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('l12-sf',    'v-g-12', 'SF',    '个别事实判断',     'PROPOSITION', 'SF', 31, NULL, '[]', '[]', '[]', NULL, 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0);

-- v1.1 标签定义（非命题6 + GM5 无GM-C + SM1 无SM-C + GF/SF = 14）
INSERT INTO label_definition (id, guide_version_id, code, name, category, base_type, sort_order, definition, recognition_rules, signal_phrases, examples, notes, record_version, created_at, updated_at, deleted) VALUES
('l11-is',    'v-g-11', 'IS',    '争议焦点',         'NON_PROPOSITION', NULL, 1,  NULL, '[]', '[]', '[]', NULL, 0, TIMESTAMP '2026-03-02 10:00:00', TIMESTAMP '2026-03-02 10:00:00', 0),
('l11-as',    'v-g-11', 'AS',    '态度声明',         'NON_PROPOSITION', NULL, 2,  NULL, '[]', '[]', '[]', NULL, 0, TIMESTAMP '2026-03-02 10:00:00', TIMESTAMP '2026-03-02 10:00:00', 0),
('l11-ss',    'v-g-11', 'SS',    '总结性陈述',       'NON_PROPOSITION', NULL, 3,  NULL, '[]', '[]', '[]', NULL, 0, TIMESTAMP '2026-03-02 10:00:00', TIMESTAMP '2026-03-02 10:00:00', 0),
('l11-jg',    'v-g-11', 'JG',    '判决依据',         'NON_PROPOSITION', NULL, 4,  NULL, '[]', '[]', '[]', NULL, 0, TIMESTAMP '2026-03-02 10:00:00', TIMESTAMP '2026-03-02 10:00:00', 0),
('l11-jd',    'v-g-11', 'JD',    '判决结果',         'NON_PROPOSITION', NULL, 5,  NULL, '[]', '[]', '[]', NULL, 0, TIMESTAMP '2026-03-02 10:00:00', TIMESTAMP '2026-03-02 10:00:00', 0),
('l11-nono',  'v-g-11', 'Non-O', '其它非论证成分',   'NON_PROPOSITION', NULL, 6,  NULL, '[]', '[]', '[]', NULL, 0, TIMESTAMP '2026-03-02 10:00:00', TIMESTAMP '2026-03-02 10:00:00', 0),
('l11-gml',   'v-g-11', 'GM-L',  '法律条文',         'PROPOSITION', 'GM', 10, NULL, '[]', '[]', '[]', NULL, 0, TIMESTAMP '2026-03-02 10:00:00', TIMESTAMP '2026-03-02 10:00:00', 0),
('l11-gmi',   'v-g-11', 'GM-I',  '法律解释',         'PROPOSITION', 'GM', 11, NULL, '[]', '[]', '[]', NULL, 0, TIMESTAMP '2026-03-02 10:00:00', TIMESTAMP '2026-03-02 10:00:00', 0),
('l11-gmu',   'v-g-11', 'GM-U',  '习惯与行业惯例',   'PROPOSITION', 'GM', 13, NULL, '[]', '[]', '[]', NULL, 0, TIMESTAMP '2026-03-02 10:00:00', TIMESTAMP '2026-03-02 10:00:00', 0),
('l11-gmm',   'v-g-11', 'GM-M',  '道德与价值观念',   'PROPOSITION', 'GM', 14, NULL, '[]', '[]', '[]', NULL, 0, TIMESTAMP '2026-03-02 10:00:00', TIMESTAMP '2026-03-02 10:00:00', 0),
('l11-gmo',   'v-g-11', 'GM-O',  '其他规范判断',     'PROPOSITION', 'GM', 15, NULL, '[]', '[]', '[]', NULL, 0, TIMESTAMP '2026-03-02 10:00:00', TIMESTAMP '2026-03-02 10:00:00', 0),
('l11-sm',    'v-g-11', 'SM',    '普通个别规范判断', 'PROPOSITION', 'SM', 20, NULL, '[]', '[]', '[]', NULL, 0, TIMESTAMP '2026-03-02 10:00:00', TIMESTAMP '2026-03-02 10:00:00', 0),
('l11-gf',    'v-g-11', 'GF',    '一般事实判断',     'PROPOSITION', 'GF', 30, NULL, '[]', '[]', '[]', NULL, 0, TIMESTAMP '2026-03-02 10:00:00', TIMESTAMP '2026-03-02 10:00:00', 0),
('l11-sf',    'v-g-11', 'SF',    '个别事实判断',     'PROPOSITION', 'SF', 31, NULL, '[]', '[]', '[]', NULL, 0, TIMESTAMP '2026-03-02 10:00:00', TIMESTAMP '2026-03-02 10:00:00', 0);

-- v1.2 关系定义（S/A/M 有向，J/I 无向；G07/G15/G16 归成员C）。
-- direction_rule / min_participants / max_participants / sort_order 四列由 G07 就绪契约要求，
-- 文本取自 mock-data/base/relation-definitions.json 与《公共规范与基础数据模型》§2.6。
INSERT INTO relation_definition (id, guide_version_id, code, name, definition, directed, direction_rule, min_participants, max_participants, sort_order, expression_template, participant_rule, diagram_rule, record_version, created_at, updated_at, deleted) VALUES
('r12-s', 'v-g-12', 'S', '支持关系',
 '一个命题或命题组为另一命题成立提供直接理由。', TRUE,
 '从支持者指向被支持命题。', 2, 2, 1, 'S(x, y)',
 '二元有序；x 可为命题或 J/M 等关系整体，y 为目标命题。只标直接支持，不自动补传递边。',
 '矩形命题节点之间经实心圆关系节点形成有向边，箭头指向被支持命题。',
 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('r12-a', 'v-g-12', 'A', '反对关系',
 '一个命题对另一命题或支持结构表达否定。', TRUE,
 '从反对者指向被反对的命题或关系节点。', 2, 2, 2, 'A(x, y)',
 '二元有序；典型为命题对命题，允许 A(po,S(pi,pj))。反对须真实表达否定，转折词本身不足以成立。',
 '矩形命题节点之间经空心圆关系节点形成有向边，箭头指向被反对者。',
 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('r12-j', 'v-g-12', 'J', '组合关系',
 '多个命题或已成立的关系共同构成一个理由，缺一不可。', FALSE,
 '无方向；成员按展示序号从小到大排列。', 2, NULL, 3, 'J(x1, x2, ..., xn)',
 '至少两个命题或已成立的关系整体；每个成员均不能单独承担共同支持。区别于多个独立 S。',
 '各成员经带“+”圆节点作无向连线。',
 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('r12-m', 'v-g-12', 'M', '匹配关系',
 '个别判断与一般判断之间的对应关系。', TRUE,
 '从个别判断或其组合指向一般判断。', 2, 2, 4, 'M(individual, general)',
 '二元有序；左侧可为个别命题或 J(多个个别判断)，右侧为一般判断；必要时整体嵌入 S 或 J。',
 '经带“+”圆节点作有向边，箭头指向一般判断。',
 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('r12-i', 'v-g-12', 'I', '同一关系',
 '两个及以上命题语义几乎相同。', FALSE,
 '无方向；成员按展示序号排列。', 2, NULL, 5, 'I(p1, p2, ..., pn)',
 '至少两个命题，且仅限命题；同类型优先审查，关系确认后用最大序号命题继续关系标注。',
 '同组命题以矩形框内“/”表示。',
 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0);

-- ─────────────────────────────────────────────────────────────────────
-- 成员 C：标注工作台临时模拟数据（A/B 的文书、要素正式表就绪前的替身）
-- start_offset/end_offset 为示意值（半开区间、递增不重叠），真实切片由 A 的文书版本提供。
-- ─────────────────────────────────────────────────────────────────────
INSERT INTO mock_draft (id, task_document_id, annotator_id, guide_version_id, document_text_version_id, annotation_scope_id, status, record_version, created_at, updated_at, deleted) VALUES
('md-1', 'td-1', 'u-ann', 'v-g-12', 'dtv-1', 'as-1', 'DRAFT', 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0);

-- 命题 P1–P8 + 非命题 2 项（IS、JD）。非命题不占 P 编号，display_no 为 NULL。
INSERT INTO mock_proposition (id, draft_id, document_text_version_id, annotation_scope_id, start_offset, end_offset, text, label_definition_id, label_code, is_proposition, display_no, record_version, created_at, updated_at, deleted) VALUES
('mp-9',  'md-1', 'dtv-1', 'as-1', 0,   21,  '本案争议焦点为被告是否应支付剩余劳务报酬。',                 'l12-is',   'IS',   FALSE, NULL, 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('mp-1',  'md-1', 'dtv-1', 'as-1', 22,  37,  '依法成立的合同，自成立时生效。',                             'l12-gml',  'GM-L', TRUE,  'P1', 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('mp-2',  'md-1', 'dtv-1', 'as-1', 38,  68,  '原告与被告于2025年3月签订劳务合同，约定报酬为6000元。',       'l12-sf',   'SF',   TRUE,  'P2', 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('mp-3',  'md-1', 'dtv-1', 'as-1', 69,  84,  '原告已按约定完成全部劳务工作。',                             'l12-sf',   'SF',   TRUE,  'P3', 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('mp-4',  'md-1', 'dtv-1', 'as-1', 85,  96,  '被告仅支付5400元。',                                        'l12-sf',   'SF',   TRUE,  'P4', 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('mp-5',  'md-1', 'dtv-1', 'as-1', 97,  112, '被告应当支付剩余600元报酬。',                                'l12-sm',   'SM',   TRUE,  'P5', 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('mp-6',  'md-1', 'dtv-1', 'as-1', 113, 132, '当事人对自己提出的主张应当提供证据。',                       'l12-gf',   'GF',   TRUE,  'P6', 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('mp-7',  'md-1', 'dtv-1', 'as-1', 133, 148, '原告主张另有劳务报酬600元。',                                'l12-sf',   'SF',   TRUE,  'P7', 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('mp-8',  'md-1', 'dtv-1', 'as-1', 149, 166, '但其提供的证据不足以证明其主张。',                           'l12-sf',   'SF',   TRUE,  'P8', 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('mp-10', 'md-1', 'dtv-1', 'as-1', 167, 187, '判决如下：被告支付原告劳务报酬600元。',                       'l12-jd',   'JD',   FALSE, NULL, 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0);

-- 演示关系：图示一 = J(P2,P3) → S → P5，并补充 S(P1,P5)、S(P4,P5)；
-- 图示二 = A(P7,P8)、S(P6,P8)、M(P7,P6)。两组互不相连，用于演示“多个独立论证图”。
-- rel-2 的 SOURCE 指向关系 rel-1（target_kind=RELATION），用于演示关系嵌套不压平。
INSERT INTO relation (id, owner_type, owner_id, relation_definition_id, relation_code, directed, status, source_relation_ids, record_version, created_at, updated_at, deleted) VALUES
('rel-1', 'DRAFT', 'md-1', 'r12-j', 'J', FALSE, 'ACTIVE', '[]', 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('rel-2', 'DRAFT', 'md-1', 'r12-s', 'S', TRUE,  'ACTIVE', '[]', 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('rel-3', 'DRAFT', 'md-1', 'r12-s', 'S', TRUE,  'ACTIVE', '[]', 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('rel-4', 'DRAFT', 'md-1', 'r12-s', 'S', TRUE,  'ACTIVE', '[]', 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('rel-5', 'DRAFT', 'md-1', 'r12-a', 'A', TRUE,  'ACTIVE', '[]', 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('rel-6', 'DRAFT', 'md-1', 'r12-s', 'S', TRUE,  'ACTIVE', '[]', 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('rel-7', 'DRAFT', 'md-1', 'r12-m', 'M', TRUE,  'ACTIVE', '[]', 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0);

INSERT INTO relation_participant (id, relation_id, ordinal, role, target_kind, target_id, record_version, created_at, updated_at, deleted) VALUES
('rp-1-1', 'rel-1', 1, 'MEMBER', 'ELEMENT', 'mp-2', 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('rp-1-2', 'rel-1', 2, 'MEMBER', 'ELEMENT', 'mp-3', 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('rp-2-1', 'rel-2', 1, 'SOURCE', 'RELATION', 'rel-1', 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('rp-2-2', 'rel-2', 2, 'TARGET', 'ELEMENT', 'mp-5', 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('rp-3-1', 'rel-3', 1, 'SOURCE', 'ELEMENT', 'mp-1', 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('rp-3-2', 'rel-3', 2, 'TARGET', 'ELEMENT', 'mp-5', 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('rp-4-1', 'rel-4', 1, 'SOURCE', 'ELEMENT', 'mp-4', 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('rp-4-2', 'rel-4', 2, 'TARGET', 'ELEMENT', 'mp-5', 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('rp-5-1', 'rel-5', 1, 'SOURCE', 'ELEMENT', 'mp-7', 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('rp-5-2', 'rel-5', 2, 'TARGET', 'ELEMENT', 'mp-8', 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('rp-6-1', 'rel-6', 1, 'SOURCE', 'ELEMENT', 'mp-6', 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('rp-6-2', 'rel-6', 2, 'TARGET', 'ELEMENT', 'mp-8', 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('rp-7-1', 'rel-7', 1, 'SOURCE', 'ELEMENT', 'mp-7', 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('rp-7-2', 'rel-7', 2, 'TARGET', 'ELEMENT', 'mp-6', 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0);

-- ─────────────────────────────────────────────────────────────────────
-- 成员 C：登录 / RBAC 种子（本轮不启用鉴权，密码为占位；登录切片改为真实哈希）
-- 角色取值对齐前端 stores/user.ts 的 UserRole。
-- ─────────────────────────────────────────────────────────────────────
INSERT INTO app_role (id, code, name) VALUES
('role-admin',     'admin',     '管理员'),
('role-annotator', 'annotator', '标注员'),
('role-arbiter',   'arbiter',   '裁定员'),
('role-expert',    'expert',    '专家'),
('role-student',   'student',   '学生'),
('role-teacher',   'teacher',   '教师');

INSERT INTO app_user (id, username, password_hash, display_name, status, record_version, created_at, updated_at, deleted) VALUES
('u-admin', 'legal.researcher', '{noop}demo', '王慧', 'ACTIVE', 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('u-ann',   'ann.li',           '{noop}demo', '李铭', 'ACTIVE', 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('u-arb',   'arb.zhang',        '{noop}demo', '张宁', 'ACTIVE', 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0);

INSERT INTO app_user_role (id, user_id, role_id) VALUES
('ur-1', 'u-admin', 'role-admin'),
('ur-2', 'u-ann',   'role-annotator'),
('ur-3', 'u-arb',   'role-arbiter');

-- ─────────────────────────────────────────────────────────────────────
-- 成员 C：关系差异与裁定的演示数据（J06）
--
-- 场景对齐《核心标注与裁定数据模型》§Mock 场景：两名标注员对同一文书独立标注，
-- 裁定者先完成命题裁定（形成**统一命题集合** us-1），再比较关系。
--   · av-1 / av-2 —— 两份独立标注版本（各自一套命题记录与关系），owner_type=ANNOTATION_VERSION；
--   · us-1        —— 命题裁定后的统一命题集合，是关系比较的共同底座；
--                    真实系统中由 B 的命题裁定（J03/J04，当前 PENDING_MODEL）产出。
-- 复用 mock_draft / mock_proposition 两张临时表（draft_id 即快照 ID），
-- B 的正式 Element 表就绪后换 PropositionSource 实现即可。
-- ─────────────────────────────────────────────────────────────────────
INSERT INTO mock_draft (id, task_document_id, annotator_id, guide_version_id, document_text_version_id, annotation_scope_id, status, record_version, created_at, updated_at, deleted) VALUES
('av-1', 'td-1', 'u-ann',   'v-g-12', 'dtv-1', 'as-1', 'SUBMITTED', 0, TIMESTAMP '2026-08-16 09:00:00', TIMESTAMP '2026-08-16 09:00:00', 0),
('av-2', 'td-1', 'u-ann2',  'v-g-12', 'dtv-1', 'as-1', 'SUBMITTED', 0, TIMESTAMP '2026-08-16 09:00:00', TIMESTAMP '2026-08-16 09:00:00', 0),
('us-1', 'td-1', NULL,      'v-g-12', 'dtv-1', 'as-1', 'UNIFIED',   0, TIMESTAMP '2026-08-16 10:00:00', TIMESTAMP '2026-08-16 10:00:00', 0);

-- 三套命题同文同切（display_no 一致），仅记录 ID 不同：这正是需要「命题映射」的原因。
INSERT INTO mock_proposition (id, draft_id, document_text_version_id, annotation_scope_id, start_offset, end_offset, text, label_definition_id, label_code, is_proposition, display_no, record_version, created_at, updated_at, deleted) VALUES
('v1-p1', 'av-1', 'dtv-1', 'as-1', 22,  37,  '依法成立的合同，自成立时生效。',                        'l12-gml', 'GM-L', TRUE, 'P1', 0, TIMESTAMP '2026-08-16 09:00:00', TIMESTAMP '2026-08-16 09:00:00', 0),
('v1-p2', 'av-1', 'dtv-1', 'as-1', 38,  68,  '原告与被告于2025年3月签订劳务合同，约定报酬为6000元。',  'l12-sf',  'SF',   TRUE, 'P2', 0, TIMESTAMP '2026-08-16 09:00:00', TIMESTAMP '2026-08-16 09:00:00', 0),
('v1-p3', 'av-1', 'dtv-1', 'as-1', 69,  84,  '原告已按约定完成全部劳务工作。',                        'l12-sf',  'SF',   TRUE, 'P3', 0, TIMESTAMP '2026-08-16 09:00:00', TIMESTAMP '2026-08-16 09:00:00', 0),
('v1-p4', 'av-1', 'dtv-1', 'as-1', 85,  96,  '被告仅支付5400元。',                                   'l12-sf',  'SF',   TRUE, 'P4', 0, TIMESTAMP '2026-08-16 09:00:00', TIMESTAMP '2026-08-16 09:00:00', 0),
('v1-p5', 'av-1', 'dtv-1', 'as-1', 97,  112, '被告应当支付剩余600元报酬。',                           'l12-sm',  'SM',   TRUE, 'P5', 0, TIMESTAMP '2026-08-16 09:00:00', TIMESTAMP '2026-08-16 09:00:00', 0),
('v1-p6', 'av-1', 'dtv-1', 'as-1', 113, 132, '当事人对自己提出的主张应当提供证据。',                  'l12-gf',  'GF',   TRUE, 'P6', 0, TIMESTAMP '2026-08-16 09:00:00', TIMESTAMP '2026-08-16 09:00:00', 0),
('v1-p7', 'av-1', 'dtv-1', 'as-1', 133, 148, '原告主张另有劳务报酬600元。',                           'l12-sf',  'SF',   TRUE, 'P7', 0, TIMESTAMP '2026-08-16 09:00:00', TIMESTAMP '2026-08-16 09:00:00', 0),
('v1-p8', 'av-1', 'dtv-1', 'as-1', 149, 166, '但其提供的证据不足以证明其主张。',                      'l12-sf',  'SF',   TRUE, 'P8', 0, TIMESTAMP '2026-08-16 09:00:00', TIMESTAMP '2026-08-16 09:00:00', 0),
('v2-p1', 'av-2', 'dtv-1', 'as-1', 22,  37,  '依法成立的合同，自成立时生效。',                        'l12-gml', 'GM-L', TRUE, 'P1', 0, TIMESTAMP '2026-08-16 09:00:00', TIMESTAMP '2026-08-16 09:00:00', 0),
('v2-p2', 'av-2', 'dtv-1', 'as-1', 38,  68,  '原告与被告于2025年3月签订劳务合同，约定报酬为6000元。',  'l12-sf',  'SF',   TRUE, 'P2', 0, TIMESTAMP '2026-08-16 09:00:00', TIMESTAMP '2026-08-16 09:00:00', 0),
('v2-p3', 'av-2', 'dtv-1', 'as-1', 69,  84,  '原告已按约定完成全部劳务工作。',                        'l12-sf',  'SF',   TRUE, 'P3', 0, TIMESTAMP '2026-08-16 09:00:00', TIMESTAMP '2026-08-16 09:00:00', 0),
('v2-p4', 'av-2', 'dtv-1', 'as-1', 85,  96,  '被告仅支付5400元。',                                   'l12-sf',  'SF',   TRUE, 'P4', 0, TIMESTAMP '2026-08-16 09:00:00', TIMESTAMP '2026-08-16 09:00:00', 0),
('v2-p5', 'av-2', 'dtv-1', 'as-1', 97,  112, '被告应当支付剩余600元报酬。',                           'l12-sm',  'SM',   TRUE, 'P5', 0, TIMESTAMP '2026-08-16 09:00:00', TIMESTAMP '2026-08-16 09:00:00', 0),
('v2-p6', 'av-2', 'dtv-1', 'as-1', 113, 132, '当事人对自己提出的主张应当提供证据。',                  'l12-gf',  'GF',   TRUE, 'P6', 0, TIMESTAMP '2026-08-16 09:00:00', TIMESTAMP '2026-08-16 09:00:00', 0),
('v2-p7', 'av-2', 'dtv-1', 'as-1', 133, 148, '原告主张另有劳务报酬600元。',                           'l12-sf',  'SF',   TRUE, 'P7', 0, TIMESTAMP '2026-08-16 09:00:00', TIMESTAMP '2026-08-16 09:00:00', 0),
('v2-p8', 'av-2', 'dtv-1', 'as-1', 149, 166, '但其提供的证据不足以证明其主张。',                      'l12-sf',  'SF',   TRUE, 'P8', 0, TIMESTAMP '2026-08-16 09:00:00', TIMESTAMP '2026-08-16 09:00:00', 0),
('us-p1', 'us-1', 'dtv-1', 'as-1', 22,  37,  '依法成立的合同，自成立时生效。',                        'l12-gml', 'GM-L', TRUE, 'P1', 0, TIMESTAMP '2026-08-16 10:00:00', TIMESTAMP '2026-08-16 10:00:00', 0),
('us-p2', 'us-1', 'dtv-1', 'as-1', 38,  68,  '原告与被告于2025年3月签订劳务合同，约定报酬为6000元。',  'l12-sf',  'SF',   TRUE, 'P2', 0, TIMESTAMP '2026-08-16 10:00:00', TIMESTAMP '2026-08-16 10:00:00', 0),
('us-p3', 'us-1', 'dtv-1', 'as-1', 69,  84,  '原告已按约定完成全部劳务工作。',                        'l12-sf',  'SF',   TRUE, 'P3', 0, TIMESTAMP '2026-08-16 10:00:00', TIMESTAMP '2026-08-16 10:00:00', 0),
('us-p4', 'us-1', 'dtv-1', 'as-1', 85,  96,  '被告仅支付5400元。',                                   'l12-sf',  'SF',   TRUE, 'P4', 0, TIMESTAMP '2026-08-16 10:00:00', TIMESTAMP '2026-08-16 10:00:00', 0),
('us-p5', 'us-1', 'dtv-1', 'as-1', 97,  112, '被告应当支付剩余600元报酬。',                           'l12-sm',  'SM',   TRUE, 'P5', 0, TIMESTAMP '2026-08-16 10:00:00', TIMESTAMP '2026-08-16 10:00:00', 0),
('us-p6', 'us-1', 'dtv-1', 'as-1', 113, 132, '当事人对自己提出的主张应当提供证据。',                  'l12-gf',  'GF',   TRUE, 'P6', 0, TIMESTAMP '2026-08-16 10:00:00', TIMESTAMP '2026-08-16 10:00:00', 0),
('us-p7', 'us-1', 'dtv-1', 'as-1', 133, 148, '原告主张另有劳务报酬600元。',                           'l12-sf',  'SF',   TRUE, 'P7', 0, TIMESTAMP '2026-08-16 10:00:00', TIMESTAMP '2026-08-16 10:00:00', 0),
('us-p8', 'us-1', 'dtv-1', 'as-1', 149, 166, '但其提供的证据不足以证明其主张。',                      'l12-sf',  'SF',   TRUE, 'P8', 0, TIMESTAMP '2026-08-16 10:00:00', TIMESTAMP '2026-08-16 10:00:00', 0);

-- 两份版本的关系：**故意在五个维度上各不相同**，用于演示差异比较算法。
--   V1：S(J(P2,P3),P5) 嵌套支持、S(P2,P3)、S(P1,P5)、J(P2,P3)、J(P6,P8)、M(P7,P6)
--   V2：S(P2,P5)、S(P3,P2)（方向相反）、S(P3,P5)、S(P6,P8)（类型不同）、A(P8,P7)、
--        M(P7,P6)（一致）、J(P2,P3,P4)（成员不同）
INSERT INTO relation (id, owner_type, owner_id, relation_definition_id, relation_code, directed, status, source_relation_ids, record_version, created_at, updated_at, deleted) VALUES
('v1-j1', 'ANNOTATION_VERSION', 'av-1', 'r12-j', 'J', FALSE, 'ACTIVE', '[]', 0, TIMESTAMP '2026-08-16 09:10:00', TIMESTAMP '2026-08-16 09:10:00', 0),
('v1-s1', 'ANNOTATION_VERSION', 'av-1', 'r12-s', 'S', TRUE,  'ACTIVE', '[]', 0, TIMESTAMP '2026-08-16 09:11:00', TIMESTAMP '2026-08-16 09:11:00', 0),
('v1-s2', 'ANNOTATION_VERSION', 'av-1', 'r12-s', 'S', TRUE,  'ACTIVE', '[]', 0, TIMESTAMP '2026-08-16 09:12:00', TIMESTAMP '2026-08-16 09:12:00', 0),
('v1-s3', 'ANNOTATION_VERSION', 'av-1', 'r12-s', 'S', TRUE,  'ACTIVE', '[]', 0, TIMESTAMP '2026-08-16 09:13:00', TIMESTAMP '2026-08-16 09:13:00', 0),
('v1-j2', 'ANNOTATION_VERSION', 'av-1', 'r12-j', 'J', FALSE, 'ACTIVE', '[]', 0, TIMESTAMP '2026-08-16 09:14:00', TIMESTAMP '2026-08-16 09:14:00', 0),
('v1-m1', 'ANNOTATION_VERSION', 'av-1', 'r12-m', 'M', TRUE,  'ACTIVE', '[]', 0, TIMESTAMP '2026-08-16 09:15:00', TIMESTAMP '2026-08-16 09:15:00', 0),
('v2-s1', 'ANNOTATION_VERSION', 'av-2', 'r12-s', 'S', TRUE,  'ACTIVE', '[]', 0, TIMESTAMP '2026-08-16 09:20:00', TIMESTAMP '2026-08-16 09:20:00', 0),
('v2-s2', 'ANNOTATION_VERSION', 'av-2', 'r12-s', 'S', TRUE,  'ACTIVE', '[]', 0, TIMESTAMP '2026-08-16 09:21:00', TIMESTAMP '2026-08-16 09:21:00', 0),
('v2-s3', 'ANNOTATION_VERSION', 'av-2', 'r12-s', 'S', TRUE,  'ACTIVE', '[]', 0, TIMESTAMP '2026-08-16 09:22:00', TIMESTAMP '2026-08-16 09:22:00', 0),
('v2-s4', 'ANNOTATION_VERSION', 'av-2', 'r12-s', 'S', TRUE,  'ACTIVE', '[]', 0, TIMESTAMP '2026-08-16 09:23:00', TIMESTAMP '2026-08-16 09:23:00', 0),
('v2-a1', 'ANNOTATION_VERSION', 'av-2', 'r12-a', 'A', TRUE,  'ACTIVE', '[]', 0, TIMESTAMP '2026-08-16 09:24:00', TIMESTAMP '2026-08-16 09:24:00', 0),
('v2-m1', 'ANNOTATION_VERSION', 'av-2', 'r12-m', 'M', TRUE,  'ACTIVE', '[]', 0, TIMESTAMP '2026-08-16 09:25:00', TIMESTAMP '2026-08-16 09:25:00', 0),
('v2-j1', 'ANNOTATION_VERSION', 'av-2', 'r12-j', 'J', FALSE, 'ACTIVE', '[]', 0, TIMESTAMP '2026-08-16 09:26:00', TIMESTAMP '2026-08-16 09:26:00', 0);

INSERT INTO relation_participant (id, relation_id, ordinal, role, target_kind, target_id, record_version, created_at, updated_at, deleted) VALUES
('v1-rp-1-1', 'v1-j1', 1, 'MEMBER', 'ELEMENT',  'v1-p2', 0, TIMESTAMP '2026-08-16 09:10:00', TIMESTAMP '2026-08-16 09:10:00', 0),
('v1-rp-1-2', 'v1-j1', 2, 'MEMBER', 'ELEMENT',  'v1-p3', 0, TIMESTAMP '2026-08-16 09:10:00', TIMESTAMP '2026-08-16 09:10:00', 0),
('v1-rp-2-1', 'v1-s1', 1, 'SOURCE', 'RELATION', 'v1-j1', 0, TIMESTAMP '2026-08-16 09:11:00', TIMESTAMP '2026-08-16 09:11:00', 0),
('v1-rp-2-2', 'v1-s1', 2, 'TARGET', 'ELEMENT',  'v1-p5', 0, TIMESTAMP '2026-08-16 09:11:00', TIMESTAMP '2026-08-16 09:11:00', 0),
('v1-rp-3-1', 'v1-s2', 1, 'SOURCE', 'ELEMENT',  'v1-p2', 0, TIMESTAMP '2026-08-16 09:12:00', TIMESTAMP '2026-08-16 09:12:00', 0),
('v1-rp-3-2', 'v1-s2', 2, 'TARGET', 'ELEMENT',  'v1-p3', 0, TIMESTAMP '2026-08-16 09:12:00', TIMESTAMP '2026-08-16 09:12:00', 0),
('v1-rp-4-1', 'v1-s3', 1, 'SOURCE', 'ELEMENT',  'v1-p1', 0, TIMESTAMP '2026-08-16 09:13:00', TIMESTAMP '2026-08-16 09:13:00', 0),
('v1-rp-4-2', 'v1-s3', 2, 'TARGET', 'ELEMENT',  'v1-p5', 0, TIMESTAMP '2026-08-16 09:13:00', TIMESTAMP '2026-08-16 09:13:00', 0),
('v1-rp-5-1', 'v1-j2', 1, 'MEMBER', 'ELEMENT',  'v1-p6', 0, TIMESTAMP '2026-08-16 09:14:00', TIMESTAMP '2026-08-16 09:14:00', 0),
('v1-rp-5-2', 'v1-j2', 2, 'MEMBER', 'ELEMENT',  'v1-p8', 0, TIMESTAMP '2026-08-16 09:14:00', TIMESTAMP '2026-08-16 09:14:00', 0),
('v1-rp-6-1', 'v1-m1', 1, 'SOURCE', 'ELEMENT',  'v1-p7', 0, TIMESTAMP '2026-08-16 09:15:00', TIMESTAMP '2026-08-16 09:15:00', 0),
('v1-rp-6-2', 'v1-m1', 2, 'TARGET', 'ELEMENT',  'v1-p6', 0, TIMESTAMP '2026-08-16 09:15:00', TIMESTAMP '2026-08-16 09:15:00', 0),
('v2-rp-1-1', 'v2-s1', 1, 'SOURCE', 'ELEMENT',  'v2-p2', 0, TIMESTAMP '2026-08-16 09:20:00', TIMESTAMP '2026-08-16 09:20:00', 0),
('v2-rp-1-2', 'v2-s1', 2, 'TARGET', 'ELEMENT',  'v2-p5', 0, TIMESTAMP '2026-08-16 09:20:00', TIMESTAMP '2026-08-16 09:20:00', 0),
('v2-rp-2-1', 'v2-s2', 1, 'SOURCE', 'ELEMENT',  'v2-p3', 0, TIMESTAMP '2026-08-16 09:21:00', TIMESTAMP '2026-08-16 09:21:00', 0),
('v2-rp-2-2', 'v2-s2', 2, 'TARGET', 'ELEMENT',  'v2-p2', 0, TIMESTAMP '2026-08-16 09:21:00', TIMESTAMP '2026-08-16 09:21:00', 0),
('v2-rp-3-1', 'v2-s3', 1, 'SOURCE', 'ELEMENT',  'v2-p3', 0, TIMESTAMP '2026-08-16 09:22:00', TIMESTAMP '2026-08-16 09:22:00', 0),
('v2-rp-3-2', 'v2-s3', 2, 'TARGET', 'ELEMENT',  'v2-p5', 0, TIMESTAMP '2026-08-16 09:22:00', TIMESTAMP '2026-08-16 09:22:00', 0),
('v2-rp-4-1', 'v2-s4', 1, 'SOURCE', 'ELEMENT',  'v2-p6', 0, TIMESTAMP '2026-08-16 09:23:00', TIMESTAMP '2026-08-16 09:23:00', 0),
('v2-rp-4-2', 'v2-s4', 2, 'TARGET', 'ELEMENT',  'v2-p8', 0, TIMESTAMP '2026-08-16 09:23:00', TIMESTAMP '2026-08-16 09:23:00', 0),
('v2-rp-5-1', 'v2-a1', 1, 'SOURCE', 'ELEMENT',  'v2-p8', 0, TIMESTAMP '2026-08-16 09:24:00', TIMESTAMP '2026-08-16 09:24:00', 0),
('v2-rp-5-2', 'v2-a1', 2, 'TARGET', 'ELEMENT',  'v2-p7', 0, TIMESTAMP '2026-08-16 09:24:00', TIMESTAMP '2026-08-16 09:24:00', 0),
('v2-rp-6-1', 'v2-m1', 1, 'SOURCE', 'ELEMENT',  'v2-p7', 0, TIMESTAMP '2026-08-16 09:25:00', TIMESTAMP '2026-08-16 09:25:00', 0),
('v2-rp-6-2', 'v2-m1', 2, 'TARGET', 'ELEMENT',  'v2-p6', 0, TIMESTAMP '2026-08-16 09:25:00', TIMESTAMP '2026-08-16 09:25:00', 0),
('v2-rp-7-1', 'v2-j1', 1, 'MEMBER', 'ELEMENT',  'v2-p2', 0, TIMESTAMP '2026-08-16 09:26:00', TIMESTAMP '2026-08-16 09:26:00', 0),
('v2-rp-7-2', 'v2-j1', 2, 'MEMBER', 'ELEMENT',  'v2-p3', 0, TIMESTAMP '2026-08-16 09:26:00', TIMESTAMP '2026-08-16 09:26:00', 0),
('v2-rp-7-3', 'v2-j1', 3, 'MEMBER', 'ELEMENT',  'v2-p4', 0, TIMESTAMP '2026-08-16 09:26:00', TIMESTAMP '2026-08-16 09:26:00', 0);

-- 裁定会话。C 只读 status/current_phase/proposition_completed_at 做 J06 的阶段门禁；
-- 写侧（J05 完成命题阶段、J07 裁定、J08 完成关系阶段、状态机）归 D。
-- ses-1 命题阶段已完成（unified_snapshot_id 指向统一命题集合 us-1），可开关系裁定；
-- ses-2 仍在命题阶段：J06 必须返回 PROPOSITIONS_NOT_COMPLETE，用于演示「先裁定命题、后裁定关系」的硬顺序。
INSERT INTO adjudication_session (id, task_document_id, arbiter_assignment_id, source_version_ids, unified_snapshot_id, status, current_phase, proposition_completed_at, relation_completed_at, record_version, created_at, updated_at, deleted) VALUES
('ses-1', 'td-1', 'ta-1', '["av-1","av-2"]', 'us-1', 'RELATION_REVIEW', 'RELATION_REVIEW', TIMESTAMP '2026-08-16 10:00:00', NULL, 0, TIMESTAMP '2026-08-16 09:30:00', TIMESTAMP '2026-08-16 10:00:00', 0),
('ses-2', 'td-1', 'ta-2', '["av-1","av-2"]', NULL, 'PROPOSITION_REVIEW', 'PROPOSITION_REVIEW', NULL, NULL, 0, TIMESTAMP '2026-08-16 09:30:00', TIMESTAMP '2026-08-16 09:30:00', 0);

-- ─────────────────────────────────────────────────────────────────────
-- 悬空端点场景（成员 C）：ses-3
--   验证「端点映射不到统一命题集合」这条防线。av-4 里的 P9 在统一命题集合 us-1 中
--   没有任何同编号或同文的命题，因此该端点映射不上——差异比较必须把它判为存在性差异
--   并在理由里提示人工核查，绝不能拿它去比"参与命题重合度"（未映射端点不进分母会让
--   重合度虚高，实测会算出 0.5 而被误判成"参与命题分歧"）。
--   ses-1 保持 8 条不变，避免已冻结的演示数据与 fixture 失效。
-- ─────────────────────────────────────────────────────────────────────
INSERT INTO mock_draft (id, task_document_id, annotator_id, guide_version_id, document_text_version_id, annotation_scope_id, status, record_version, created_at, updated_at, deleted) VALUES
('av-3', 'td-1', 'u-ann',  'v-g-12', 'dtv-1', 'as-1', 'SUBMITTED', 0, TIMESTAMP '2026-08-16 11:00:00', TIMESTAMP '2026-08-16 11:00:00', 0),
('av-4', 'td-1', 'u-ann2', 'v-g-12', 'dtv-1', 'as-1', 'SUBMITTED', 0, TIMESTAMP '2026-08-16 11:00:00', TIMESTAMP '2026-08-16 11:00:00', 0);

INSERT INTO mock_proposition (id, draft_id, document_text_version_id, annotation_scope_id, start_offset, end_offset, text, label_definition_id, label_code, is_proposition, display_no, record_version, created_at, updated_at, deleted) VALUES
('v3-p1', 'av-3', 'dtv-1', 'as-1', 22,  37,  '依法成立的合同，自成立时生效。',                        'l12-gml', 'GM-L', TRUE, 'P1', 0, TIMESTAMP '2026-08-16 11:00:00', TIMESTAMP '2026-08-16 11:00:00', 0),
('v3-p2', 'av-3', 'dtv-1', 'as-1', 38,  68,  '原告与被告于2025年3月签订劳务合同，约定报酬为6000元。',  'l12-sf',  'SF',   TRUE, 'P2', 0, TIMESTAMP '2026-08-16 11:00:00', TIMESTAMP '2026-08-16 11:00:00', 0),
('v4-p1', 'av-4', 'dtv-1', 'as-1', 22,  37,  '依法成立的合同，自成立时生效。',                        'l12-gml', 'GM-L', TRUE, 'P1', 0, TIMESTAMP '2026-08-16 11:00:00', TIMESTAMP '2026-08-16 11:00:00', 0),
('v4-p2', 'av-4', 'dtv-1', 'as-1', 38,  68,  '原告与被告于2025年3月签订劳务合同，约定报酬为6000元。',  'l12-sf',  'SF',   TRUE, 'P2', 0, TIMESTAMP '2026-08-16 11:00:00', TIMESTAMP '2026-08-16 11:00:00', 0),
-- P9：编号与原文在统一命题集合里都不存在 → 映射不上，是"悬空端点"的来源
('v4-p9', 'av-4', 'dtv-1', 'as-1', 188, 210, '被告主张原告尚有一笔差旅费未结清。',                      'l12-sf',  'SF',   TRUE, 'P9', 0, TIMESTAMP '2026-08-16 11:00:00', TIMESTAMP '2026-08-16 11:00:00', 0);

INSERT INTO relation (id, owner_type, owner_id, relation_definition_id, relation_code, directed, status, source_relation_ids, record_version, created_at, updated_at, deleted) VALUES
('v3-s1', 'ANNOTATION_VERSION', 'av-3', 'r12-s', 'S', TRUE, 'ACTIVE', '[]', 0, TIMESTAMP '2026-08-16 11:10:00', TIMESTAMP '2026-08-16 11:10:00', 0),
('v4-s1', 'ANNOTATION_VERSION', 'av-4', 'r12-s', 'S', TRUE, 'ACTIVE', '[]', 0, TIMESTAMP '2026-08-16 11:11:00', TIMESTAMP '2026-08-16 11:10:00', 0),
('v4-s2', 'ANNOTATION_VERSION', 'av-4', 'r12-s', 'S', TRUE, 'ACTIVE', '[]', 0, TIMESTAMP '2026-08-16 11:12:00', TIMESTAMP '2026-08-16 11:10:00', 0);

INSERT INTO relation_participant (id, relation_id, ordinal, role, target_kind, target_id, record_version, created_at, updated_at, deleted) VALUES
('v3-rp-1-1', 'v3-s1', 1, 'SOURCE', 'ELEMENT', 'v3-p1', 0, TIMESTAMP '2026-08-16 11:10:00', TIMESTAMP '2026-08-16 11:10:00', 0),
('v3-rp-1-2', 'v3-s1', 2, 'TARGET', 'ELEMENT', 'v3-p2', 0, TIMESTAMP '2026-08-16 11:10:00', TIMESTAMP '2026-08-16 11:10:00', 0),
('v4-rp-1-1', 'v4-s1', 1, 'SOURCE', 'ELEMENT', 'v4-p1', 0, TIMESTAMP '2026-08-16 11:11:00', TIMESTAMP '2026-08-16 11:11:00', 0),
('v4-rp-1-2', 'v4-s1', 2, 'TARGET', 'ELEMENT', 'v4-p2', 0, TIMESTAMP '2026-08-16 11:11:00', TIMESTAMP '2026-08-16 11:11:00', 0),
('v4-rp-2-1', 'v4-s2', 1, 'SOURCE', 'ELEMENT', 'v4-p9', 0, TIMESTAMP '2026-08-16 11:12:00', TIMESTAMP '2026-08-16 11:12:00', 0),
('v4-rp-2-2', 'v4-s2', 2, 'TARGET', 'ELEMENT', 'v4-p1', 0, TIMESTAMP '2026-08-16 11:12:00', TIMESTAMP '2026-08-16 11:12:00', 0);

-- ses-3：命题阶段已完成，源版本 av-3/av-4，用于验证悬空端点
INSERT INTO adjudication_session (id, task_document_id, arbiter_assignment_id, source_version_ids, unified_snapshot_id, status, current_phase, proposition_completed_at, relation_completed_at, record_version, created_at, updated_at, deleted) VALUES
('ses-3', 'td-1', 'ta-3', '["av-3","av-4"]', 'us-1', 'RELATION_REVIEW', 'RELATION_REVIEW', TIMESTAMP '2026-08-16 11:00:00', NULL, 0, TIMESTAMP '2026-08-16 10:30:00', TIMESTAMP '2026-08-16 11:00:00', 0);
