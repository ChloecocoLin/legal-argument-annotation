# 入库与审计试行交接

日期：2026-10-15，分支 feature/member-d。用户明确选择“按现有提案在 D 分支试行”，授权以 06 入库/审计提案及文件模型提案继续开发。本批交付第 8 步的入库与审计试行增量；用户授权不等于四人共享模型评审完成。

## 试行范围

- FV06：确认唯一入库，返回原提案约定的 status/committedAt，保持六字段信封。
- L01：仅查询本批实际产生的 FV06 永久审计，按当前身份过滤后分页、计 total；支持对象类型/ID、操作者、UTC 时间范围。
- L02/L03：本批支持 FinalAnnotationVersion 的去重来源 ID/决定 ID 和 recordVersion/at 历史；其他对象类型明确拒绝，未声称完成整个跨模块追溯。
- 页面：最终审查增加试行入库确认、已入库只读说明、入库时间和审计入口；新增 /audit 查询、分页及详情。

试行服务/控制器仅在 `(local | test) & !prod` 注册。V6 迁移位于 db/migration/trial，只有 local/test 的 Flyway locations 加载；正常 prod 配置仍为既有 vendor/common，未增加试行表。共享 openapi-ready.yaml、SDK 和原始公共模型保持不变；本批 JSON Schema 为 contracts/review/member-d-audit-trial.schema.json，明确待评审。

未将原型的固定成功状态变为真实批准。实时 A/B/C 正式证据仍是入库前置：本地预览依赖永远不能批准或入库。入库正向通过测试中的服务端证据替身验收，不提供任何客户端或环境变量绕过。

## 原子事务与并发

锁顺序为案例计数行 → TaskDocument → Task → Session，所有 D 现有案例写入共用案例锁。入库重读身份范围、最终版本、会话和任务状态；仅最新 APPROVED、批准人/时间齐全且未入库的最终版可执行。再次核对完整来源、当前决定摘要、专家要求、文本/标签/结构及绑定本内容/决定/指南的正式验证证据。

同一事务保存：FinalAnnotationVersion.IN_DATABASE/committedAt/版本递增、案例 COMPLETED、会话 COMPLETED、任务完成判定、永久 TrialAuditEvent、唯一案例入库索引、验证证据、私有最终轨迹及 24 小时幂等结果。提交人存于审计，未擅自在核心 FinalAnnotationVersion 增加 committedBy 字段。任一步失败全部回滚，包括已经写入的审计。

trial_final_commitments 以 task_document_id 为主键，并对 final_version_id/audit_event_id 唯一；已有任何 IN_DATABASE 最终版也会拒绝重复入库。相同键、相同内容在重新检查当前授权后返回首次 data；同键异内容冲突。不同键重复或幂等记录到期后再次提交均不能产生第二条入库。审计/入库记录不依赖 24 小时幂等保留期。

Task 仅在所有关联 TaskDocument 均有 IN_DATABASE 最终版时 COMPLETED；其他案例继续工作。事务使用 READ_COMMITTED，避免不同案例同时等待同一 Task 行锁后仍用早先可重复读快照做完成判定。跨案例并发测试验证两个案例均入库后任务正确完成；单案入库不会提前锁死另一个案例。

源版本内容/ID 永久保留，流程状态已 ADJUDICATED，不回写其要素、关系或参与项。现有 D 的提交、撤回、裁定与最终版本写入口沿用案例/任务门禁；入库后拒绝重建和修改。A/B/C 的生产写入口必须在共享事务适配时共同遵守案例锁与只读状态，当前未完成其生产联合验收。

## 审计与权限

审计只追加，没有编辑/删除公共接口。记录服务端操作者、有效角色和权限、首次请求 ID、UTC 秒精度时间、对象及任务/案例/会话、四类资源前后状态/recordVersion、原因、固定来源版本/决定 IDs、事务 ID 和 canonical 内容摘要。前后值使用白名单投影，不保存标注原文、令牌、口令、完整快照或任意请求 JSON。入库验证证据独立存储，不通过管理员审计列表公开。

ApiResponse 新增服务端专用关联 ID 工厂，让首次 FV06 响应 requestId 与同事务审计一致；其他接口仍使用原有自动生成方式。重试是新 HTTP 请求，响应信封 ID 可不同，但 data 沿用首次结果且不新增事件；事件保留首次请求 ID。

ADMIN+AUDIT_VIEW 可查全局安全审计投影；TASK_CREATOR+TASK_VIEW 限本人负责的任务；ARBITER+FINAL_REVIEW 限当前有效案例分配。权限每次重新检查，先授权再分页/计数，不泄露跨任务总数。标注员、专家无该审计列表权限。管理员/创建者拥有审计读取权不代表拥有原始标注或完整来源读取权；L02/L03 复用最终版内容授权，仅负责案例的有效裁定者可读。

## 页面核查

| 页面 | 本批变动 |
| --- | --- |
| /final-review/:id | APPROVED 时提供“确认入库（试行）”，须实时质量/正式证据均通过，确认框主动勾选，保留幂等键重试；IN_DATABASE 显示永久只读与时间，不显示可编辑流程；新增入库审计入口 |
| /audit | 按原型现有卡片、表格和间距风格新增审计查询；按对象、操作者、UTC 起止时间筛选，分页、状态前后值和关联摘要详情；所有角色均受服务端权限限制 |
| /admin-home、/export | 未改变统计或导出资格；既有统计读取已入库状态，导出预览仍严格筛 IN_DATABASE；文件生成与下载待下一增量 |

当前本地 F2 `cedc45a8-623c-4d2e-b9c1-a2089b743105` 仍为 IN_REVIEW，正式验证依赖未接通，不能批准/入库；审计列表 0 条与导出范围为空是正确数据。测试正向结果未导入用户本地库，没有制作虚假入库或审计截图。

浏览器核查了管理员审计筛选/空结果和裁定者最终页的审计入口、批准禁用。审计页面实际 760px 视口下筛选单栏、无页面横向溢出，已恢复默认视口。截图见 screenshots/audit-trial-desktop.jpg、audit-trial-narrow.jpg 和 final-review-commit-trial-gate.jpg。

## 验证

既有 53 项回归通过，新增 15 项试行测试全部通过，合计 68 项：原子状态与固定来源、同键重试/异内容、过期幂等不重复、审计前置失败回滚、审计写入后后续失败回滚、安全重试、同案并发唯一、跨案例并发完成任务、单案例不提前完成任务、未批准/旧版本/历史快照拒绝、本地证据禁止入库、专家要求/错误内容阻断、即时撤销权限、角色/跨任务隔离、来源/历史只读分页、入库后现有写入口拒绝。

持久化专项使用实际 H2 文件数据库和原迁移+V6，保存已入库最终快照、审计与唯一记录，执行 SHUTDOWN 后重新连接，确认内容和关联完整。该测试的批准证据是服务端测试替身；不等同于生产 MySQL 或真实模型验收。

前端类型检查与生产构建通过，保留既有公共包体积提示。只读 HTTP 脚本验证管理员/创建者/裁定者审计范围、标注员/专家拒绝、最终来源与历史、管理员不能借审计读取来源，以及正向测试响应与试行审计 Schema。U08/FV02 冻结响应回归，冻结文件 SHA-256 保持 `813539491A53C0151C44BD426985F74A1DF4D5AD35A7DF1955D20C9CAE13B390`。

```powershell
python scripts/verify-member-d-commit-trial.py --final-id cedc45a8-623c-4d2e-b9c1-a2089b743105
```

脚本只发 GET，不批准或入库。正向证据文件位于 backend/target/member-d-commit-trial-response.json 和 member-d-audit-trial-event.json，由测试生成，不提交到 Git，也不进入运行中的联调数据库。

## 下一增量

用户对现有文件模型提案的 D 分支试行授权继续有效。接下来按第 9 步推进 JSON/XLSX 不可变文件、EX 创建/查询、POST 下载凭证和只读 F02，保留待团队评审标记；仍仅允许 IN_DATABASE 来源。PNG/SVG 等 C 正式全图能力接入后开放。T14 新任务重标与 A 的生产 Task/TaskDocument 锁定及来源引用继续作为联合验收项，不能在 D 分支替代 A 的实现。

团队评审后再同步 Markdown、两份 OpenAPI、Mock 与 SDK，并将试行表/模型迁移至共同认可的生产结构；不能直接把 trial V6 拷入生产 common 目录造成历史迁移差异。
