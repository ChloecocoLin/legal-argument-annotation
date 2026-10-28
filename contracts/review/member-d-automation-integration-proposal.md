# 第七批自动任务内部接入提案（待团队评审）

日期：2026-10-21。M01–M04 的 READY 请求/响应保持不变；本文件不是冻结契约变更。

## 1. 已冻结实现及传输缺口

| 接口 | 实现与保持的格式 |
| --- | --- |
| M01 | GET /api/v1/automation-tasks，按当前 OWNER 和权限过滤后分页；items 仅 taskDocumentId/status，不添加 id |
| M02 | POST /api/v1/task-documents/{id}/automation-tasks，仅 modelId/modelVersion；响应仅 status=DRAFT |
| M03 | GET /api/v1/automation-tasks/{id}，仅 status/modelVersion/suggestionCount |
| M04 | POST /api/v1/automation-tasks/{id}/transitions，仅 action=QUEUE/CANCEL；Idempotency-Key 与 If-Match；仅返回 status |

页面无法从上述投影得到后续操作所需的自动任务 ID、recordVersion、阶段、模型配置和失败信息。因此另设内部联调入口，不手改 READY 或生成 SDK：

- GET /api/v1/integration/automation/workspace：items 为完整 AutomationTask 安全投影；total、models、cases、realModelAvailable=false、reviewContractReady=false。status/page/size 白名单，按权限过滤后分页；cases 只包含本人有效 OWNER、已启用且可写的案例。
- GET /api/v1/integration/automation-tasks/{id}/workspace：task、taskName、documentTitle、suggestions、events。无原始模型输入、私有执行凭据或其他标注员人工结果。
- POST /api/local/member-d/task-documents/{id}/automation-tasks：与 M02 同一服务、请求和幂等空间，返回完整首次结果供页面获取 ID；仍仅 local/test 非 prod。
- GET /api/local/member-d/assignments/{id}/model-suggestions：供 B 接入验证的只读适配，校验本人有效标注分配及任务成员、文本/范围/指南基准；返回 items 和 reviewContractReady=false。不是公共 M05；没有采纳、修改、拒绝或写草稿接口。

建议将来共同评审 M01/M02/M03/M04 的完整安全投影及模型配置发现接口；在此之前保留内部路径。

## 2. 持久化、状态与执行边界

沿用共同 JDBC workflow_records 和事务：automationTask、automationInput（私有冻结输入）、automationRun（私有唯一领取及期限）、modelSuggestion、automationEvent。24 小时幂等结果沿用 annotation_mutation_receipts；不复制另一份任务或进度事实源。正式 SQL 实体/迁移与生产模型服务部署需后续评审，当前适配仅 local/test 非 prod。

AutomationTask 保留公共六字段及 taskDocumentId、status、modelId/modelVersion、requestedAt/completedAt；内部补 documentTextVersionId、annotationScopeId、guideVersionId、executionMode、providerVersion、inputDigest、stage、progress、startedAt、suggestionCount、failureCode/failureMessage、reviewAvailability。模型输入固定文本及其哈希、确认范围、绑定指南字典和模型描述，工作进程在执行前及发布前重新核对。

状态：DRAFT→QUEUED→RUNNING→COMPLETED；工作进程可将 QUEUED/RUNNING 记为 FAILED；创建者仅能取消 DRAFT/QUEUED，RUNNING 不提供取消。失败/取消保留旧记录并新建重试；同一案例最多一个 DRAFT/QUEUED/RUNNING。父任务/案例锁定后不允许管理命令继续写入。

后台先在短事务内唯一领取，再在事务外执行适配器，最后一个事务校验并发布**全部**候选、终态及事件。任一数据库/审计异常均回滚发布。领取期限为 120 秒；超时或服务中断后保留失败记录，不自动伪造完成或重复发布。GET 不执行恢复或写事件。

## 3. 明确的测试适配器

当前仅有 member-d-test-adapter / 1.0，providerVersion=deterministic-first-sentence-v1，executionMode=TEST_ADAPTER。无外部模型调用，非 LegalArg-BERT 或已训练模型。规则是范围内首句、指南首个可用命题标签、置信度固定 0；不承诺语义正确，仅供流程验证。创建后仍须显式 QUEUE 才由本地工作进程运行。

当前能力仅 ELEMENT。校验 UTF-16 半开边界及代理对、文本/范围/指南身份、动态可用标签（v1.2 排除 GM-C）、有限置信度 [0,1]、数量及字段白名单。非法输出整批失败；RELATION/嵌套模型输出未接入，不以表达式字符串假装正式关系。将来接真实执行器时需固定模型身份、版本、能力及输出结构，并复用 C 的结构校验。

## 4. 与 B 的待接入项

现有 B 相关分支未发现公共 M05/M06 或 ModelSuggestionReview 实现。共享候选始终 PENDING_HUMAN_REVIEW，targetDraftId/reviewedById/reviewedAt 为 null，不能由首位标注员改变共享状态。本批只验证两份有效分配能读取同一候选，不能读取他人分配/审核结果。

下一步共同确认 M8 独立 ModelSuggestionReview，按 suggestionId + assignmentId 保留每人审核，验证本人 ACTIVE 草稿及基准；采纳/修改后创建新的草稿对象并保存机器来源、审核者及原候选 ID。不能把现有 J10 人工裁定自定义当作 M06。关系候选参与项到本人新对象 ID 的映射及审核并发亦须共同评审。

机器候选不写草稿、切割提交、AnnotationVersion、最终版、进度 X/N 或 SnapshotObjectOwners。即使测试候选生成成功，也须经过独立人工标注、完整提交、裁定及必要专家流程；没有直接入库入口。
