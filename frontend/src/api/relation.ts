// 关系实例接口函数（R01–R04，接口归成员C维护）。契约见 openapi-ready.yaml。
// 注意：拦截器已解包统一信封，调用方**不要**再写 .data。
import { get, getPage, post, put, del } from './request'
import type { PageResult, ListResult } from '@/types/common'
import type {
  Relation,
  RelationCreateRequest,
  RelationCreateResult,
  RelationParticipantsRequest,
  RelationParticipantsResult,
  RelationDeleteRequest,
  RelationStatus,
  MockProposition
} from '@/types/relation'

// R01 读取草稿内的关系（分页）
export function listRelations(
  draftId: string,
  params?: { code?: string; page?: number; size?: number }
): Promise<PageResult<Relation>> {
  return getPage<Relation>(`/annotation-drafts/${draftId}/relations`, params)
}

// R02 建立关系；校验失败由后端抛 RELATION_CYCLE / INVALID_PARTICIPANT
export function createRelation(
  draftId: string,
  body: RelationCreateRequest
): Promise<RelationCreateResult> {
  return post<RelationCreateResult>(
    `/annotation-drafts/${draftId}/relations`,
    body
  )
}

// R03 整集替换参与项
export function replaceParticipants(
  relationId: string,
  body: RelationParticipantsRequest
): Promise<RelationParticipantsResult> {
  return put<RelationParticipantsResult>(
    `/relations/${relationId}/participants`,
    body
  )
}

// R04 删除关系（软删 status → REMOVED）。契约要求带删除原因，故用 DELETE + body。
export function deleteRelation(
  relationId: string,
  body: RelationDeleteRequest
): Promise<{ status: RelationStatus }> {
  return del<{ status: RelationStatus }>(`/relations/${relationId}`, {
    data: body
  })
}

// 【临时桥接】模拟命题。E01 等正式要素接口就绪后，删除本函数并改这里的唯一调用点即可。
// 后端对应 MockPropositionController（已在 Javadoc 标注"不是契约接口"）。
export async function listMockPropositions(
  draftId: string
): Promise<MockProposition[]> {
  const r = await get<ListResult<MockProposition>>(
    `/annotation-drafts/${draftId}/mock-propositions`
  )
  return r.items
}
