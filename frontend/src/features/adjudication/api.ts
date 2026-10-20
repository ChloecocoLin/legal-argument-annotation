import { get, post } from '@/api/request'
import type { AdjudicationSession, AdjudicationDecision, PropositionAdjudicationItem, RelationAdjudicationItem, J01Request, J07Request, J11Request, PropositionReviewResult, DocumentTextVersion, AnnotationScope, GuideVersion, LabelDefinition } from '@/generated/api-client/types.gen'
import type { SnapshotView, AnnotationVersion } from '@/features/versions/types'
export type { AdjudicationSession, AdjudicationDecision, PropositionAdjudicationItem, RelationAdjudicationItem, J07Request }
export interface Workspace { fixture?: boolean; decisionMode?:string; experts?:{id:string;name:string}[]; expertReviews?:{id:string;status:string;expertUserId:string}[]; documentTextVersion?: DocumentTextVersion; annotationScope?: AnnotationScope; guideVersion?: GuideVersion; labels?: LabelDefinition[]; session: AdjudicationSession; snapshots: SnapshotView[]; propositionItems: PropositionAdjudicationItem[]; relationItems: RelationAdjudicationItem[]; decisions: AdjudicationDecision[] }
const local = '/api/local/member-d/adjudication'
export const createSession = (caseId: string, body: J01Request, key: string) => post<AdjudicationSession>(`/task-documents/${caseId}/adjudication-sessions`, body, { headers: { 'Idempotency-Key': key } })
export const localSessions = (caseId: string) => get<{ items: AdjudicationSession[] }>(`/integration/cases/${caseId}/adjudication-sessions`)
export async function availableVersions(caseId: string) {
  const all: AnnotationVersion[] = []
  for (let page = 1; ; page++) {
    const result = await get<{ data: { items: AnnotationVersion[] }; meta: { total: number } }>(`/task-documents/${caseId}/annotation-versions`, { page, size: 100 }, { _withMeta: true })
    all.push(...result.data.items); if (all.length >= result.meta.total) return all
  }
}
export async function workspace(id:string):Promise<Workspace>{
 const result=await get<Workspace>(`/integration/adjudication-sessions/${id}/workspace`)
 const query=await get<PropositionReviewResult>(`/adjudication-sessions/${id}/proposition-items`)
 if(result.session.recordVersion!==query.session.recordVersion)throw new Error('会话在读取期间变化，请刷新')
 result.propositionItems=query.items.map(i=>i.item)
 return result
}
export const completePhase = (id: string, phase: 'propositions' | 'relations', key: string, version: number) => post<AdjudicationSession | { status: string }>(`/adjudication-sessions/${id}/${phase}/complete`, { confirmed: true }, { headers: { 'Idempotency-Key': key, 'If-Match': version } })
export const decide = (itemId: string, kind: 'PROPOSITION' | 'RELATION', body: J07Request, key: string, version: number) => {
  if (kind === 'PROPOSITION' && !import.meta.env.DEV) throw new Error('命题决定 J04 模型仍待确认')
  return post<{ itemId: string; status: string }>(kind === 'PROPOSITION' ? `${local}/proposition-items/${itemId}/trial-decisions` : `/relation-adjudication-items/${itemId}/decisions`, body, { ...(kind === 'PROPOSITION' ? { baseURL: '' } : {}), headers: { 'Idempotency-Key': key, 'If-Match': version } })
}
export const returnSession = (id: string, body: J11Request, key: string, version: number) => post<{ status: string }>(`/adjudication-sessions/${id}/return`, body, { headers: { 'Idempotency-Key': key, 'If-Match': version } })
