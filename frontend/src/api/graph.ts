// Frozen C graph transport. Rich nodes/edges are read from the authenticated integration workspace.
import {get,post} from './request'
import type {Gr01Success,Gr02Success,Gr02Request,Gr03Success,Gr03Request} from '@/generated/api-client/types.gen'
import type {RelationOwnerType} from '@/types/relation'
export const getGraph=(ownerType:RelationOwnerType,ownerId:string,layoutVersion?:number)=>get<Gr01Success['data']>(`/annotation-owners/${ownerType}/${ownerId}/graph`,{layoutVersion})
export const validateGraph=(draftId:string,body:Gr02Request,key:string,version:number)=>post<Gr02Success['data']>(`/annotation-drafts/${draftId}/graph/validate`,body,{headers:{'Idempotency-Key':key,'If-Match':version}})
export const saveGraphLayout=(graphId:string,body:Gr03Request,key:string)=>post<Gr03Success['data']>(`/graphs/${graphId}/layouts`,body,{headers:{'Idempotency-Key':key}})
