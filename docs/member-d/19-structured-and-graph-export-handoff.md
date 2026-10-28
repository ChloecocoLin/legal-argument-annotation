# 第六批：真实结构化导出回验与图示归档

日期：2026-10-21。分支：feature/member-d。适用 local/test 且非 prod；沿用已授权的 D 分支试行。**核心导出与归档闭环已完成；附件业务绑定及生产模型评审未完成，不将其标记为整批正式转正。**

## 1. 修改与真实来源

- 修复切割来源：优先从共享 workflow_records 的 segmentationSubmission 读取真实 S01 提交，只有 NOT_FOUND 时才兼容旧试行 SUBMISSION。原先真实统一关口引用的新提交无法被导出器找到。
- JSON/XLSX 保留冻结文本、UTF-16 位置、动态指南字典、要素/关系/参与项完整身份、固定来源版本、切割提交/计算/关口、最终版和来源链。新增生效决定、自定义快照的元数据及最终 provenance 明确引用的已确认专家结论/指南依据。只导出引用记录，不导出全部盲标候选、自定义草稿或完整专家问题文书。
- schemaVersion 为 member-d-export-trial-2，带 validationMode=D_BRANCH_TRIAL；Excel 的 Manifest 同样标明试行，新增 CustomReferences/ExpertReferences 工作表。旧文件原样保留，不重新生成。
- 修复导出中心映射：文书 id 曾覆盖 finalVersion.id，导致选中 F1 后提交“最终版本不存在”；现在始终保留最终版 ID。超时重试保留同一请求编号与请求内容，期间禁用选项修改。

## 2. PNG/SVG 的受控服务器试行方案

新增独立内部入口 POST /api/local/member-d/graph-exports，严格只接受 finalVersionIds、format、include。公开 EX02 仍只接受 JSON/XLSX；未修改冻结 READY YAML 或生成 SDK。

服务器按当前身份和任务归属读取 IN_DATABASE 冻结快照，用第四批 C RelationValidator/GraphAssembler 纯适配重新验证/组图。冻结 renderGraphs 绑定内容摘要、指南、决定、渲染器版本及 renderInputDigest；后台处理时再次核对摘要与渲染版本。客户端不能提交节点、SVG、PNG、坐标、摘要或“已验证”标记。

渲染器 C-geometry+D-java2d-trial-1 参照 C graphGeometry/原型：命题 58×38 矩形、关系圆半径 12、S 实心、A 空心、J/M 带加号、I 成员编号框与虚线；有向边裁切至边界并画箭头。颜色采用 frontend/src/styles/tokens.css。沿用分量、分层 BFS、层内编号排序等几何规则，由同一组绘图原语输出 SVG 或 Java2D PNG。

所有选中最终版的分量及孤立命题组成**单张完整图示**，与多文件 ZIP 区分。每个分量保存 finalVersionId、graphId、componentId、fileEntryName、contentDigest、完整 nodeIds/edgeIds、isolated、viewport。布局为服务器确定性自动布局，不读取或修改可变图布局；不会声称与用户拖动坐标逐像素一致。

限制：累计最多 1000 个图节点，宽/高不超过 8000，最多 1600 万像素，文件最大 30 MB；超限明确失败，无截断或只取第一张。默认字体随服务器 JRE/系统可能不同，跨环境字节一致不作承诺；同一归档文件始终按已存字节下载。此等价服务器方案仍待 C/D 联合评审，不能当作生产正式渲染证书，formalReady 未解锁。

## 3. 文件、记录与下载

沿用不可变发布和事务回滚机制，完成后同事务保存 FileAsset、GeneratedFileMetadata、ExportRecord 终态及事件。生成元数据包含渲染版本、来源清单、分量清单、输入摘要与实际字节 SHA-256；文件不可覆盖。生成失败不发布 AVAILABLE 文件，发布/数据库/审计故障回滚并保留 QUEUED 以供真实重试。

下载仍须当前权限与任务归属、填写用途、申请五分钟凭证。记录保存凭证签发事实；不声称客户端已完成传输。F02 的重复 GET 不改记录或审计，返回相同归档字节。不存在接受任意客户端图片后标为“正式图”的通道。

浏览器的 C 预览导出与本次服务器归档分别管理，不能把浏览器直接下载当作 ExportRecord。导出记录页可查看生成来源与全部分量清单。

## 4. 页面核查

| 页面 | 核查内容 |
| --- | --- |
| /export 导出中心 | 真实已入库候选、正确最终版 ID；JSON/Excel/PNG/SVG 单格式选择；图示试行说明；按格式区分图示与完整文本选项；超时沿原请求重试 |
| /export-records 导出记录 | 完成状态、大小、来源/文件摘要、生成元数据/分量清单；下载用途及凭证；导出与下载追溯 |

截图：screenshots/integration-batch6-export-center.png、integration-batch6-export-records.png。实际归档图：screenshots/integration-batch6-archived-graph.png。

## 5. 验证与可复核数据

- 全量后端 154 项，失败/错误/跳过均为 0；增加真实 S01→两人草稿→命题/关系自定义→专家→试行批准/入库→四格式导出的集成用例。前端构建通过（保留已有 bundle 大小提示）。补齐 componentId/fileEntryName 后再运行两项渲染测试通过。
- 真实专家引用测试按最终 provenance 导出，包含本轮已引用的关系专家结论；不虚构未发生的命题专家复核。
- 图示单测覆盖 S/A/J/M/I、嵌套关系、孤立命题、精确节点/边覆盖、SVG XML 转义、PNG 可解码、两个格式同结构/尺寸、确定性输出、摘要/渲染器篡改及超限失败。既有长文本无损 Excel、防公式、不可覆盖、回滚、并发、下载权限/过期/完整性测试继续通过。
- 实际已有 F1 552259f0-374a-472f-9927-2697c7ad9371 完成四种文件；页面另外实际创建 PNG 并填写用途下载。全链及重启回验确认旧最终内容、T14 新任务、全部文件/记录/摘要未改变，重复 GET 不写审计。精简证据见 19-export-acceptance.json，无凭证。
- 脚本 scripts/verify-member-d-exports.py：仅回环地址；--create 明确新建四种试行导出及下载票据；--verify BASELINE 使用已有记录重新签票并比对持久化文件。票据及身份令牌只存内存，不写基线或日志。详尽基线/文件在 backend/target，未提交；持久化数据库已备份，未清空。
- 既有 verify-member-d-closed-loop.py 冻结接口/盲标权限/入库审计/T14 检查继续通过。READY SHA-256 保持 813539491A53C0151C44BD426985F74A1DF4D5AD35A7DF1955D20C9CAE13B390。

## 6. 明确保留的接入与评审项

1. A 的 DocumentService 仅持有 originalFileId 引用，创建者原件提交模型仍待评审；B 的 GuideServiceImpl.patchAttachment 仍将 attachmentFileId 写入旧指南表，尚无共享文件归属、权限及原子绑定证明。不能仅据这些 ID 开放文件。本批未伪造这些文件，也未扩大未绑定原件的下载权限（仍仅上传管理员本人）。下一步须 A/B 确认提交/解绑与业务权限，在同一事实源事务绑定后接 F01/F03；此项未完成。
2. 多指南并列、复杂 J/M/I 的完整真实科研数据尚待团队提供案例集中联验；当前已有动态字典保留实现，结构/长文本由单元与既有夹具测试覆盖，不宣称所有指南/所有研究数据均已实测。
3. 生产文件模型、永久审计、受控渲染方案转正、EX/F DTO 冻结扩展，仍需团队评审。新接口及分量模型见 contracts/review/member-d-graph-export-trial.openapi.yaml 和导出模型提案增补。
4. 专家问题独立文书、教学成果、导入映射、额外格式、批量多文件 ZIP 属于待冻结扩展；不把最终版中的专家来源引用冒充独立专家文书导出。

下一主线：第七批自动任务接入。上述附件与评审缺口并行保留；不把它们无声转成“已完成”。
