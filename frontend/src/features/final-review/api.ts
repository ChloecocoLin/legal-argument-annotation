import { get, post } from '@/api/request'
import type { Element, Relation, RelationParticipant, AdjudicationSession, GuideVersion } from '@/generated/api-client/types.gen'
export interface FinalVersion {
  correctionSessionId?: string; id: string; taskDocumentId: string; sessionId: string; versionNumber: number
  status: 'DRAFT' | 'IN_REVIEW' | 'APPROVED' | 'IN_DATABASE'
  documentTextVersionId: string; annotationScopeId: string; guideVersionId: string
  elementIds: string[]; relationIds: string[]; graphId: string
  provenance: Provenance[]; approvedById: string | null; approvedAt: string | null; committedAt: string | null
  createdAt: string; createdById: string; updatedAt: string; updatedById: string; recordVersion: number
}
export interface Provenance { resultKind: 'ELEMENT' | 'RELATION' | 'EXCLUDED_ELEMENT' | 'EXCLUDED_RELATION'; resultId: string | null; sourceVersionIds: string[]; sourceObjectIds: string[]; decisionId: string | null; expertIssueId: string | null }
export interface FinalSnapshot { version: FinalVersion; elements: Element[]; relations: Relation[]; participants: RelationParticipant[]; graph: { id: string; elementIds: string[]; relationIds: string[]; status: string } }
export interface FinalWorkspace {
  graphView: import('@/types/graph').GraphView; fixture?: boolean; snapshot: FinalSnapshot; session: AdjudicationSession
  quality: { checks: { code: string; message: string; passed: boolean }[]; issues: { code: string; message: string; blocking: boolean }[]; healthy: boolean; formalReady: boolean; mode: string; canSubmit: boolean; canApprove: boolean }
  document: { id: string; title: string; caseNumber?: string }; guideVersion: GuideVersion
  summary: { propositions: number; relations: number; expertReviews: number; excluded: number }
  history: { id: string; operation: string; createdAt: string; createdById: string; sequenceNumber: number; command: { reason?: string }; before: Partial<FinalVersion>; after: FinalVersion }[]
  latest: boolean
  sources?: { id: string; displayCode: string }[]
}
const local = '/api/local/member-d/final-review'
export async function getFinal(id: string): Promise<FinalWorkspace> {
  const data = await get<{ id: string; status: string; elementIds: string[]; provenanceCount: number } | FinalWorkspace>(`/final-annotation-versions/${id}`)
  if (import.meta.env.DEV) return get<FinalWorkspace>(`${local}/versions/${id}`, undefined, { baseURL: '' })
  if (!('snapshot' in data) || !data.snapshot.version.recordVersion) throw new Error('最终审查完整读取尚未接入，请按待评审传输提案补齐 FV02；当前摘要不提供写入所需的版本号')
  return data
}
export const listLocalFinals = (sessionId: string) => get<{ items: FinalVersion[]; nextVersionNumber: number }>(`${local}/sessions/${sessionId}/versions`, undefined, { baseURL: '' })
export const createFinal = (sessionId: string, number: number, key: string) => post<{ id: string; status: string }>(`/adjudication-sessions/${sessionId}/final-versions`, { versionNumber: number }, { headers: { 'Idempotency-Key': key } })
export type ReviewAction = { operation: 'review'; body: { action: 'SUBMIT' } } | { operation: 'return'; body: { reason: string } } | { operation: 'approve' | 'commit'; body: { confirmed: true } }
export const transitionFinal = (id: string, command: ReviewAction, key: string, version: number) => {
  if (command.operation === 'commit' && !import.meta.env.DEV) throw new Error('入库试行仅在本地联调开放，公共模型仍待团队评审')
  return post<{ status: string; issues?: unknown[] }>(`/final-annotation-versions/${id}/${command.operation}`, command.body, { headers: { 'Idempotency-Key': key, 'If-Match': version } })
}

export const openCorrection = (id: string, reason: string, key: string, version: number) => post<{sessionId: string}>(`/api/local/member-d/final-versions/${id}/correction-rounds`, {reason}, {baseURL:'',headers:{'Idempotency-Key':key,'If-Match':version}})
