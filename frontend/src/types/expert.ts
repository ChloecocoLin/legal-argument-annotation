// 专家复核 X 系列类型（X01–X05 均由成员B负责）。对齐后端实际返回。
import type { EntityBase } from './common'
import type { PropositionAdjudicationItem } from './adjudication'

// 复核单状态
export type ExpertReviewStatus = 'REQUESTED' | 'IN_REVIEW' | 'CONFIRMED'

// 问题状态
export type ExpertIssueStatus = 'OPEN' | 'RESOLVED'

// 问题对应项类型
export type IssueItemKind = 'PROPOSITION' | 'RELATION' | 'GENERAL'

// 专家复核单
export interface ExpertReview extends EntityBase {
  sessionId: string
  expertTaskMemberId: string
  requestedById: string
  status: ExpertReviewStatus
  openedAt: string | null
  confirmedAt: string | null
  recordVersion: number
}

// 专家问题
export interface ExpertIssue extends EntityBase {
  expertReviewId: string
  itemKind: IssueItemKind
  itemId: string | null
  question: string
  status: ExpertIssueStatus
  resolution: string | null
  guideBasis: string | null
  resolvedAt: string | null
  recordVersion: number
}

// X01 摘要
export interface ExpertReviewSummary {
  review: ExpertReview
  taskId: string
  taskName: string
  documentId: string
  documentTitle: string
  guideVersionId: string | null
  issueCount: number
  openIssueCount: number
}

// 候选分组（按源版本，演示返回文本数组）
export interface ExpertCandidateGroup {
  versionId: string
  displayCode: string
  texts: string[]
}

// 问题上下文
export interface ExpertIssueContext {
  issueId: string
  itemKind: IssueItemKind
  item: PropositionAdjudicationItem | null
  textExcerpts: string[]
  candidateGroups: ExpertCandidateGroup[]
}

// X02 详情
export interface ExpertReviewDetail {
  review: ExpertReview
  guideVersionId: string | null
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
