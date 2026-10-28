// 关系裁定类型（J06 读取 + 「生成关系裁定结果」试算）。归成员C。
// 依据：openapi-ready.yaml 的 J06、docs/关系裁定接口约定.md、分工.md §四-4 与 §四-8。
//
// 刻意与 B 的 types/adjudication.ts 分开：那边是命题裁定（J03/J09/J10/J12）与
// 「对象字典」形态的 CustomRelation（带 expression，与正式关系模型冲突），
// 本文件是 C 的关系裁定契约面，两边的候选与决定语义不同，不合并。
import type { RelationType } from './common'
import type {
  ParticipantInput,
  ParticipantRole,
  ParticipantTargetKind,
  Relation,
  RelationIssue
} from './relation'
import type { GraphView } from './graph'

// ── 枚举（契约声明）────────────────────────────────────────────────────

/** 契约声明的五类关系分歧。维度比它细一档，见 dimension。 */
export type RelationConflictType =
  | 'EXISTENCE'
  | 'TYPE'
  | 'DIRECTION'
  | 'PARTICIPANTS'
  | 'AGREEMENT'

/** 裁定项状态。写侧归成员D（J07），本轮恒为 OPEN。 */
export type RelationItemStatus = 'OPEN' | 'DECIDED' | 'ESCALATED' | 'RETURNED'

/** 裁定动作六个（《核心标注与裁定数据模型》§1 对象字典）。 */
export type RelationDecisionAction =
  | 'ADOPT_VERSION'
  | 'CUSTOM'
  | 'DELETE'
  | 'RETURN'
  | 'ESCALATE_EXPERT'
  | 'CONFIRM_AGREEMENT'

/** 关系裁定结果状态。判定优先级见 docs/关系裁定接口约定.md §6。 */
export type RelationResultStatus = 'COMPLETE' | 'INCOMPLETE' | 'INVALID'

// ── J06 出参 ──────────────────────────────────────────────────────────

/** 参与项：displayNo / labelCode / text 是 ELEMENT 目标的补充信息，用于原文核对。 */
export interface AdjudicationParticipantView {
  id: string
  ordinal: number | null
  role: ParticipantRole
  targetKind: ParticipantTargetKind
  targetId: string
  displayNo: string | null
  labelCode: string | null
  text: string | null
}

/** 候选侧（契约只有 candidateRelationIds，可读信息是 C 的追加式超集）。 */
export interface RelationCandidate {
  relationId: string
  /** 缺失一侧（存在性差异）时为 null */
  versionId: string | null
  versionLabel: string
  relationCode: RelationType
  /** 源版本上的展示表达式，后端不落库、读时生成 */
  expression: string
  participants: AdjudicationParticipantView[]
  /** 含无法映射到统一命题集合的端点 → 界面必须拦住，不能对着不可比的候选做决定 */
  unmapped: boolean
}

export interface RelationAdjudicationItem {
  id: string
  sessionId: string
  conflictType: RelationConflictType
  /** 比 conflictType 细一档的中文维度：参与命题 / 组合成员 / 嵌套层级 / 方向 / 关系类型 / 一方新增 / 一方遗漏 / 一致项 */
  dimension: string
  /** 中文差异理由，可直接显示给裁定者 */
  reason: string
  candidateRelationIds: string[]
  candidates: RelationCandidate[]
  status: RelationItemStatus
  decisionId: string | null
  /** 任一候选含关系型端点（嵌套）→ 界面提示先裁内层 */
  nestedFirst: boolean
}

// ── 「生成关系裁定结果」入参 ──────────────────────────────────────────

export interface CustomRelationInput {
  relationCode: RelationType
  /** 端点在**统一命题集合**上（嵌套时可指向本次裁定已保留的关系） */
  participants: ParticipantInput[]
}

export interface RelationDecisionInput {
  itemId: string
  action: RelationDecisionAction
  /** ADOPT_VERSION / CONFIRM_AGREEMENT：采用哪一版 */
  sourceVersionId?: string | null
  /** 采用哪条源关系；与 sourceVersionId 同时给出时以它为准 */
  sourceObjectId?: string | null
  /** CUSTOM 用 */
  customRelation?: CustomRelationInput | null
  /** 采用某版建议填写；RETURN / ESCALATE_EXPERT 必填 */
  reason?: string | null
}

export interface RelationResultRequest {
  /** 缺省由后端取会话声明的统一命题集合；本页不传，避免与 D 的 J05 抢事实来源 */
  unifiedSnapshotId?: string
  decisions: RelationDecisionInput[]
}

// ── 「生成关系裁定结果」出参（C 的交付物）────────────────────────────

export interface RelationAdjudicationResult {
  sessionId: string
  unifiedSnapshotId: string
  status: RelationResultStatus
  /** 分歧项数（不含一致项） */
  conflictCount: number
  agreementCount: number
  /** 本次计入「已裁」的项数 */
  decidedCount: number
  /** 未给决定，或决定是 RETURN / ESCALATE_EXPERT */
  pendingItemIds: string[]
  /** 决定不成立、必须重新裁定（引用了已删除的内层关系等） */
  reAdjudicationItemIds: string[]
  /** 合并后的关系，ownerType=FINAL_VERSION、ownerId=统一命题集合 */
  relations: Relation[]
  issues: RelationIssue[]
  /** 正式论证图，形状同 GR01 的 GraphView */
  graph: GraphView
}
