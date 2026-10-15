import { get } from '@/api/request'

export interface ExportCandidate {
  id: string; versionNumber: number; guideVersionId: string; guideVersionCode: string
  title: string; caseNumber?: string; sourceType: string; status: 'IN_DATABASE'
  elementCount: number; relationCount: number
  preview: { id: string; displayNo: string; text: string; type: string }[]
}
export interface ExportPreview { mode: 'LOCAL_PREVIEW'; exportAvailable: false; reason: string; items: ExportCandidate[] }
export async function loadExportPreview(): Promise<ExportPreview> {
  if (import.meta.env.DEV) return get<ExportPreview>('/api/local/member-d/export-preview', undefined, { baseURL: '' })
  throw new Error('导出范围与预览尚未接入，请等待导出模型评审和正式接口联调')
}
