import { get, post } from '@/api/request'
import type { AdjudicationSession, AdjudicationDecision, PropositionAdjudicationItem, RelationAdjudicationItem, J01Request, J07Request, J11Request, PropositionReviewResult, DocumentTextVersion, AnnotationScope, GuideVersion, LabelDefinition } from '@/generated/api-client/types.gen'
import { getVersion } from '@/features/versions/api'
import type { SnapshotView, AnnotationVersion } from '@/features/versions/types'
export type { AdjudicationSession, AdjudicationDecision, PropositionAdjudicationItem, RelationAdjudicationItem, J07Request }
export interface Workspace { fixture?: boolean; documentTextVersion?: DocumentTextVersion; annotationScope?: AnnotationScope; guideVersion?: GuideVersion; labels?: LabelDefinition[]; session: AdjudicationSession; snapshots: SnapshotView[]; propositionItems: PropositionAdjudicationItem[]; relationItems: RelationAdjudicationItem[]; decisions: AdjudicationDecision[] }
const local = '/api/local/member-d/adjudication'
export const createSession = (caseId: string, body: J01Request, key: string) => post<AdjudicationSession>(`/task-documents/${caseId}/adjudication-sessions`, body, { headers: { 'Idempotency-Key': key } })
export const localSessions = (caseId: string) => get<{ items: AdjudicationSession[] }>(`${local}/cases/${caseId}/sessions`, undefined, { baseURL: '' })
export async function availableVersions(caseId: string) {
  const all: AnnotationVersion[] = []
  for (let page = 1; ; page++) {
    const result = await get<{ data: { items: AnnotationVersion[] }; meta: { total: number } }>(`/task-documents/${caseId}/annotation-versions`, { page, size: 100 }, { _withMeta: true })
    all.push(...result.data.items); if (all.length >= result.meta.total) return all
  }
}
export async function workspace(id: string): Promise<Workspace> {
  if (import.meta.env.DEV) return get<Workspace>(`${local}/sessions/${id}`, undefined, { baseURL: '' })
  const proposition = await get<PropositionReviewResult>(`/adjudication-sessions/${id}/proposition-items`)
  const session = proposition.session, snapshots = await Promise.all(session.sourceVersionIds.map(getVersion))
  const relationItems: RelationAdjudicationItem[] = []
  if (session.propositionCompletedAt) for (let page = 1; ; page++) {
    const result = await get<{ data: { items: RelationAdjudicationItem[] }; meta: { total: number } }>(`/adjudication-sessions/${id}/relation-items`, { page, size: 100 }, { _withMeta: true })
    if (result.data.items.some(i => !i.recordVersion || !i.candidateRelationIds || i.sessionId !== id)) throw new Error('关系候选完整读取尚未接入，请按裁定传输评审提案补齐 J06')
    relationItems.push(...result.data.items); if (relationItems.length >= result.meta.total) break
  }
  return { session, snapshots, propositionItems: proposition.items.map(i => i.item), relationItems, decisions: proposition.items.flatMap(i => i.currentDecision ? [i.currentDecision] : []) }
}
export const completePhase = (id: string, phase: 'propositions' | 'relations', key: string, version: number) => post<AdjudicationSession | { status: string }>(`/adjudication-sessions/${id}/${phase}/complete`, { confirmed: true }, { headers: { 'Idempotency-Key': key, 'If-Match': version } })
export const decide = (itemId: string, kind: 'PROPOSITION' | 'RELATION', body: J07Request, key: string, version: number) => {
  if (kind === 'PROPOSITION' && !import.meta.env.DEV) throw new Error('命题决定 J04 模型仍待确认')
  return post<{ itemId: string; status: string }>(kind === 'PROPOSITION' ? `${local}/proposition-items/${itemId}/fixture-decisions` : `/relation-adjudication-items/${itemId}/decisions`, body, { ...(kind === 'PROPOSITION' ? { baseURL: '' } : {}), headers: { 'Idempotency-Key': key, 'If-Match': version } })
}
export const returnSession = (id: string, body: J11Request, key: string, version: number) => post<{ status: string }>(`/adjudication-sessions/${id}/return`, body, { headers: { 'Idempotency-Key': key, 'If-Match': version } })
