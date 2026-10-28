-- C relation metadata for existing local demo IDs only; user-created definitions are untouched.

UPDATE relation_definition SET definition = '一个命题或命题组为另一命题成立提供直接理由。',
    direction_rule = '从支持者指向被支持命题。',
    min_participants = '2',
    max_participants = '2',
    sort_order = '1',
    expression_template = 'S(x, y)',
    participant_rule = '二元有序；x 可为命题或 J/M 等关系整体，y 为目标命题。只标直接支持，不自动补传递边。',
    diagram_rule = '矩形命题节点之间经实心圆关系节点形成有向边，箭头指向被支持命题。'
WHERE id = 'r12-s' AND sort_order IS NULL;

UPDATE relation_definition SET definition = '一个命题对另一命题或支持结构表达否定。',
    direction_rule = '从反对者指向被反对的命题或关系节点。',
    min_participants = '2',
    max_participants = '2',
    sort_order = '2',
    expression_template = 'A(x, y)',
    participant_rule = '二元有序；典型为命题对命题，允许 A(po,S(pi,pj))。反对须真实表达否定，转折词本身不足以成立。',
    diagram_rule = '矩形命题节点之间经空心圆关系节点形成有向边，箭头指向被反对者。'
WHERE id = 'r12-a' AND sort_order IS NULL;

UPDATE relation_definition SET definition = '多个命题或已成立的关系共同构成一个理由，缺一不可。',
    direction_rule = '无方向；成员按展示序号从小到大排列。',
    min_participants = '2',
    max_participants = NULL,
    sort_order = '3',
    expression_template = 'J(x1, x2, ..., xn)',
    participant_rule = '至少两个命题或已成立的关系整体；每个成员均不能单独承担共同支持。区别于多个独立 S。',
    diagram_rule = '各成员经带“+”圆节点作无向连线。'
WHERE id = 'r12-j' AND sort_order IS NULL;

UPDATE relation_definition SET definition = '个别判断与一般判断之间的对应关系。',
    direction_rule = '从个别判断或其组合指向一般判断。',
    min_participants = '2',
    max_participants = '2',
    sort_order = '4',
    expression_template = 'M(individual, general)',
    participant_rule = '二元有序；左侧可为个别命题或 J(多个个别判断)，右侧为一般判断；必要时整体嵌入 S 或 J。',
    diagram_rule = '经带“+”圆节点作有向边，箭头指向一般判断。'
WHERE id = 'r12-m' AND sort_order IS NULL;

UPDATE relation_definition SET definition = '两个及以上命题语义几乎相同。',
    direction_rule = '无方向；成员按展示序号排列。',
    min_participants = '2',
    max_participants = NULL,
    sort_order = '5',
    expression_template = 'I(p1, p2, ..., pn)',
    participant_rule = '至少两个命题，且仅限命题；同类型优先审查，关系确认后用最大序号命题继续关系标注。',
    diagram_rule = '同组命题以矩形框内“/”表示。'
WHERE id = 'r12-i' AND sort_order IS NULL;
