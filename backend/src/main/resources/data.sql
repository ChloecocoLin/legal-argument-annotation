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

-- v1.2 关系定义（S/A/M 有向，J/I 无向；G07/G15 归成员C，此处仅建数据）
INSERT INTO relation_definition (id, guide_version_id, code, name, definition, directed, expression_template, participant_rule, diagram_rule, record_version, created_at, updated_at, deleted) VALUES
('r12-s', 'v-g-12', 'S', '支持关系', NULL, TRUE,  NULL, NULL, NULL, 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('r12-a', 'v-g-12', 'A', '反对关系', NULL, TRUE,  NULL, NULL, NULL, 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('r12-j', 'v-g-12', 'J', '组合关系', NULL, FALSE, NULL, NULL, NULL, 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('r12-m', 'v-g-12', 'M', '匹配关系', NULL, TRUE,  NULL, NULL, NULL, 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0),
('r12-i', 'v-g-12', 'I', '同一关系', NULL, FALSE, NULL, NULL, NULL, 0, TIMESTAMP '2026-08-14 10:00:00', TIMESTAMP '2026-08-14 10:00:00', 0);

-- M3 J09/J10 联调数据。依赖对象只读，写操作仅发生在 annotation_element 与 arbiter_custom_annotation。
INSERT INTO document_text_version (id, document_id, version_number, normalized_text, content_sha256, status, record_version, created_at, updated_at) VALUES
('86d2b05b-fa18-4d45-a451-edcaa83d673f', 'bfd699c7-e225-4263-b077-c247e4d59fbc', 1,
 '本院认为，依法成立的合同，自成立时生效。当事人应当按照约定全面履行自己的义务。综上所述，依照法律规定，判决如下：驳回其他诉讼请求。',
 '0905cc3f16fd346f363963570af18ea5cd3fbe01c3e667bb28f7c1f69cca35e1', 'PUBLISHED', 0,
 TIMESTAMP '2026-10-10 08:00:00', TIMESTAMP '2026-10-10 08:00:00');

INSERT INTO annotation_scope (id, document_text_version_id, scope_type, start_offset, end_offset, selection_method, status, record_version, created_at, updated_at) VALUES
('4c3122b2-90e0-47b4-8e67-0986764897bc', '86d2b05b-fa18-4d45-a451-edcaa83d673f', 'REASONING', 5, 39, 'MANUAL', 'CONFIRMED', 0,
 TIMESTAMP '2026-10-10 08:00:00', TIMESTAMP '2026-10-10 08:00:00');

INSERT INTO annotation_version (id, display_code, document_text_version_id, annotation_scope_id, guide_version_id, status, record_version, created_at, updated_at) VALUES
('402d8716-3ea0-46ac-9078-05545fe58071', 'V1', '86d2b05b-fa18-4d45-a451-edcaa83d673f', '4c3122b2-90e0-47b4-8e67-0986764897bc', 'v-g-12', 'UNDER_ADJUDICATION', 0, TIMESTAMP '2026-10-10 09:00:00', TIMESTAMP '2026-10-10 09:00:00'),
('e4b02d3f-f041-48fa-bf91-7736a381eab9', 'V2', '86d2b05b-fa18-4d45-a451-edcaa83d673f', '4c3122b2-90e0-47b4-8e67-0986764897bc', 'v-g-12', 'UNDER_ADJUDICATION', 0, TIMESTAMP '2026-10-10 09:00:00', TIMESTAMP '2026-10-10 09:00:00');

INSERT INTO adjudication_session (id, task_document_id, status, source_version_ids, proposition_completed_at, record_version, created_at, updated_at) VALUES
('aba39933-394e-41e0-aa76-1cf8acc57ba7', '3cbb140f-0274-4871-8248-194271101a14', 'PROPOSITION_REVIEW',
 '["402d8716-3ea0-46ac-9078-05545fe58071","e4b02d3f-f041-48fa-bf91-7736a381eab9"]', NULL, 0,
 TIMESTAMP '2026-10-10 09:00:00', TIMESTAMP '2026-10-10 09:00:00');

INSERT INTO proposition_adjudication_item (id, session_id, conflict_type, candidate_element_ids, status, record_version, created_at, updated_at) VALUES
('058fa5ba-c4a8-457a-96f9-1efd15df8e20', 'aba39933-394e-41e0-aa76-1cf8acc57ba7', 'LABEL',
 '["007077eb-0059-41ed-bd82-bf5b90faf40f","a9564144-5a19-45e9-bb33-4d2758e68acf"]', 'PENDING', 0,
 TIMESTAMP '2026-10-10 09:00:00', TIMESTAMP '2026-10-10 09:00:00');

INSERT INTO annotation_element (id, owner_type, owner_id, client_key, document_text_version_id, annotation_scope_id, start_offset, end_offset, text, label_definition_id, label_code, is_proposition, display_no, source_element_ids, record_version, created_at, updated_at) VALUES
('007077eb-0059-41ed-bd82-bf5b90faf40f', 'ANNOTATION_VERSION', '402d8716-3ea0-46ac-9078-05545fe58071', NULL,
 '86d2b05b-fa18-4d45-a451-edcaa83d673f', '4c3122b2-90e0-47b4-8e67-0986764897bc', 5, 20,
 '依法成立的合同，自成立时生效。', 'l12-gml', 'GM-L', TRUE, 'P1', '[]', 0,
 TIMESTAMP '2026-10-10 09:00:00', TIMESTAMP '2026-10-10 09:00:00'),
('a9564144-5a19-45e9-bb33-4d2758e68acf', 'ANNOTATION_VERSION', 'e4b02d3f-f041-48fa-bf91-7736a381eab9', NULL,
 '86d2b05b-fa18-4d45-a451-edcaa83d673f', '4c3122b2-90e0-47b4-8e67-0986764897bc', 5, 20,
 '依法成立的合同，自成立时生效。', 'l12-gmi', 'GM-I', TRUE, 'P1', '[]', 0,
 TIMESTAMP '2026-10-10 09:00:00', TIMESTAMP '2026-10-10 09:00:00');
