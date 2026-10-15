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
export function getExpertReviewDetail(reviewId: string): Promise<ExpertReviewDetail> {
  return get<ExpertReviewDetail>(`/expert-reviews/${reviewId}`)
}

// X03 专家接受复核申请（START：REQUESTED → IN_REVIEW）。需带当前 recordVersion。
export function startExpertReview(
  reviewId: string,
  ifMatch: number
): Promise<ExpertReview> {
  return post<ExpertReview>(
    `/expert-reviews/${reviewId}/transitions`,
    { action: 'START' },
    { headers: { 'If-Match': String(ifMatch) } }
  )
}

// X04 专家提交问题结论和指南依据。需带当前 issue 的 recordVersion。
export function submitIssueResolution(
  issueId: string,
  body: IssueResolutionRequest,
  ifMatch: number
): Promise<ExpertIssue> {
  return post<ExpertIssue>(
    `/expert-issues/${issueId}/resolutions`,
    body,
    { headers: { 'If-Match': String(ifMatch) } }
  )
}

// X05 确认专家复核完成（所有问题已 RESOLVED）。需带当前 review 的 recordVersion。
export function confirmExpertReview(
  reviewId: string,
  ifMatch: number
): Promise<ExpertReview> {
  return post<ExpertReview>(
    `/expert-reviews/${reviewId}/confirm`,
    { confirmed: true },
    { headers: { 'If-Match': String(ifMatch) } }
  )
}
