// 论证图接口函数（GR01–GR03，接口归成员C维护）。契约见 openapi-ready.yaml。
// 注意：拦截器已解包统一信封，调用方**不要**再写 .data。
import { get, post } from './request'
import type {
  GraphView,
  GraphValidateRequest,
  GraphValidateResult,
  LayoutCreateRequest,
  LayoutCreateResult
} from '@/types/graph'
import type { RelationOwnerType } from '@/types/relation'

// GR01 读取某归属对象（草稿/标注版本/裁定自定义/最终版本）的论证图
export function getGraph(
  ownerType: RelationOwnerType,
  ownerId: string,
  layoutVersion?: number
): Promise<GraphView> {
  return get<GraphView>(`/annotation-owners/${ownerType}/${ownerId}/graph`, {
    layoutVersion
  })
}

// GR02 校验草稿内的一组命题与关系能否构成合法图示
export function validateGraph(
  draftId: string,
  body: GraphValidateRequest
): Promise<GraphValidateResult> {
  return post<GraphValidateResult>(
    `/annotation-drafts/${draftId}/graph/validate`,
    body
  )
}

// GR03 保存布局（只写坐标，不改逻辑；同图自增 layoutVersion）
export function saveGraphLayout(
  graphId: string,
  body: LayoutCreateRequest
): Promise<LayoutCreateResult> {
  return post<LayoutCreateResult>(`/graphs/${graphId}/layouts`, body)
}
