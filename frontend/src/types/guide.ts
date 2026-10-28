// 指南库 G 系列类型。依据契约 6.2 与 openapi-ready.yaml。
import type { EntityBase } from './common'
import type { RelationType } from './common'

// Guide 状态
export type GuideStatus = 'ACTIVE' | 'ARCHIVED'

// GuideVersion 状态
export type GuideVersionStatus =
  | 'DRAFT'
  | 'PENDING_REVIEW'
  | 'PUBLISHED'
  | 'ARCHIVED'

// 标签类别
export type LabelCategory = 'PROPOSITION' | 'NON_PROPOSITION'

// 版本审核动作
export type ReviewAction = 'SUBMIT_REVIEW' | 'RETURN' | 'PUBLISH'

// 定义变更类型
export type ChangeType = 'ADDED' | 'REMOVED' | 'MODIFIED'

// 指南容器
export interface Guide extends EntityBase {
  title: string
  description: string | null
  status: GuideStatus
  recordVersion?: number
}

// G01 列表项：指南字段 + 最新版本展示字段（后端扁平对象）
export interface GuideListItem {
  id: string
  title: string
  description: string | null
  guideStatus: GuideStatus
  versionId: string | null
  versionCode: string | null
  versionStatus: GuideVersionStatus | null
  fileName: string | null
  fileSize: number | null
  pageCount: number | null
  uploadedBy: string | null
  uploadedAt: string | null
  publishedAt: string | null
}

// 指南版本
export interface GuideVersion extends EntityBase {
  guideId: string
  versionCode: string
  status: GuideVersionStatus
  attachmentFileId: string | null
  fileName: string | null
  fileSize: number | null
  pageCount: number | null
  uploadedBy: string | null
  publishedAt: string | null
  recordVersion?: number
}

// 指南 + 版本列表
export interface GuideSummary {
  guide: Guide
  versions: GuideVersion[]
}

// G03 返回（含版本 id 列表）
export interface GuideDetail {
  id: string
  versionIds: string[]
}

// 标签定义
export interface LabelDefinition extends EntityBase {
  guideVersionId: string
  code: string
  name: string
  category: LabelCategory
  baseType: string | null
  sortOrder: number
  definition: string
  recognitionRules: string[]
  signalPhrases: string[]
  examples: string[]
  notes: string | null
  recordVersion?: number
}

// 新增标签（G13）
export interface LabelCreate {
  code: string
  name: string
  category: LabelCategory
  baseType: string | null
  sortOrder: number
  definition: string
  recognitionRules: string[]
  signalPhrases: string[]
  examples: string[]
  notes?: string | null
}

// 更新标签（G14），所有字段可选
export type LabelPatch = Partial<LabelCreate>

// 关系定义（G07 由成员C维护，B 只读）
export interface RelationDefinition extends EntityBase {
  guideVersionId: string
  code: RelationType
  name: string
  definition: string
  directed: boolean
  expressionTemplate: string
  participantRule: string
  diagramRule: string
  // 以下四项为 G07 契约字段，由成员C于关系定义扩列时补齐（S/A/M 二元有向，J/I 无向 ≥2）
  directionRule: string
  minParticipants: number
  maxParticipants: number | null
  sortOrder: number
  recordVersion?: number
}

// 新增/更新关系定义（G15/G16，由成员C维护）。六项文本字段，更新时全部可选。
export interface RelationDefinitionUpsert {
  code?: RelationType
  definition?: string
  directionRule?: string
  expressionTemplate?: string
  participantRule?: string
  diagramRule?: string
}

// 定义变更（G11 版本对比）
export interface DefinitionChange<T> {
  code: string
  changeType: ChangeType
  before: T | null
  after: T | null
  changedFields: string[]
}

// G11 返回
export interface GuideCompareResult {
  labelChanges: DefinitionChange<LabelDefinition>[]
  relationChanges: DefinitionChange<RelationDefinition>[]
}

// G02 创建指南请求
export interface GuideCreateRequest {
  title: string
  description?: string | null
}

// G04 创建版本请求
export interface GuideVersionCreateRequest {
  versionCode: string
  attachmentFileId?: string | null
}

// G08 更新附件请求
export interface GuideVersionAttachmentPatch {
  attachmentFileId: string
}

// G09 审核请求
export interface ReviewDecisionRequest {
  action: ReviewAction
  reason: string
}

// G10 归档请求
export interface ArchiveRequest {
  reason: string
}
