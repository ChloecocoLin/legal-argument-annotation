# 入库、审计与最终草稿修订提案（待团队评审）

日期：2026-10-15。本提案对应计划第 8 步准备工作，涉及 FV06、L01–L03、T14；同时登记第 7 步退回后内容修订协议缺口。不修改冻结契约，不将 PENDING_MODEL 改为 READY。

后续状态：用户已授权按本提案在 D 分支试行，仍待团队评审；试行实现、测试和生产隔离边界见 [09 试行交接](09-commit-audit-trial-handoff.md)。本次授权未冻结 CorrectionRound 或替代其他成员的生产适配器。

## 入库的原子边界

FV06 只接受当前案例最新且 APPROVED 的最终版；请求带 Idempotency-Key 与 FinalAnnotationVersion 的 If-Match。按案例 → TaskDocument → Task → Session → FinalVersion 的统一锁顺序检查权限、状态、来源、全部命题及关系决定、正式验证证据、专家问题及任务专家要求。质量证据必须绑定最终内容摘要、指南/文本/切割版本和当前决定集合，不能使用客户端传入的“通过”布尔值。

建议同一数据库事务内完成最终状态 IN_DATABASE、committedAt、提交人、版本递增、案例锁定、源成果只读保护、会话 COMPLETED、永久 AuditEvent 和幂等结果。多案例任务仅在所有案例入库后 COMPLETED；第一案入库不能提前阻止其他未入库案例工作。任务/案例锁定范围需要 A/D 共同确认。审计或任一写入失败，全部回滚；并发或相同请求不得产生第二次入库。

建议数据库对每案例有效入库结果建立唯一约束。幂等重试在检查当前身份、范围后先返回首次结果，不能因原 If-Match 过期重复入库。同键异内容冲突。APPROVED 的 committedAt 必须为 null，EX01/EX02 严格要求 IN_DATABASE；不通过隐藏菜单实现资格检查。

## 公共 AuditEvent 建议模型

公共字段：id、createdAt/ById、updatedAt/ById、recordVersion（初始 1）。业务字段建议：actorUserId、effectiveRole、permissionCode、requestId、operationCode、objectType/objectId、taskId/taskDocumentId/sessionId、occurredAt、beforeState、afterState、reason、sourceVersionIds、decisionIds、relatedObjectIds、transactionId、contentDigest。时间为 UTC 秒精度，身份和时间由服务端填充。

AuditEvent 追加后不可覆盖；敏感内容、令牌、凭据不入日志。保留最小必要前后值与摘要，完整源内容从不可变快照按权限读取。若需要跨服务审计，应明确 outbox 与业务原子提交及重放策略，不能先写业务再“尽力记日志”。审计的保留期、可见范围、导出与访问记录需团队确认。

L01 按授权范围分页过滤事件；L02 只返回可访问对象的来源链；L03 的格式、导出授权及文件记录需随文件模型确认。不能把系统管理员自动赋予盲标内容读取权，也不能给专家整版来源访问权。

当前实现中的 final_review_transitions、adjudication_transitions 是 D 的永久流程历史，不等于公共 AuditEvent。24 小时幂等表不是审计表；历史与退回理由不能随幂等过期而删除。

## 重标与最终草稿修订

入库后通过 A 的 T14 新建任务，sourceTaskId 和 sourceFinalVersionId 必须同时保留，原任务与入库版保持只读。T14、入库锁与权限需联合验收。

FV04 的已冻结语义是 IN_REVIEW → DRAFT，必须保留理由与轨迹。现有 READY 契约没有最终要素编辑、重新打开命题阶段或修订冻结映射的命令。当前 D 不擅自让 FINAL_REVIEW 会话回到 PROPOSITION_REVIEW，也不回写原始标注。

当前支持：退回草稿、在验证能力修复后重新送审，或调用 FV01 生成下一独立最终快照；上一快照内容保留、历史版只读。若要调整命题/关系本身，建议团队确认独立 CorrectionRound（session、parentFinalVersion、sourceDecisionSet、targetItems、reason、阶段、重新确认记录），明确 J04/J05/J07/J08 的新轮次、映射冻结、专家复核继承与失效规则，再同步所有契约与样例。不能用前端表格编辑或重新生成同一内容冒充业务补正已完成。

## 评审与验收用例

A：多案例任务锁定与 T14；B：最终来源、自定义快照和重裁轮次；C：正式图示、完整语义及绑定内容摘要的验证证据；D：事务、状态、历史、入库及来源链。统一生产数据库与共享事务仍需团队确认。

至少验证：并发入库、审计失败回滚、过期版本、同键重试/异内容、权限撤销、APPROVED 禁止科研导出、单案入库不锁死其他案、已入库数据所有写入口拒绝、重标双来源、源 ID/内容不变、最终草稿重裁后旧映射失效。
