// 指南库接口函数。契约编号见注释。
import { get, getPage, post, patch, del } from './request'
import type { PageResult, ListResult } from '@/types/common'
import type {
  Guide,
  GuideListItem,
  GuideVersion,
  GuideSummary,
  LabelDefinition,
  LabelCreate,
  LabelPatch,
  RelationDefinition,
  GuideCompareResult,
  GuideCreateRequest,
  GuideVersionCreateRequest,
  GuideVersionAttachmentPatch,
  ReviewDecisionRequest,
  ArchiveRequest
} from '@/types/guide'

// G01 检索指南（分页）
export function listGuides(params?: {
  q?: string
  status?: string
  versionStatus?: string
  page?: number
  size?: number
}): Promise<PageResult<GuideListItem>> {
  return getPage<GuideListItem>('/guides', params)
}

// G02 创建指南容器
export function createGuide(body: GuideCreateRequest): Promise<Guide> {
  return post<Guide>('/guides', body)
}

// G03 查看指南及版本列表
export function getGuide(guideId: string): Promise<GuideSummary> {
  return get<GuideSummary>(`/guides/${guideId}`)
}

// G04 创建指南草稿版本
export function createGuideVersion(
  guideId: string,
  body: GuideVersionCreateRequest
): Promise<GuideVersion> {
  return post<GuideVersion>(`/guides/${guideId}/versions`, body)
}

// G05 查看版本详情
export function getGuideVersion(guideVersionId: string): Promise<GuideVersion> {
  return get<GuideVersion>(`/guide-versions/${guideVersionId}`)
}

// G06 读取标签定义（后端直接返回数组）
export function listLabels(
  guideVersionId: string,
  params?: { category?: string; q?: string }
): Promise<LabelDefinition[]> {
  return get<LabelDefinition[]>(
    `/guide-versions/${guideVersionId}/labels`,
    params
  )
}

// G07 读取关系定义（接口归成员C维护，B 只读）
export async function listRelationDefinitions(
  guideVersionId: string,
  params?: { code?: string }
): Promise<RelationDefinition[]> {
  const r = await get<ListResult<RelationDefinition>>(
    `/guide-versions/${guideVersionId}/relations`,
    params
  )
  return r.items
}

// G08 更新版本附件（仅 DRAFT）
export function updateVersionAttachment(
  guideVersionId: string,
  body: GuideVersionAttachmentPatch
): Promise<GuideVersion> {
  return patch<GuideVersion>(`/guide-versions/${guideVersionId}`, body)
}

// G09 提交审核 / 退回 / 发布
export function submitReviewDecision(
  guideVersionId: string,
  body: ReviewDecisionRequest
): Promise<GuideVersion> {
  return post<GuideVersion>(
    `/guide-versions/${guideVersionId}/review-decisions`,
    body
  )
}

// G10 归档版本
export function archiveGuideVersion(
  guideVersionId: string,
  body: ArchiveRequest
): Promise<GuideVersion> {
  return post<GuideVersion>(
    `/guide-versions/${guideVersionId}/archive`,
    body
  )
}

// G11 比较两个版本
export function compareGuideVersions(
  leftId: string,
  rightId: string
): Promise<GuideCompareResult> {
  return get<GuideCompareResult>('/guide-versions/compare', { leftId, rightId })
}

// G13 新增草稿标签
export function createLabel(
  guideVersionId: string,
  body: LabelCreate
): Promise<LabelDefinition> {
  return post<LabelDefinition>(
    `/guide-versions/${guideVersionId}/labels`,
    body
  )
}

// G14 局部更新草稿标签
export function updateLabel(
  labelDefinitionId: string,
  body: LabelPatch
): Promise<LabelDefinition> {
  return patch<LabelDefinition>(
    `/label-definitions/${labelDefinitionId}`,
    body
  )
}

// G17 删除草稿标签（无引用时）
export function deleteLabel(labelDefinitionId: string): Promise<void> {
  return del<void>(`/label-definitions/${labelDefinitionId}`)
}
