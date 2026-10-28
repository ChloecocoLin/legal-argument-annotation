import { get, post } from '@/api/request'

export interface ExportCandidate {
  id: string; versionNumber: number; guideVersionId: string; guideVersionCode: string
  title: string; caseNumber?: string; sourceType: string; status: 'IN_DATABASE'
  elementCount: number; relationCount: number
  preview: { id: string; displayNo: string; text: string; type: string }[]
}
export interface ExportPreview { items: ExportCandidate[]; total: number }
export interface ExportRecord { generatedMetadata?: {rendererVersion?: string;componentManifest?: unknown[];validationMode?:string}; validationMode?:string; file?: {fileName:string;sizeBytes:number;sha256:string}; id: string; status: string; format: string; requestedById: string; requestedAt: string; completedAt: string | null; fileId: string | null; failureMessage: string | null; finalVersionIds: string[]; sourceDigest: string; sourceManifest: Record<string, unknown>[] }
export const coreIncludes = ['ELEMENTS','RELATIONS','PARTICIPANTS','DICTIONARIES','VERSIONS','PROVENANCE']
export async function loadExportPreview(page = 1): Promise<ExportPreview> {
  const r = await get<{ data: {items: {finalVersion:{id:string;versionNumber:number;status:'IN_DATABASE';guideVersionId:string};document:{title:string;caseNumber?:string;sourceType:string};guideVersion:{versionCode:string};elementCount:number;relationCount:number;previewElements:ExportCandidate['preview']}[]}; meta: { total: number } }>('/exports/eligible-final-versions', { page, size: 100 }, { _withMeta: true })
  return { items: r.data.items.map(x=>({...x.document,...x.finalVersion,guideVersionCode:x.guideVersion.versionCode,elementCount:x.elementCount,relationCount:x.relationCount,preview:x.previewElements})), total: r.meta.total }
}
export const createExport = (body: {format:string}, key: string) => ['PNG','SVG'].includes(body.format) ? post<ExportRecord>('/api/local/member-d/graph-exports',body,{baseURL:'',headers:{'Idempotency-Key':key}}) : post<ExportRecord>('/exports', body, { headers: { 'Idempotency-Key': key } })
export const listExports = (page: number, status?: string) => get<{data: {items: ExportRecord[]}; meta: {total: number}}>('/exports', { page, size: 20, ...(status ? {status} : {}) }, {_withMeta: true})
export const readExport = (id: string) => get<ExportRecord>(`/exports/${id}`)
export const requestDownload = (id: string, purpose: string, key: string) => post<{fileId: string; downloadToken: string; expiresAt: string}>(`/exports/${id}/download-tickets`, {purpose}, {headers: {'Idempotency-Key': key}})
export async function saveDownload(ticket: {fileId: string; downloadToken: string}, name: string) {
  const blob = await get<Blob>(`/file-assets/${ticket.fileId}/content`, {downloadToken: ticket.downloadToken}, {responseType: 'blob'})
  const url = URL.createObjectURL(blob), a = document.createElement('a')
  a.href = url; a.download = name; a.click(); setTimeout(() => URL.revokeObjectURL(url), 1000)
}
