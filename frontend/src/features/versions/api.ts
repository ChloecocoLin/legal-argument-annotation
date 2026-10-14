import { get, post } from '@/api/request'
import type { A04Request, A05Request, T13Success, AnnotationVersion } from '@/generated/api-client/types.gen'
import type { SnapshotView, VersionList, LocalContext, SubmissionPreview } from './types'
import { useUserStore, type UserRole } from '@/stores/user'

export const getProgress = (taskId: string) => get<T13Success['data']>(`/tasks/${taskId}/progress`)
export const listVersions = (caseId: string, page = 1, status?: string) => get<VersionList>(
  `/task-documents/${caseId}/annotation-versions`, { page, size: 10, ...(status ? { status } : {}) }, { _withMeta: true })
export const getVersion = (id: string) => get<SnapshotView>(`/annotation-versions/${id}`)
export const submitDraft = (id: string, body: A04Request, key: string, version: number) => post<AnnotationVersion>(
  `/annotation-drafts/${id}/submit`, body, { headers: { 'Idempotency-Key': key, 'If-Match': version } })
export const withdrawVersion = (id: string, body: A05Request, key: string, version: number) => post<AnnotationVersion>(
  `/annotation-versions/${id}/withdraw`, body, { headers: { 'Idempotency-Key': key, 'If-Match': version } })

// Explicit development adapter, never silently substituted for public APIs.
export const getLocalContext = async () => {
  const result = await get<LocalContext>('/api/local/member-d/context', undefined, { baseURL: '' })
  const user = useUserStore(); user.id = result.actorId; user.name = result.actorName
  const roles: Record<string, UserRole> = { ADMIN: 'admin', TASK_CREATOR: 'creator', ANNOTATOR: 'annotator', ARBITER: 'arbiter', EXPERT: 'expert', TEACHER: 'teacher', STUDENT: 'student' }
  user.roles = result.roles.map(role => roles[role]).filter((role): role is UserRole => !!role)
  return result
}
export const getLocalPreview = (id: string) => get<SubmissionPreview>(`/api/local/member-d/drafts/${id}`, undefined, { baseURL: '' })

export function errorMessage(error: unknown): string {
  const e = error as { response?: { data?: { error?: { message?: string } } }; message?: string }
  const message = e.response?.data?.error?.message || e.message || '加载失败，请重试'
  const messages: Record<string, string> = { FORBIDDEN: '没有查看或操作此数据的权限', UNAUTHENTICATED: '请先选择有效身份或登录', BLIND_SCOPE_DENIED: '只能查看本人版本或负责裁定的案例', NOT_FOUND: '找不到所请求的数据' }
  return messages[message] || message
}
export function errorCode(error: unknown): string | undefined {
  return (error as { response?: { data?: { error?: { code?: string } } } }).response?.data?.error?.code
}
export const formatTime = (value: string) => new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
export const statusName = (status: string) => ({ SUBMITTED: '已提交', WITHDRAWN: '已撤回', UNDER_ADJUDICATION: '裁定中', ADJUDICATED: '已裁定', ACTIVE: '草稿', IN_PROGRESS: '标注中', COMPLETED: '已完成', ANNOTATING: '标注中', DRAFT: '待发布' }[status] || status)

/** One logical action keeps its key/body/version across network retries. Refresh starts a new action. */
export function mutationAttempt<T>() {
  let attempt: { key: string; body: T; version: number } | undefined
  return {
    get(body: T, version: number) {
      if (!attempt) attempt = { key: crypto.randomUUID(), body: structuredClone(body), version }
      return attempt
    },
    reset() { attempt = undefined },
    pending() { return !!attempt }
  }
}
