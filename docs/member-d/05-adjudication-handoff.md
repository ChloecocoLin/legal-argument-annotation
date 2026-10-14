# 第 5 步裁定联调交接

本批在 feature/member-d 延续版本基础。已检查远端分支，2026-10-14 仅 origin/main 与 origin/feature/member-d，无 A/B/C 开发分支可以读取。保留明确的 local/test 适配器，生产必须提供 AnnotationDependencies、VersionIdentity 和 AdjudicationDependencies。

## 接口与页面

D 提供 V03、J01/J02/J05/J07/J08/J11。J01 固定独立来源和共同基准；会话建立与版本锁定在同一事务完成。J05 对全部命题及非命题含一致项检查决定，冻结映射并建立关系项。J07 追加决定、检查来源与统一关系引用；J08 再检查全部项目及关系图后转为 FINAL_REVIEW。J11 指定对象补正，旧会话、决定和快照留存，新提交产生新 Vn 并可创建新会话。幂等记录保留 24 小时，重试检查当前权限，If-Match 使用对应资源 recordVersion。

页面沿用原型 conflict-resolve.html 的三阶段条、1.7:1 双栏、候选并列、右栏来源/进度，以及 arbiter-annotate.html 的原文候选/编辑区域/图区域/保存区域。不同来源使用稳定 UUID 区分，支持多列溢出阅读。任务详情新增裁定入口。

访问 /adjudication/conflict?caseId=案例ID。创建后 URL 包含 sessionId，可刷新恢复；旧 RETURNED 会话按 sessionId 只读访问。本地工作区读取和演示命题决定位于 /api/local/member-d/adjudication，明确不是 B/C 的公共 J03/J06/J09/J10 或 D 的正式 J04。正式 J04 仍未发布。

自定义页读取来源与上下文；正式模式预留 B 的 J09/J10 调用及动态 G06 标签。当前本地保存禁用；关系编辑等待 C。保存快照与确认采用是独立操作。第 7 步 FV01–FV05、最终快照和审查页未在本批交付，FINAL_REVIEW 不等于已经生成、批准或入库。

## 冻结契约与评审补丁

contracts/openapi-ready.yaml 保持原始哈希，生成物不修改。J01/J02 的缩略 DTO 无法表达 null 的未完成时间和必要版本号，本地实现返回完整 AdjudicationSession，对应 contracts/review/member-d-adjudication-transport.patch.json 为独立提案。它尚未获得团队评审；涉及这些读取的联调按 proposed overlay 验证，不能宣称已通过未修改的冻结 Schema。

J07/J08/J11 保持冻结的缩略响应，完成后重新读取会话/工作区。J06 的完整裁定项及当前决定读取也列入提案，正式前端遇到缺失候选或 recordVersion 时拒绝开放写入。

## 依赖适配要求

- A：真实任务/逐案成员/文本/关口与锁定事务；替换依赖桩，并接入切割计算。
- B：comparePropositions 覆盖全部命题、非命题和一致项；M13 正式映射与 J04；J03/J09/J10/J12 的候选、自定义、专家问题；草稿补正与 D 共用事务。
- C：compareRelations 覆盖差异及一致项；validateRelations 校验所有生效关系、嵌套无环和统一端点，删除内层关系时重检外层；关系编辑、全图校验和 PNG/SVG 另接。
- C 认证：VersionIdentity 使用真实令牌，AdjudicationDependencies.expertAccess 检查专家指派及有效任务成员。local/test 身份和适配器在 prod 激活时全部禁用。

本地映射和结构校验不是 B/C 完整语义算法；待确认模型见 04-segmentation-and-mapping-proposal.md。退回资格索引和永久阶段历史是私有实现，不代替公共 CorrectionRequest 或 AuditEvent。V4 私有历史表永久保留 J11 的理由与指定对象，独立于 24 小时幂等记录；测试删除幂等记录后仍能查询原退回历史。

## 验收记录

- 后端 29 项通过：13 项裁定测试 + 16 项版本回归测试。裁定覆盖独立来源、跨基准、N=3、80% 不通过、一致项人工确认、专家门禁、两阶段顺序、旧版本、幂等异内容、权限撤销后重试、事务回滚、并发唯一会话、退回指定成员及补交后新会话、删除命题后关系端点拒绝。
- 前端类型检查与生产构建通过；既有公共包体积提示仍存在。
- 本地 HTTP 的 J02/V03 及完整 item/decision 实体通过 proposed overlay Schema 校验；原批次 T13/V01/V02 回归校验通过。未修改冻结输入，未宣称 J01/J02 通过原缩略 Schema。
- 浏览器实际完成创建会话 → 采用命题 → 确认一致项 → J05 → 采用关系 → J08 → FINAL_REVIEW。跨阶段按钮禁用，自定义页读取真实来源/原文/动态标签，未接入保存保持禁用。
- 使用 start.bat 的同一 server.ps1 启动原型（18081，无自动弹窗），实际打开 conflict-resolve.html 对照；同时阅读 arbiter-annotate.html 并按原型原文左侧/编辑右侧布局核查。
- 桌面 1440×900 与窄屏 760×900 核查通过；浏览器尺寸已恢复。截图见 screenshots/adjudication-final-review.jpg 与 screenshots/arbiter-context.jpg。
- 后端更新并重启后，会话 4c12faaa-6466-4511-8077-eb724cef1958 仍为 FINAL_REVIEW，保留 V3/V4 来源和三条决定；数据库迁移未清空上一批历史。

当前本地演示会话已处于最终审查阶段，其来源已锁定。演示命题映射不支持拆分合并或自定义快照，完整关系/图语义需 C 接入；J04、切割算法、专家指派与自定义保存尚未作为正式链路验收。

只读复核：

```powershell
python scripts/verify-member-d-adjudication.py --session-id 4c12faaa-6466-4511-8077-eb724cef1958
python scripts/verify-member-d-contract.py
```
