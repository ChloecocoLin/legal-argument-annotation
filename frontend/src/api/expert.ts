// 专家复核接口函数。X01–X05 后端均由成员B负责。
import { get, getPage, post } from './request'
import type { PageResult } from '@/types/common'
import type {
  ExpertReview,
  ExpertIssue,
  ExpertReviewSummary,
  ExpertReviewDetail,
  IssueResolutionRequest
} from '@/types/expert'

// X01 列出本人专家待办（分页）
export function listMyExpertReviews(params?: {
  status?: string
  q?: string
  page?: number
  size?: number
}): Promise<PageResult<ExpertReviewSummary>> {
  return getPage<ExpertReviewSummary>('/expert-reviews', params)
}

// X02 读取问题、原文与指南依据
export function getExpertReviewDetail(
  reviewId: string
): Promise<ExpertReviewDetail> {
  return get<ExpertReviewDetail>(`/expert-reviews/${reviewId}`)
}

// X03 专家接受复核申请（START：REQUESTED → IN_REVIEW）
export function startExpertReview(reviewId: string): Promise<ExpertReview> {
  return post<ExpertReview>(`/expert-reviews/${reviewId}/transitions`, {
    action: 'START'
  })
}

// X04 专家提交问题结论和指南依据
export function submitIssueResolution(
  issueId: string,
  body: IssueResolutionRequest
): Promise<ExpertIssue> {
  return post<ExpertIssue>(`/expert-issues/${issueId}/resolutions`, body)
}

// X05 确认专家复核完成（所有问题已 RESOLVED）
export function confirmExpertReview(reviewId: string): Promise<ExpertReview> {
  return post<ExpertReview>(`/expert-reviews/${reviewId}/confirm`, {
    confirmed: true
  })
}
