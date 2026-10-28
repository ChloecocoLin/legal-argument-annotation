import {ref} from 'vue'
import {get,post,patch} from '@/api/request'
import {errorMessage,mutationAttempt} from '@/features/versions/api'
export type Row=Record<string,any>
export interface Workspace {teacherMode:boolean;studentMode:boolean;creatorMode:boolean;courses:Row[];assignments:Row[];standards:Row[];sourceGrants:Row[];sources:Row[];teachers:Row[];students:Row[];exerciseTypes:Row[]}
const base='/api/local/member-d/teaching'
export const read=<T=Row>(path:string)=>get<T>(base+path,undefined,{baseURL:''})
export const loadWorkspace=()=>read<Workspace>('/workspace')
export function useTeachingMutation(){const busy=ref(false),pending=ref(false),error=ref('');const attempt=mutationAttempt<{path:string;body:Row;method:'POST'|'PATCH'}>();async function mutate(path:string,body:Row,version=0,method:'POST'|'PATCH'='POST'):Promise<Row|undefined>{if(busy.value)return;busy.value=true;pending.value=true;error.value='';try{const a=attempt.get(JSON.parse(JSON.stringify({path,body,method})),version);const config={baseURL:'',headers:{'Idempotency-Key':a.key,...(a.version?{'If-Match':a.version}:{})}};const value=a.body.method==='PATCH'?await patch<Row>(base+a.body.path,a.body.body,config):await post<Row>(base+a.body.path,a.body.body,config);attempt.reset();pending.value=false;return value}catch(e){error.value=errorMessage(e);const status=(e as {response?:{status?:number}}).response?.status;if(status&&status>=400&&status<500){attempt.reset();pending.value=false}return undefined}finally{busy.value=false}}return{busy,pending,error,mutate}}
export const utc=(value:string)=>new Date(value).toISOString().replace(/\.\d{3}Z$/,'Z')
