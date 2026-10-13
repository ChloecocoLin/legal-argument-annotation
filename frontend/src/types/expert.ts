// 专家复核 X 系列类型（X01–X05 均由成员B负责）。依据契约 6.5。
import type { EntityBase } from './common'
import type { Element } from './element'
import type { CustomRelation, RelationParticipant } from './adjudication'

// 复核单状态
export type ExpertReviewStatus = 'REQUESTED' | 'IN_REVIEW' | 'CONFIRMED'

// 问题状态
export type ExpertIssueStatus = 'OPEN' | 'RESOLVED'

// 问题对应项类型
export type IssueItemKind = 'PROPOSITION' | 'RELATION' | 'GENERAL'

// 专家复核单
export interface ExpertReview extends EntityBase {
  sessionId: string
  expertUserId: string
  expertTaskMemberId: string
  requestedById: string
  status: ExpertReviewStatus
}

// 专家问题
export interface ExpertIssue extends EntityBase {
  expertReviewId: string
  sessionId: string
  itemKind: IssueItemKind
  itemId: string | null
  question: string
  status: ExpertIssueStatus
  resolution: string | null
  guideBasis: string | null
  resolvedById: string | null
  resolvedAt: string | null
}

// X01 摘要
export interface ExpertReviewSummary {
  review: ExpertReview
  taskId: string
  taskName: string
  documentId: string
  documentTitle: string
  guideVersionId: string
  issueCount: number
  openIssueCount: number
}

// 文本摘录
export interface TextExcerpt {
  documentTextVersionId: string
  annotationScopeId: string
  startOffset: number
  endOffset: number
  text: string
}

// 候选分组
export interface ExpertCandidateGroup {
  versionId: string
  displayCode: string
  elements: Element[]
  relations: CustomRelation[]
  participants: RelationParticipant[]
}

// 问题上下文
export interface ExpertIssueContext {
  issueId: string
  itemKind: IssueItemKind
  item: unknown | null
  textExcerpts: TextExcerpt[]
  candidateGroups: ExpertCandidateGroup[]
}

// X02 详情
export interface ExpertReviewDetail {
  review: ExpertReview
  guideVersionId: string
  issues: ExpertIssue[]
  contexts: ExpertIssueContext[]
}

// X03 请求
export interface ReviewTransitionRequest {
  action: 'START'
}

// X04 请求
export interface IssueResolutionRequest {
  resolution: string
  guideBasis: string
}

// X05 请求
export interface ReviewConfirmRequest {
  confirmed: boolean
}
