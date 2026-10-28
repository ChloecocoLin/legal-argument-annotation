// 关系实例类型（R01–R04，归成员C）。依据《核心标注与裁定数据模型》§1 与 openapi-ready.yaml。
import type { EntityBase, RelationType } from './common'

// 参与项角色：有向关系用 SOURCE/TARGET，无向关系（J/I）只用 MEMBER
export type ParticipantRole = 'SOURCE' | 'TARGET' | 'MEMBER'

// 参与项目标种类：命题要素 / 关系整体（嵌套）
export type ParticipantTargetKind = 'ELEMENT' | 'RELATION'

export type RelationStatus = 'ACTIVE' | 'REMOVED'

// 归属：标注草稿 / 标注版本 / 裁定者自定义 / 最终版本
export type RelationOwnerType =
  | 'DRAFT'
  | 'ANNOTATION_VERSION'
  | 'ARBITER_CUSTOM'
  | 'FINAL_VERSION'

// 参与项出参。displayNo/labelCode/text 是 ELEMENT 目标的补充信息，用于渲染与原文定位。
export interface RelationParticipant {
  id?: string
  ordinal: number
  role: ParticipantRole
  targetKind: ParticipantTargetKind
  targetId: string
  displayNo?: string | null
  labelCode?: string | null
  text?: string | null
}

// 关系出参。注意：**没有文本表达式字段**（契约明令关系不存表达式），
// expression 由后端从参与项递归生成，仅供展示。
export interface Relation extends EntityBase {
  ownerType: RelationOwnerType
  ownerId: string
  relationDefinitionId: string
  relationCode: RelationType
  name: string | null
  directed: boolean
  status: RelationStatus
  sourceRelationIds: string[]
  participants: RelationParticipant[]
  expression: string
}

// R02/R03 的参与项入参
export interface ParticipantInput {
  ordinal: number
  role: ParticipantRole
  targetKind: ParticipantTargetKind
  targetId: string
}

export interface RelationCreateRequest {
  relationCode: RelationType
  participants: ParticipantInput[]
}

export interface RelationParticipantsRequest {
  participants: ParticipantInput[]
}

export interface RelationDeleteRequest {
  reason: string
}

export interface RelationCreateResult {
  id: string
  directed: boolean
}

export interface RelationParticipantsResult {
  relationId: string
  participantCount: number
}

// 校验/审查问题项。code 与后端 ErrorCode 同名。
export interface RelationIssue {
  code: string
  level: 'ERROR' | 'WARNING'
  message: string
  relationId?: string | null
  participantOrdinal?: number | null
  /**
   * 图示审查分类（后端 AuditCategory）。只有「图示审查」需要它，其余出口可能为 null；
   * 界面把 null 归入「其他结构问题」。取值见 shared/graphAudit.ts 的 AUDIT_CATEGORIES。
   */
  category?: string | null
}

// 【临时】模拟命题。替代 A/B 的正式要素接口（E01…），就绪后本类型与
// api/relation.ts 的 listMockPropositions 一并删除。
export interface MockProposition {
  id: string
  snapshotId: string
  labelCode: string
  displayNo: string | null
  proposition: boolean
  text: string
}
