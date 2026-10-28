import { get, post } from '@/api/request'
export interface AutomationTask {id:string;taskDocumentId:string;status:string;modelId:string;modelVersion:string;executionMode:string;providerVersion:string;inputDigest:string;recordVersion:number;stage:string;progress:number;suggestionCount:number;createdAt:string;requestedAt:string|null;completedAt:string|null;failureCode:string|null;failureMessage:string|null}
export interface Model {modelId:string;modelVersion:string;displayName:string;description:string;executionMode:string;available:boolean;capability:string}
export interface Workspace {items:AutomationTask[];total:number;models:Model[];cases:{taskDocumentId:string;title:string;taskName:string;taskId:string}[];realModelAvailable:boolean;reviewContractReady:boolean}
export interface Suggestion {id:string;suggestionKind:string;startOffset:number;endOffset:number;candidateLabelCode:string;text:string;confidence:number;status:string}
export interface Detail {task:AutomationTask;taskName:string;documentTitle:string;suggestions:Suggestion[];events:Record<string,unknown>[]}
export const workspace=(page:number,status:string)=>get<Workspace>('/integration/automation/workspace',{page,size:20,...(status?{status}:{})})
export const detail=(id:string)=>get<Detail>(`/integration/automation-tasks/${id}/workspace`)
export const create=(caseId:string,body:{modelId:string;modelVersion:string},key:string)=>post<AutomationTask>(`/api/local/member-d/task-documents/${caseId}/automation-tasks`,body,{baseURL:'',headers:{'Idempotency-Key':key}})
export const transition=(id:string,action:string,key:string,version:number)=>post<{status:string}>(`/automation-tasks/${id}/transitions`,{action},{headers:{'Idempotency-Key':key,'If-Match':version}})
