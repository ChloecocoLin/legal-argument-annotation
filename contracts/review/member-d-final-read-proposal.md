# FV02 完整读取组合 DTO 提案（待评审，不替换冻结输入）

当前公开 FV02 严格返回冻结摘要 `{id,status,elementIds,provenanceCount}`；FV01/FV03/FV04/FV05 也保持原响应。页面本地完整读取使用显式 local/test 适配器，生产不得回退。此文件仅提出只读传输补全，不定义新的公共持久化表，也不把待定 AuditEvent/修订轮次变成 READY。

建议 FV02 的 data 改为 FinalReviewContext：

```typescript
interface FinalReviewContext {
  snapshot: {
    version: FinalAnnotationVersion; // 核心模型全部字段与公共字段
    elements: Element[];
    relations: Relation[];
    participants: RelationParticipant[];
    graph: Graph;                   // 所有独立结构和孤立命题
    layouts: GraphLayout[];          // 布局与逻辑分开
  };
  session: AdjudicationSession;
  sources: {id: UUID; displayCode: string}[];
  document: {id: UUID; title: string; caseNumber?: string};
  guideVersion: GuideVersion;
  summary: {propositions: int; relations: int; expertReviews: int; excluded: int};
  quality: {
    checks: {code: string; message: string; passed: boolean}[];
    issues: {code: string; message: string; blocking: boolean}[];
    healthy: boolean; formalReady: boolean; mode: string;
    canSubmit: boolean; canApprove: boolean;
  };
  history: FinalReviewTransitionView[];
  latest: boolean;
}
```

FinalAnnotationVersion 必须含公共 id/createdAt/createdById/updatedAt/updatedById/recordVersion，以及 taskDocumentId/sessionId/versionNumber/status/documentTextVersionId/annotationScopeId/guideVersionId/elementIds/relationIds/graphId/provenance/approvedById/approvedAt/committedAt。未批准、未入库时间显式 null。

provenance 每项含 resultKind（ELEMENT/RELATION/EXCLUDED_ELEMENT/EXCLUDED_RELATION）、resultId（排除为 null）、sourceVersionIds/sourceObjectIds、decisionId、expertIssueId（无专家来源为 null）。完整来源包含未采纳候选及被删除关系，不只列最终对象。

FinalReviewTransitionView 为单最终版授权流程视图，含 id/sequenceNumber/operation/createdAt/createdById、command（FV04 有 reason）、before/after FinalAnnotationVersion。它不等同系统 AuditEvent，不允许任意对象或跨任务日志查询。私有 decisionDigest、数据库索引和本地 fixture 字段不进入生产公开 DTO。

canSubmit/canApprove 只是读取时提示，写入必须在事务中重新核验身份、资源状态、If-Match、决定及绑定当前内容的正式依赖证据。禁止客户端提交 quality、formalReady、mode 或直接修改 status。没有正式证据时，不能把结构检查的通过变成科研批准。

新的状态转换仍读取资源 recordVersion；FV03/FV04/FV05 可保持冻结短响应，成功后重读完整 FV02。历史快照按稳定 ID 可读但不可写。FV02 授权查看者的开放条件须与 C/A 明确；在入库/公开规则落定前仅授予负责案例的裁定者，不自动给任务创建者、管理员、标注员或专家整版读取权限。

团队确认后同步 Markdown、两份 OpenAPI、完整 Mock，再更新 READY 哈希与生成物。最终草稿内容修订和重裁轮次另见 docs/member-d/06-commit-audit-and-revision-proposal.md；不通过本只读提案悄悄新增写入动作。
