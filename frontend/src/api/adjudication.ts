// 裁定模块接口函数。
// 成员B 后端负责：J03 / J09 / J10 / J12。
// J01 / J02 / J05 / J11 后端归成员D，B 前端需要调用，故一并封装。
import { get, post } from './request'
import type { PageQuery, ListResult } from '@/types/common'
import type {
  AdjudicationSession,
  PropositionReviewItem,
  CustomContext,
  CustomAnnotationRequest,
  CustomAnnotationResult,
  EscalateExpertRequest,
  EscalateExpertResult,
  StartSessionRequest
} from '@/types/adjudication'

// J01 读取或启动裁定会话（后端归 D）
export function startAdjudicationSession(
  taskDocumentId: string,
  body: StartSessionRequest
): Promise<AdjudicationSession> {
  return post<AdjudicationSession>(
    `/task-documents/${taskDocumentId}/adjudication-sessions`,
    body
  )
}

// J02 读取会话状态（后端归 D，B 只读）
export function getAdjudicationSession(
  sessionId: string
): Promise<AdjudicationSession> {
  return get<AdjudicationSession>(`/adjudication-sessions/${sessionId}`)
}

// J03 列出命题项（含一致项、候选分组、当前决定）
export async function listPropositionItems(
  sessionId: string,
  params?: { status?: string; conflictType?: string }
): Promise<PropositionReviewItem[]> {
  const r = await get<ListResult<PropositionReviewItem>>(
    `/adjudication-sessions/${sessionId}/proposition-items`,
    params
  )
  return r.items
}

// J04 命题逐项决定：当前 PENDING_MODEL，未落就绪接口，暂不实现写操作。
// 前端“采用 A / B / 自定义”按钮在接口就绪前显示未接入状态，不伪造保存成功。

// J05 确认命题阶段完成（后端归 D）
export function completePropositions(
  sessionId: string
): Promise<AdjudicationSession> {
  return post<AdjudicationSession>(
    `/adjudication-sessions/${sessionId}/propositions/complete`,
    { confirmed: true }
  )
}

// J09 读取自定义编辑上下文
export function getCustomContext(
  sessionId: string,
  params: { itemKind: 'PROPOSITION' | 'RELATION'; itemId: string }
): Promise<CustomContext> {
  return get<CustomContext>(
    `/adjudication-sessions/${sessionId}/custom-context`,
    params
  )
}

// J10 保存自定义命题/关系快照（B 唯一负责编排）
export function saveCustomAnnotation(
  sessionId: string,
  body: CustomAnnotationRequest
): Promise<CustomAnnotationResult> {
  return post<CustomAnnotationResult>(
    `/adjudication-sessions/${sessionId}/custom-annotations`,
    body
  )
}

// J11 退回标注员补正（后端归 D）
export function returnSession(
  sessionId: string,
  body: { targetAssignmentIds: string[]; reason: string }
): Promise<AdjudicationSession> {
  return post<AdjudicationSession>(
    `/adjudication-sessions/${sessionId}/return`,
    body
  )
}

// J12 提交疑难问题给专家
export function escalateToExpert(
  sessionId: string,
  body: EscalateExpertRequest
): Promise<EscalateExpertResult> {
  return post<EscalateExpertResult>(
    `/adjudication-sessions/${sessionId}/expert-reviews`,
    body
  )
}

export type { PageQuery }
