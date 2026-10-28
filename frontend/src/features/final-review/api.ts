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
  quality: { trialReady?: boolean; canApproveTrial?:boolean; trialEvidence?:{mode:string;ready:boolean;proof?:Record<string,string>}; checks: { code: string; message: string; passed: boolean }[]; issues: { code: string; message: string; blocking: boolean }[]; healthy: boolean; formalReady: boolean; mode: string; canSubmit: boolean; canApprove: boolean }
  document: { id: string; title: string; caseNumber?: string }; guideVersion: GuideVersion
  summary: { propositions: number; relations: number; expertReviews: number; excluded: number }
  history: { id: string; operation: string; createdAt: string; createdById: string; sequenceNumber: number; command: { reason?: string }; before: Partial<FinalVersion>; after: FinalVersion }[]
  latest: boolean
  sources?: { id: string; displayCode: string }[]
}
export const getFinal = (id:string) => get<FinalWorkspace>(`/integration/final-annotation-versions/${id}/workspace`)
export const listLocalFinals = (sessionId:string) => get<{items:FinalVersion[];nextVersionNumber:number}>(`/integration/adjudication-sessions/${sessionId}/final-versions`)
export const createFinal = (sessionId: string, number: number, key: string) => post<{ id: string; status: string }>(`/adjudication-sessions/${sessionId}/final-versions`, { versionNumber: number }, { headers: { 'Idempotency-Key': key } })
export type ReviewAction = { operation: 'review'; body: { action: 'SUBMIT' } } | { operation: 'return'; body: { reason: string } } | { operation: 'approve' | 'commit'; trial?:boolean; body: { confirmed: true } }
export const transitionFinal = (id: string, command: ReviewAction, key: string, version: number) => {
  if (command.operation === 'commit' && !import.meta.env.DEV) throw new Error('入库试行仅在本地联调开放，公共模型仍待团队评审')
  if ((command.operation==='approve'||command.operation==='commit')&&command.trial) return post<{status:string}>(`/api/local/member-d/final-review/versions/${id}/trial-${command.operation}`,command.body,{baseURL:'',headers:{'Idempotency-Key':key,'If-Match':version}})
  return post<{ status: string; issues?: unknown[] }>(`/final-annotation-versions/${id}/${command.operation}`, command.body, { headers: { 'Idempotency-Key': key, 'If-Match': version } })
}

export const openCorrection = (id: string, reason: string, key: string, version: number) => post<{sessionId: string}>(`/api/local/member-d/final-versions/${id}/correction-rounds`, {reason}, {baseURL:'',headers:{'Idempotency-Key':key,'If-Match':version}})
