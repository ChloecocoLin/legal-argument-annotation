// 关系裁定接口函数（接口归成员C）。契约见 openapi-ready.yaml 的 J06 与 docs/关系裁定接口约定.md。
// 注意：拦截器已解包统一信封，调用方**不要**再写 .data。
import { getPage, post } from './request'
import type { PageResult } from '@/types/common'
import type {
  RelationAdjudicationItem,
  RelationAdjudicationResult,
  RelationConflictType,
  RelationResultRequest
} from '@/types/relationAdjudication'

// J06 列出关系差异和一致项（分页）。命题阶段未冻结时后端返回 PROPOSITIONS_NOT_COMPLETE(409)。
export function listRelationItems(
  sessionId: string,
  params?: {
    status?: string
    conflictType?: RelationConflictType
    page?: number
    size?: number
  }
): Promise<PageResult<RelationAdjudicationItem>> {
  return getPage<RelationAdjudicationItem>(
    `/adjudication-sessions/${sessionId}/relation-items`,
    params
  )
}

// 【非契约接口】生成关系裁定结果 —— 只读试算，不落库。
// 契约把裁定**写入**归 D 的 J07/J08；本接口是 C 的交付物本身
// （分工.md §四-8「关系裁定结果 + 正式论证图」）。D 的就绪后语义算法不变，只换决定的来源。
export function resolveRelationResult(
  sessionId: string,
  body: RelationResultRequest
): Promise<RelationAdjudicationResult> {
  return post<RelationAdjudicationResult>(
    `/adjudication-sessions/${sessionId}/relation-result`,
    body
  )
}
