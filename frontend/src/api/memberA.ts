import { get, getPage, patch, post, put } from './request'

export interface PageMetaItems<T> {
  items: T[]
  total: number
  page: number
  size: number
}

export function listDocuments(params: Record<string, unknown>) {
  return getPage<{ id: string; courtName: string; causeOfAction: string }>('/documents', params)
}

export function getDocument(documentId: string) {
  return get<{ id: string; originalFileId: string; textVersionIds: string[] }>(`/documents/${documentId}`)
}

export function getTextVersion(textVersionId: string) {
  return get<{ id: string; normalizedText: string; contentSha256: string }>(
    `/document-text-versions/${textVersionId}`
  )
}

export function listTasks(params: Record<string, unknown>) {
  return getPage<{ id: string; status: string }>('/tasks', params)
}

export function createTask(body: Record<string, unknown>) {
  return post<{ id: string; status: string }>('/tasks', body)
}

export function getTask(taskId: string) {
  return get<{ id: string; ownerUserId: string; status: string; defaultRequiredAnnotators: number }>(
    `/tasks/${taskId}`
  )
}

export function updateTask(taskId: string, body: Record<string, unknown>, recordVersion: number) {
  return patch<{ recordVersion: number }>(`/tasks/${taskId}`, body, {
    headers: { 'If-Match': String(recordVersion) }
  })
}

export function addTaskDocument(taskId: string, body: Record<string, unknown>) {
  return post<{ id: string; status: string }>(`/tasks/${taskId}/documents`, body)
}

export function listMembers(taskId: string, params: Record<string, unknown> = {}) {
  return getPage<{ userId: string; memberRole: string; status: string }>(`/tasks/${taskId}/members`, params)
}

export function addMember(taskId: string, body: Record<string, unknown>) {
  return post<{ status: string; joinedAt: string }>(`/tasks/${taskId}/members`, body)
}

export function listAssignments(taskId: string) {
  return getPage<{ taskDocumentId: string; requiredAnnotators: number; assignedAnnotators: number }>(
    `/tasks/${taskId}/assignments`
  )
}

export function setAssignments(
  taskDocumentId: string,
  body: Record<string, unknown>,
  recordVersion: number
) {
  return put<{ requiredAnnotators: number; assignedAnnotators: number }>(
    `/task-documents/${taskDocumentId}/assignments`,
    body,
    { headers: { 'If-Match': String(recordVersion) } }
  )
}

export function proposeAssignments(taskId: string, assignmentMode = 'AUTO') {
  return post<{ items: { taskDocumentId: string; annotatorUserIds: string[] }[] }>(
    `/tasks/${taskId}/assignment-proposals`,
    { assignmentMode }
  )
}

export function transitionTask(taskId: string, action: 'PUBLISH' | 'START', recordVersion: number) {
  return post<{ status: string }>(
    `/tasks/${taskId}/transitions`,
    { action },
    { headers: { 'If-Match': String(recordVersion) } }
  )
}

export function getProgress(taskId: string) {
  return get<{
    items: {
      taskDocumentId: string
      submittedCount: number
      requiredAnnotators: number
      status: string
    }[]
  }>(`/tasks/${taskId}/progress`)
}

export function submitSegmentation(taskDocumentId: string, body: Record<string, unknown>) {
  return post<{ status: string; attemptNumber: number }>(
    `/task-documents/${taskDocumentId}/segmentation-submissions`,
    body
  )
}

export function listSegmentations(taskDocumentId: string) {
  return getPage<{ attemptNumber: number; status: string; reviewedAt: string | null }>(
    `/task-documents/${taskDocumentId}/segmentation-submissions`
  )
}

export function reviewSegmentation(
  submissionId: string,
  body: { action: 'ACCEPT' | 'RETURN'; reviewReason: string },
  recordVersion: number
) {
  return post<{ status: string; reviewedAt: string }>(
    `/segmentation-submissions/${submissionId}/reviews`,
    body,
    { headers: { 'If-Match': String(recordVersion) } }
  )
}

export function getPresetCheckpoint(taskDocumentId: string) {
  return get<Record<string, unknown>>(`/task-documents/${taskDocumentId}/segmentation-checkpoints`)
}

export function getWorkbench(taskId: string) {
  return get<{ items: Record<string, unknown>[] }>(`/dev/a/workbench`, { taskId })
}

export function getTaskSetup(taskId: string) {
  return get<{
    task: Record<string, unknown>
    members: Record<string, unknown>[]
    taskDocuments: Record<string, unknown>[]
  }>(`/integration/tasks/${taskId}/setup`)
}

export function getTaskOptions() {
  return get<{ guides: { id: string; versionCode: string }[]; documents: { documentId: string; documentTextVersionId: string; annotationScopeId: string; label: string }[] }>('/integration/task-options')
}

export function listUsers(params: Record<string, unknown>) {
  return getPage<{ id: string; displayName: string }>('/users', params)
}

export function listLabels(guideVersionId: string) {
  return get<Array<{ code: string; name: string; category: string }>>(
    `/guide-versions/${guideVersionId}/labels`
  )
}
