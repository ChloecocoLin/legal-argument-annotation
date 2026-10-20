# 第 1–3 批试行交接与剩余依赖

日期：2026-10-20。基线 e9da5b2，工作分支 feature/member-d。依据原《成员D分步开发计划.md》和《成员D后续完成顺序-2026-10-20.md》，本文件记录实际实现，不将试行当成正式联合验收。

用户授权：按现有文件/入库提案在 D 分支试行；本次另确认内部切割边界的所有两两 F1 均值算法，以及独立 CorrectionRound 补正轮次。全部保留待团队评审标记。正式 README、共享契约和其他成员的职责不能被本文件替代。

## 交付状态

| 批次 | 本次实现及验证 | 仍不能勾为正式完成 |
| --- | --- | --- |
| 第 1 批（原步骤 9） | FileAsset / ExportRecord / DownloadRecord 持久化；JSON / XLSX；冻结输入、不可变文件和摘要；真实队列处理/失败状态；凭证及重复 GET；导出中心、记录和下载目的；管理员未绑定原件私有上传 | A 的创建者待审提交、业务文件归属与公共发布；正式共享模型、部署存储及保留策略；C 的 PNG/SVG |
| 第 2 批（原步骤 6、5、2–4） | 真实独立切割输入、版本化计算、统一关口确认/读取/本人草稿绑定；人工命题决定试行入口；完整切割→提交→两阶段裁定→最终版测试 | A 的正式切割输入/任务与共享事务，B 的正式对齐/拆分合并/自定义/专家回流，C 的认证/关系语义/完整图；S04/S05/J04 正式模型仍未冻结 |
| 第 3 批（原步骤 7、8、9 回验） | 独立补正会话与下一最终版，旧版/旧会话保护；已入库成果列表/完整详情；按实际指南字典计标签/关系；真实计算记录来源；导出/签发追溯；给 A 的 T14 双来源只读描述；回归结构化导出 | FV02 完整正式传输、正式证据批准、公共审计模型与跨模块审计、A 的 T14 实际建任务、C 的完整图及 PNG/SVG；生产数据库联验 |

因此，本次完成三批在 D 分支可独立实现的试行内容，第 2、3 批的正式跨成员闭环仍待交付和评审。没有实现或声称已完成教学、自动任务这两批后续范围。

## 页面核查

| 页面 | 路径 | 如何核查 |
| --- | --- | --- |
| 导出中心 | /export | 管理员/创建者；仅 IN_DATABASE，JSON/XLSX 单选、核心来源不可取消；提交后进入记录；原型 1.6:1 左配置右预览布局保留 |
| 导出记录 | /export-records | 状态/分页、真实大小、详情/来源与文件摘要、导出及凭证签发追溯；填写目的后申请并下载，过期重新申请 |
| 切割试行面板（新增协作入口） | /segmentation | 标注员仅本人输入，裁定者读取 N 人输入并计算/确认；原文 UTF-16 整数切割；已进入裁定/归档只读；不替代 A 的正式文本划选工作台 |
| 冲突解决 | /adjudication/conflict | 人工命题提交使用 trial-decisions，旧 fixture-decisions 别名保留；补正轮次从新 sessionId 进入；拆分合并/自定义继续明确门禁 |
| 最终审查 | /final-review/:id | FV04 后可创建独立补正轮次；旧版指向新会话并只读；新会话按案例连续 F 号生成；正式批准门禁不变 |
| 已入库成果（新增） | /results | 管理员/源任务创建者；只读列表、全部要素/关系/参与项/来源与动态字典分布；历史预置关口不冒充真实算法记录 |

运行库没有被强行批准或入库。当前已有 F2 为 IN_REVIEW，因 C 等正式验证未就绪，导出及成果页面空状态正确。正向生成/下载使用隔离测试库，测试证书替身只存在 src/test。

## 第 1 批：文件与下载

迁移 V7 只在 trial 目录。EX01 按授权先过滤再分页，items 为 finalVersion/document/guideVersion/elementCount/relationCount/previewElements；EX02 返回完整服务端 ExportRecord；EX03/EX04 完整记录，完成后附不含 storageKey 的 FileAsset。每次单格式、1–100 个已入库版本，禁止客户端注入状态、身份、文件路径。

EX02 冻结实际输入后入队。每 3 秒本地 worker 取一条，在行锁下发布一次；处理与终态写入同一事务，外部只见 QUEUED→COMPLETED/FAILED，不伪造百分比。内容错误真实 FAILED，原文件不覆盖；文件/数据库/审计故障回滚保留 QUEUED 供重试。单文件上限 30 MB，输入上限 2000 万 JSON 字符。

JSON 包含文本、范围、指南/动态字典、完整实体、图/布局、最终版、采用/排除来源；源版本只投影元数据及源对象身份，不向创建者额外开放被排除的盲标原文。新增切割关口、计算和提交作为可复算来源；旧 fixture 切割只有关口记录。

XLSX 为真实 SpreadsheetML ZIP 工作簿，以 ID 关联各 sheet；单元格为字符串，公式不执行。超过 30000 个 UTF-16 单位的文本分入 LongText，主表引用 `@LongText:sheet:row:field`，按 part 连续拼接；分段不切开代理对，XML 禁止字符导致明确失败，禁止静默截断。Office 格式依据：[Microsoft SpreadsheetML](https://learn.microsoft.com/en-us/office/open-xml/spreadsheet/structure-of-a-spreadsheetml-document)、[Cell](https://learn.microsoft.com/en-us/dotnet/api/documentformat.openxml.spreadsheet.cell)。

F03/EX05 的 POST 保存 DownloadRecord、签发事件和加密幂等响应，5 分钟票据绑定 actor/file/export；只存令牌 SHA-256，幂等响应 AES-GCM。24 小时同键重试返回首次响应，即使过期也不延长；新键申请新票据。F02 bearer+downloadToken 只读，校验当前权限/范围、主体/文件、到期/撤销、大小/摘要。重复 GET 不追加记录、不核销票据，签发事件不能被描述为传输已完成。

文件保存至 `member-d.files.root`，默认 backend/.local-data/files。UUID 不可覆盖键，回滚清除本次新发布文件；没有用路径拼接接收任意客户端存储键。数据库和文件系统不是真正分布式事务，进程在发布后提交前崩溃仍可能留下孤儿，生产接入需补按数据库引用清理的保留任务。密钥 .ticket-key 与归档字节需同部署备份，不能提交仓库。

F01 本批只支持管理员的未绑定私有原件（UTF-8 TXT/PDF/DOCX，20 MB；返回安全完整资源），由上传者当前管理权限读取。没有提供创建者任意上传后公开原件的旁路。内容检查是编码/文件头/容器基础检查，文书抽取、待审提交和正式业务绑定仍由 A 接入。

## 第 2 批：算法与真实输入

策略接口 SegmentationAgreement 可替换，当前算法 `PAIRWISE_INTERNAL_BOUNDARY_F1_MEAN / trial-1`。每名当前有效独立标注员取最新 attempt，至少 N 且至少 2 名不同人员；校验当前分配/成员、同案例/文本/范围和文本摘要。

从每份连续完整 UTF-16 半开区间取内部边界，不包含范围起终点。两人 F1 = 2×交集数/(两集合大小之和)，两者均空为 1、仅一者空为 0。N 人取所有无序两两 F1 等权均值。用 BigInteger 有理数未舍入值比较 > 4/5；0.8 必须失败。显示小数不参与判断。

计算追加不可变记录，固定 submissionIds、inputDigest、每对分数、精确分子分母、算法版本及阈值。确认必须计算通过、输入仍为当前有效集合、边界合法、理由非空；每计算只能确认一次，同键返回首次。标注员显式将关口绑定自己的 ACTIVE 草稿（If-Match），不自动改要素边界，也不更换旧正式版本的切割基准。

本地桥接 local_annotation_dependencies 仅 local/test；没有替 A 新建正式共享表，也没有将模型 PENDING 改为 READY。命题 J04 仍是用户可人工操作的试行入口，正式对齐/一对多/多对一及 B 自定义保存尚未接通。

## 第 3 批：补正与成果

V9 保存 CorrectionRound，parentFinalVersionId/sessionId 唯一。仅本案例裁定者、最新且有 FV04 历史的 DRAFT 可创建；案例→任务文书→任务→会话锁、If-Match、幂等、理由、专家现有事项检查都在事务内。

补正生成新 session（固定原不可变 sourceVersionIds），重新建立全部命题项，重新执行 J04/J05/J07/J08；旧 session 和父最终版增加接替会话引用，旧决定和内容保留且禁止继续写。专家证据不自动继承，新会话重新检查任务要求。下一最终版使用案例连续版本号。新旧决定按动作及选定来源等语义比较，只有编号/时间/理由变化时拒绝生成，避免重建同内容冒充补正。

成果列表/详情按当前源任务范围授权，只读已入库版；数量按实际指南的 labelDefinitionId / relationDefinitionId 汇总，不硬编码标签或将公式当事实。详情关联真正的 AgreementCalculation；旧 fixture 切割明确提示不可复算。

导出记录的永久 data events 与 FV06 审计模型独立，新增授权读取接口，不把异构私有记录直接塞入 L01 的既定 DTO。UI 可读 EX02、worker、EX05/F03 签发轨迹，无 storageKey、票据或摘要凭据。

T14 只提供当前授权已入库版的 `sourceTaskId + sourceFinalVersionId + contentDigest + 固定文本/范围/指南` 描述，不虚构 A 的任务创建逻辑；原任务和最终版保持只读。真正建任务与双来源保存要由 A 的 T14 服务在共享事务中校验实现。

## 试行接口目录

统一成功/失败信封、UUIDv4、UTC 秒精度、写入 Idempotency-Key 延续既有规则；草稿绑定和补正需对应对象 If-Match。所有新增业务服务及迁移只加载 local/test，prod 不加载；正式适配缺失时继续失败，不回退模拟认证/验证。

| 路径 | 方法与用途 |
| --- | --- |
| /api/v1/file-assets | POST multipart file + application/json metadata{fileName,mimeType}，私有原件 |
| /api/v1/file-assets/{id}/download-tickets | POST {purpose} |
| /api/v1/file-assets/{id}/content | GET ?downloadToken=，二进制 |
| /api/v1/exports/eligible-final-versions | GET guideVersionId/sourceType/page/size |
| /api/v1/exports | POST {finalVersionIds,format,include}；GET status/format/page/size/sort=requestedAt:desc |
| /api/v1/exports/{id} | GET 完整记录及安全文件元数据 |
| /api/v1/exports/{id}/download-tickets | POST {purpose} |
| /api/local/member-d/segmentation/cases/{caseId} | GET 授权工作区 |
| 上一路径/submissions | POST {boundaries} |
| 上一路径/calculations | POST {} |
| 上一路径/checkpoints | POST {calculationId,unifiedBoundaries,reason} |
| 上一路径/draft-binding | POST {draftId,checkpointId} + If-Match |
| /api/local/member-d/adjudication/proposition-items/{id}/trial-decisions | POST 沿用 DecisionCommand；旧 fixture-decisions 别名兼容 |
| /api/local/member-d/final-versions/{id}/correction-rounds | GET/POST {reason} + If-Match |
| /api/local/member-d/results | GET guideVersionId/sourceType/page/size |
| /api/local/member-d/results/{id} | GET 完整成果与统计 |
| /api/local/member-d/results/{id}/reannotation-source | GET 供 A 的 T14 双来源描述，不创建任务 |
| /api/local/member-d/export-records/{id}/events | GET 本记录授权的导出/签发事件 |

评审格式及独立示例：contracts/review/member-d-batches-trial.schema.json、member-d-batches-trial.mock.json。示例仅供结构评审，不加载运行库，不以示例摘要作为真实证据。冻结 openapi-ready.yaml 及生成 SDK 未修改，SHA256 仍为 813539491A53C0151C44BD426985F74A1DF4D5AD35A7DF1955D20C9CAE13B390。评审后须同步修订 Markdown、完整/READY YAML、Mock，再生成 SDK。

## 验证及下一步

后端 94 项测试全部通过（原 68 项 + 新增 26 项），前端 vue-tsc/Vite 构建通过。评审 JSON Schema 的 7 组示例全部通过。独立 openpyxl 读到 23 张工作表，LongText 完整还原且无公式单元格。页面证据位于 screenshots/batches-*.jpg。用独立 openpyxl 读取 XLSX、还原 LongText 并验证没有公式单元格。覆盖同键/异内容、并发生成、审计故障回滚、越权/权限撤销、到期/错主体/错文件/篡改摘要、只读 GET、0.8、N 人均值、空边界、UTF-16 代理对、输入更新失效、完整真实切割链、补正保留与实质改变等。

剩余推进顺序：先 A/B/C 交付及公共模型评审 → 正式对齐/自定义/专家/完整图和认证/共享事务 → 正式证据批准及 T14 建任务 → PNG/SVG 和生产数据库联合验收。可独立的自动任务和教学模型准备按原后续批次推进，不提前宣布科研正式主线完成。

离线复核工具：`scripts/verify-member-d-trial.py`（校验工具依赖 jsonschema/openpyxl，不增加后端运行依赖）。可用有相应库的 Python 分别执行 `--schema-only` 和 `--export-only`；先运行 Maven 测试生成 target/member-d-export.xlsx 与长文本对照文件。最终源码回归报告位于 backend/target/surefire-reports，构建及测试输出不入库。

后续核查更正（2026-10-20）：已读到 A/B/C 的任务、专家、关系/图和认证等实现，不能把当前 D 分支未接入写成其他成员未交付。具体可接入能力和仍需适配的边界以 [修订接入计划](11-cross-member-integration-plan.md) 为准。
