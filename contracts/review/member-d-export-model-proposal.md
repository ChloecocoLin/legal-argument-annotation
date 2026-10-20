# 导出、文件与下载模型提案

2026-10-20 进展：用户另授权切割算法及独立补正轮次试行；已实现范围、接口和未完成依赖见 [第 1–3 批交接](../../docs/member-d/10-batches-1-3-handoff.md)。下文为原提案/阶段记录，正式共享模型仍待评审。

状态：DRAFT / 待 A/B/C/D 共同评审，2026-10-15。对应详细计划第 9 步及契约 M4/M6/M7/M9。本文不替代共享模型，不开放 EX/F/L/FV06 写接口。入库与 AuditEvent 的事务边界沿用 docs/member-d/06-commit-audit-and-revision-proposal.md。

## 1. 首批范围与权限

仅导出已入库 FinalAnnotationVersion；ADMIN 同时具有 EXPORT_CREATE 可创建导出，TASK_CREATOR 同时具有该权限且是源任务 ownerUserId 才可创建。读取导出记录需 EXPORT_RECORD_VIEW；创建者只能读本人创建、且来源任务仍获授权的记录。下载每次重新检查当前账号、角色、权限、来源范围与文件状态。管理员查看聚合/导出权限不转化为读取未入库的原始盲标版本权限。

“获授权研究者”尚无独立模型，不新建隐式角色。专家问题文档和教学导出使用独立 sourceType、独立授权规则，未确认前不得走 FINAL_ANNOTATION 的通道。原件上传沿用 F01 的管理员公共库/创建者本人待审提交限制，需 A 确认 DocumentSubmission 后再接入。

首批 EX02 的 format 为单个 JSON 或 XLSX。PNG/SVG 等 C 提供正式全图渲染及不可变文件后开放；CSV/JPG/SPSS 不在首批。原型支持多选格式，而冻结请求只有单个 format：本批页面采用单选配置预览；若团队要求多格式，必须先补批量任务/ZIP 规范，不能重复点击创建几个任务却显示成一条记录。

## 2. 模型字段

所有实体具有共享的 id（服务端 UUIDv4）、createdAt/createdById、updatedAt/updatedById、recordVersion；时间均为 UTC 秒精度。可空值明确为 null，数组无项用 []。下表除标记可空者均必填，生成字段均禁止客户端指定。

### ExportRecord（新）

| 字段 | 类型与约束 |
| --- | --- |
| sourceType | 首批固定 FINAL_ANNOTATION；未来 EXPERT_ISSUE_DOCUMENT/TEACHING_RESULT 需单独评审 |
| finalVersionIds | 非空去重 UUID[]；所有源版本均 IN_DATABASE，持久化后不可替换 |
| sourceManifest | `{finalVersionId,taskId,taskDocumentId,documentTextVersionId,annotationScopeId,guideVersionId,contentDigest}`[]，与上述 ID 一一对应，服务端构建 |
| format | JSON / XLSX；PNG / SVG 仅在 C 适配器就绪且契约确认后扩展 |
| include | 去重枚举数组；核心 ELEMENTS/RELATIONS/PARTICIPANTS/DICTIONARIES/VERSIONS/PROVENANCE 固定包含；可选 ACTOR_DISPLAY_NAMES，不能移除来源 ID |
| status | QUEUED / RUNNING / COMPLETED / FAILED；终态不可原地重跑或覆盖 |
| requestedById / requestedAt | UUID / UTC 时间，由当前身份生成 |
| completedAt | UTC 时间或 null，终态必填 |
| fileId | UUID 或 null；COMPLETED 时引用 AVAILABLE 且匹配本记录的 FileAsset，失败/处理中为 null |
| failureCode / failureMessage | 字符串或 null；FAILED 必填，安全可展示，无路径、凭证或堆栈 |
| sourceDigest | 源 manifest 与选项的 canonical JSON SHA-256，小写十六进制 64 位 |

任务失败保留记录；重试是用新幂等键创建新记录，可增加 retryOfExportId（UUID/null，需团队确认），不得篡改旧记录。首批不提供假“部分失败”：单文件生成失败即 FAILED；未来多格式/多图文件清单才能表达 PARTIALLY_FAILED。

### GeneratedFileMetadata（对 FileAsset 的配套实体）

不直接在冻结 FileAsset 塞任意 JSON。FileAsset 原字段和存储标识由现有公共模型维护；建议增加一对一生成元数据，归属 D/C 共同确认。

| 字段 | 类型与约束 |
| --- | --- |
| fileId | UUID，唯一，引用 FileAsset |
| purpose | FINAL_ANNOTATION_EXPORT / ARGUMENT_GRAPH_RENDER；普通原件由其业务对象授权 |
| exportId | UUID 或 null；结构化导出必须有值 |
| sourceFinalVersionIds / sourceGraphIds | 去重 UUID[]，由服务端取得 |
| rendererId / rendererVersion | 字符串或 null；正式图输出必填，结构化数据可空 |
| componentManifest | `{graphId,componentId,fileEntryName}`[]；图输出包含全部连通分量及孤立命题，不能只输出第一张 |
| contentDigest | 实际文件字节 SHA-256，必须与 FileAsset.sha256 相同 |
| expiresAt | UTC 时间或 null；科研归档默认不自动过期，缓存图可按确认策略过期 |

存储键仅后端可见，不把绝对磁盘路径当作公开 URL。结果先写临时区，计算长度/摘要，再以不可变键发布；文件发布与数据库失败的孤儿清理由内部任务处理，不能把半文件标 AVAILABLE。资源存储根和大小/类型限制需部署配置确认。

### DownloadRecord（新，记录凭证签发）

| 字段 | 类型与约束 |
| --- | --- |
| fileId / requestedById | UUID，服务端授权后生成 |
| exportId | UUID 或 null，EX05 签发必须对应 COMPLETED ExportRecord 的 fileId |
| purpose | 1–500 非空白字符；用户填写下载目的 |
| issuedAt / expiresAt | UTC 时间；建议短时 5 分钟，有效期由配置固定，需团队确认 |
| tokenDigest | 只存高熵随机令牌的 SHA-256，不存明文令牌；仅服务端可读 |
| revokedAt | UTC 时间或 null，仅显式撤销命令可改；GET 不改 |
| requestId / auditEventId | UUID，关联签发请求和永久审计 |

这里记录的是“获准下载/凭证签发”，不能声称 GET 传输已完成。F03/EX05 在一个事务内保存 DownloadRecord、AuditEvent 与幂等结果；24 小时同键同请求返回第一次的凭证，即使凭证已过期也不延长有效期；用户需新键签发新票据。为可重放首次凭证，首次响应需加密保存在受限幂等存储，凭证不得进入普通审计或应用日志。

F02 校验令牌摘要、当前主体、文件绑定、有效期、撤销和资源授权，只读文件，不能消费凭证、追加 DownloadRecord 或更新 lastDownloadedAt。有效期内重试返回相同不可变字节；过期、错文件、错主体、权限撤销均拒绝。响应头 Content-Type/Length/Disposition，文件名需安全编码。GET 不写业务或下载审计。

## 3. 需要同步的传输缺口（评审后一起改 MD、全量 YAML、READY YAML、Mock）

| 接口 | 建议与现有缺口 |
| --- | --- |
| F01 | 当前只有 status/sha256，缺 fileId/recordVersion，A/B 无法引用；建议返回完整 FileAsset 的安全投影，不含 storageKey |
| EX01 | items 明确为 `{finalVersion,document:{id,title,caseNumber,sourceType},guideVersion:{id,versionCode},elementCount,relationCount,previewElements}`；meta 按权限过滤后分页；previewElements 前五行带动态 labelCode 和 UTF-16 位置 |
| EX02 | 当前只返 status，缺后续查询的 exportId；建议返完整 ExportRecord 安全投影（含 id/recordVersion），状态 QUEUED；Idempotency-Key 保证同键只一条 |
| EX03 | items 为 ExportRecord 安全投影，data.total 与 meta.total 一致；status/format 精确枚举，sort 白名单；文书检索需补 q，原型的搜索框不能擅加未声明查询参数 |
| EX04 | 当前仅 status/fileId，不足记录详情；建议完整安全投影和 FileAsset 下载元数据，不返文件路径/令牌摘要 |
| F03 / EX05 | 保留 fileId/downloadToken/contentUrl，建议补 downloadRecordId/expiresAt 供页面显示有效期；客户端调用 F02 必须附 downloadToken，不能当永久公开链接 |
| FV06 | 保留 status/committedAt；后续操作需 recordVersion 时一并补丁，审批证据与审计原子性见第 8 步提案 |
| L01–L03 | 事件/来源/历史先冻结实体与 DTO，按对象授权；不能直接公开私有 transition 表充当公共 AuditEvent |

首批结构化文件包含固定原文/范围与摘要、UTF-16 半开偏移、动态标签/关系字典、Element/Relation/RelationParticipant 完整身份与引用、FinalAnnotationVersion 及 provenance、对应图与布局。机器候选不得作为最终来源。JSON 关系事实只能为结构化参与项，公式是可选 display 字段。XLSX 建议按 Manifest/TextVersions/Scopes/Labels/RelationDefinitions/Elements/Relations/Participants/FinalVersions/Provenance/Graphs/Layouts 分 sheet，以 ID 外键关联；文本值写字符串单元格，防止公式被执行。

## 4. 评审决策与后续实施顺序

待确认：上述 RBAC 范围、单格式策略、DTO 增补、文件存储和保留期、5 分钟有效期、加密幂等响应、AuditEvent 及共享事务适配。确认后再实施：V6 迁移和审计原子事务 → FV06 唯一入库 → JSON/XLSX 文件生成 → EX 状态与不可变文件 → F03/EX05 签发与 F02 只读 → 接通页面 → C 全图及失败验收。模型确认是详细计划第 8、9 步的既定前置条件，不以本地预览替代正式验收。

必须验证：无权限/跨任务/未入库拒绝；不同指南字典分别保留；快照与文件重启后不变；幂等、并发唯一入库和失败回滚；过期/错文件/错主体下载；连续 GET 不产生任何业务写入；失败导出不能签发票据；全部图分量可复现。
