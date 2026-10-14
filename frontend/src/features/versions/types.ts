import type { AnnotationDraft, AnnotationVersion, Element, Relation, RelationParticipant, LabelDefinition, SegmentationCheckpoint, GuideVersion, DocumentTextVersion } from '@/generated/api-client/types.gen'
export type { AnnotationVersion, Element, Relation, RelationParticipant }

// Approved Markdown transport composition; review patch is separate from the frozen input.
export interface SnapshotView {
  version: AnnotationVersion
  elements: Element[]
  relations: Relation[]
  participants: RelationParticipant[]
}
export interface VersionList {
  data: { items: AnnotationVersion[]; submittedCount: number; requiredAnnotators: number }
  meta: { page: number; size: number; total: number }
}
// Local/test adapter DTOs are NOT public A/B/C contracts. Replace the adapter at integration time.
interface LocalTask {
  id: string; name: string; ownerUserId: string; guideVersionId: string; status: string
  defaultRequiredAnnotators: number; assignmentMode: string; dueAt: string | null
}
interface LocalTaskDocument {
  id: string; taskId: string; documentId: string; requiredAnnotators: number; status: string
  documentTextVersionId: string; annotationScopeId: string
}
export interface LocalContext {
  fixture: true
  actorId: string
  actorName: string
  roles: string[]
  tasks: LocalTask[]
  taskDocuments: (LocalTaskDocument & { document: { id: string; title: string }; guideVersion: GuideVersion })[]
  drafts: AnnotationDraft[]
}
export interface SubmissionPreview {
  fixture: true
  draft: AnnotationDraft
  task: LocalTask
  taskDocument: LocalTaskDocument
  checkpoint: SegmentationCheckpoint
  textVersion: DocumentTextVersion
  elements: Element[]
  relations: Relation[]
  participants: RelationParticipant[]
  graph: { id: string; status: string; elementIds: string[]; relationIds: string[] }
  labels: LabelDefinition[]
}
