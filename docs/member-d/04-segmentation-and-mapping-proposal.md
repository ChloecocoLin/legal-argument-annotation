# 下一阶段模型提案：切割确认与统一命题映射（待团队评审）

本提案对应计划第 6 步，覆盖 M2/M13、S04/S05、J04，并影响 J03/J05/J06/J07/J10/J11/J12。它不修改冻结契约，不创建新的公共实体表，也不将 PENDING_MODEL 改为 READY。

## 切割一致性与确认

A 提供同一 taskDocumentId、documentTextVersionId、annotationScopeId 下每名成员的不可变 SegmentationSubmission。输入固定 submissionIds、attemptNumber、文本哈希与 UTF-16 半开区间；每名成员只取当前有效独立提交。边界算法需要团队选择并给出参考用例，不能由 D 擅自确定：候选包括边界集合 F1 或按文本单元计算的一致性；多于两人时还需确定成对汇总或全体一致口径。

建议 AgreementCalculation 包含公共字段，以及 inputSubmissionIds、documentTextVersionId、annotationScopeId、algorithmCode、algorithmVersion、parameters、perPairScores、aggregateScore、threshold、passed、computedAt。精度、舍入与空输入行为必须明确；判断用未舍入值，严格 score > 0.8。0.8 必须不通过。复算生成新记录，不覆盖旧计算。

S04 请求建议引用 calculationId 与完整统一边界，裁定者填写确认理由。服务端校验输入仍有效、同基准、至少 N 名不同成员、计算已通过、边界合法，并原子生成 CONFIRMED checkpoint。S05 只投影授权关口及输入来源。确认人、时间和 boundaryHash 均由服务端生成。后续草稿固定 checkpointId；新的切割结果不能悄悄改变已有正式版本。

待确认项：算法定义、统一切割被修订时的影响、培训和试标通过条件、协商记录如何与计算关联。

## 命题与非命题对齐

建议 AlignmentGroup 记录 sessionId、itemId、候选集合 candidateElementIds、文本锚点集合（textVersionId/startOffset/endOffset）、alignmentAlgorithm/version，以及人工修订来源。组中允许同版多个候选，不按跨版本 P1/P2 等展示码建立对象身份。

建议 UnifiedElementMapping 是按会话冻结的独立结果，包含 sessionId、propositionDecisionIds、sourceVersionIds、guideVersionId、textVersionId、checkpointId、mappingEntries、frozenAt。每个 entry 包含 sourceElementIds[]、unifiedElementIds[]、decisionId、mappingKind（保留/拆分/合并/排除/非命题）。采用与排除都必须覆盖来源；源 ID 永久保留。集合来源支持一对多、多对一，不能依靠客户端显示编号推导。

J04 保持 DecisionCommand 的采用、自定义、删除、确认一致结构。ADOPT_VERSION 校验本项候选及所属源版本；CUSTOM 引用 B 的 J10 独立快照，不回写源版；DELETE 保留排除依据；AGREEMENT 只能经人工 CONFIRM_AGREEMENT 生效。普通采用和一致确认按 Task.requireAdjudicationReason 校验，其他动作必须非空理由。暂不通过 J04 的 RETURN/ESCALATE 分支制造不完整状态；退回用 J11，专家指派用 J12。

事务：先锁案例与会话，再锁裁定项；检查当前阶段和 If-Match；追加不可变决定、更新当前 decisionId 和 item 状态，递增 item/session recordVersion；同键重试先读原结果，但仍检查当前权限。旧决定不覆盖。拆分合并后的图端点映射需经 C 验证。

J05 逐项检查命题和非命题的生效决定、一致项确认及专家状态，然后生成并冻结映射，调用 C 建立完整关系项（包含一致项），原子进入 RELATION_REVIEW。失败须一起回滚，不能残留命题完成时间或半份关系项。

## 退回和专家阶段

J11 必须指定本会话有效来源的 targetAssignmentIds 和理由。目标草稿恢复可编辑；目标旧版本取消新会话资格但原始内容、ID 与历史裁定引用保留。未退回来源可作为新会话输入。补交产生新的 Vn，新会话固定新来源，不重用 RETURNED 会话。

建议 CorrectionRequest 公共模型补充 sessionId、targetAssignmentIds、sourceVersionIds、reason、requestedById/At、replacementVersionIds 和完成状态。当前代码仅有 D 私有资格索引与幂等记录，不冒充该公共模型或 AuditEvent。

GENERAL 专家问题需记录 originPhase；返回命题还是关系阶段由服务端保存的来源决定。所有专家事项有已确认结论和指南依据后方可解除门禁。M11 的结构化专家结论和讨论轮次应与 B 一并确认。

## 分工与评审产物

A：切割算法和输入读取、计算测试样例、文本/范围、关口事务适配。B：命题比较、对齐组、J03/J09/J10/J12、专家问题与自定义实体。C：统一端点映射、关系比较、嵌套/环校验、图覆盖与图组件。D：会话与决定编排、门禁、冻结映射协调、来源与版本资格、退回事务。

团队确认后同步修订版 Markdown、完整与 READY YAML、Mock 及哈希，再生成 SDK；本提案不得只更新其中一份。评审至少提供：两人及 N>2、0.8/超过0.8、非命题、一致项、拆分/合并、偏离切割说明、跨版本同名编号、删除内层关系、专家未决和退回重提。

当前本地适配器只按 UTF-16 重叠区间组织候选、按映射参与项组织关系；人工逐项创建演示决定。它支持一对一映射，遇到拆分合并或自定义写入明确拒绝，生产环境不加载。
