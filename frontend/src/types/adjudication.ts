// 裁定 J 系列类型（成员B负责 J03/J09/J10/J12）。依据契约 6.4。
import type { EntityBase, RelationType, ParticipantRef } from './common'
import type { Element } from './element'
import type { GuideVersion } from './guide'

// 会话状态
export type AdjudicationSessionStatus =
  | 'PROPOSITION_REVIEW'
  | 'RELATION_REVIEW'
  | 'FINAL_REVIEW'
  | 'RETURNED'

// 命题项分歧类型
export type PropositionConflictType =
  | 'LABEL'
  | 'BOUNDARY'
  | 'AGREEMENT'
  | 'SPLIT'
  | 'MERGE'

// 参与者角色
export type ParticipantRole = 'SOURCE' | 'TARGET' | 'MEMBER'

// 自定义项类型
export type CustomItemKind = 'PROPOSITION' | 'RELATION'

// 仅含 id 的外部对象占位（A/D 域，用到时再补字段）
export interface RefObject {
  id: string
}

// J09 返回的正文版本（页面至少依赖 normalizedText）。
export interface DocumentTextVersionRef extends RefObject {
  documentId: string
  versionNumber: number
  normalizedText: string
  status?: string
}

// J09 返回的说理范围。offset 为相对于完整 normalizedText 的 UTF-16 半开区间。
export interface AnnotationScopeRef extends RefObject {
  documentTextVersionId: string
  scopeType: 'REASONING'
  startOffset: number
  endOffset: number
  status?: string
}

// J09 中源标注版本的页面所需最小字段。
export interface AnnotationVersionRef extends RefObject {
  displayCode?: string
}

// 裁定会话
export interface AdjudicationSession extends EntityBase {
  taskDocumentId: string
  status: AdjudicationSessionStatus
  sourceVersionIds: string[]
  propositionCompletedAt: string | null
  recordVersion?: number
}

// 命题裁定项
export interface PropositionAdjudicationItem extends EntityBase {
  sessionId: string
  conflictType: PropositionConflictType
  candidateElementIds: string[]
  status?: string
}

// 按源版本分组的候选
export interface VersionElementGroup {
  versionId: string
  displayCode: string
  elements: Element[]
}

// J03 单项视图
export interface PropositionReviewItem {
  item: PropositionAdjudicationItem
  candidates: VersionElementGroup[]
  currentDecision: RefObject | null
}

// 关系（J10 编排所需，C 维护完整语义）
export interface CustomRelation extends EntityBase {
  ownerType: string
  ownerId: string
  relationCode: RelationType
  expression: string
  sourceRelationIds: string[]
}

// 关系参与者
export interface RelationParticipant extends EntityBase {
  relationId: string
  ordinal: number
  role: ParticipantRole
  targetKind: 'ELEMENT' | 'RELATION'
  targetId: string
}

// J09 自定义上下文
export interface CustomContext {
  session: AdjudicationSession
  itemKind: CustomItemKind
  item: PropositionAdjudicationItem | CustomRelation
  sourceVersions: AnnotationVersionRef[]
  candidateElements: Element[]
  candidateRelations: CustomRelation[]
  candidateParticipants: RelationParticipant[]
  documentTextVersion: DocumentTextVersionRef
  annotationScope: AnnotationScopeRef
  guideVersion: GuideVersion
  allowedActions: string[]
}

// J10 输入：自定义要素
export interface CustomElementInput {
  clientKey: string
  startOffset: number
  endOffset: number
  labelCode: string
  sourceElementIds: string[]
}

// J10 输入：自定义关系
export interface CustomRelationInput {
  clientKey: string
  relationCode: RelationType
  sourceRelationIds: string[]
  participants: CustomParticipantInput[]
}

// J10 输入：关系端点
export interface CustomParticipantInput {
  ordinal: number
  role: ParticipantRole
  targetKind: 'ELEMENT' | 'RELATION'
  targetClientKey: string
}

// J10 请求
export interface CustomAnnotationRequest {
  itemKind: CustomItemKind
  itemId: string
  customElements: CustomElementInput[]
  customRelations: CustomRelationInput[]
  reason: string
}

// clientKey → UUID 映射
export interface ClientIdMapping {
  clientKey: string
  targetKind: 'ELEMENT' | 'RELATION'
  id: string
}

// J10 返回
export interface CustomAnnotationResult {
  annotationId: string
  itemKind: CustomItemKind
  elementIds: string[]
  relationIds: string[]
  idMap: ClientIdMapping[]
  reason: string
}

// J12 请求：提交专家
export interface EscalateExpertRequest {
  expertUserId: string
  itemKind: 'PROPOSITION' | 'RELATION' | 'GENERAL'
  itemId: string | null
  question: string
}

// J12 返回
export interface EscalateExpertResult {
  review: RefObject
  issues: RefObject[]
  sessionRecordVersion: number
}

// J01 请求（D 维护，B 仅可能触发读取）
export interface StartSessionRequest {
  sourceVersionIds: string[]
}

export type { ParticipantRef }
