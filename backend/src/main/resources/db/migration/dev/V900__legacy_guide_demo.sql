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
