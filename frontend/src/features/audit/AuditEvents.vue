<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { get } from '@/api/request'
import LocalSession from '@/features/versions/LocalSession.vue'
import { errorMessage, formatTime, getLocalContext } from '@/features/versions/api'
interface AuditEvent {
 id:string;actorUserId:string;effectiveRole:string;permissionCode:string;requestId:string;operationCode:string;objectType:string;objectId:string
 taskId:string;taskDocumentId:string;sessionId:string;occurredAt:string;reason:string;contentDigest:string;transactionId:string
 beforeState:Record<string,{status:string;recordVersion:number}>;afterState:Record<string,{status:string;recordVersion:number}>
 actorName?:string;taskName?:string;sourceVersionIds:string[];decisionIds:string[]
}
function localDate(d:Date){return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`}
const today=new Date(), weekAgo=new Date(today);weekAgo.setDate(today.getDate()-6)
const actorName=ref(''),taskName=ref('')
function dateBound(value:string,end=false){return new Date(value+'T'+(end?'23:59:59':'00:00:00')).toISOString().replace(/\.\d{3}Z$/,'Z')}
const route=useRoute(), objectId=ref(String(route.query.objectId||'')), actorId=ref(''), from=ref(localDate(weekAgo)), to=ref(localDate(today)), page=ref(1), total=ref(0)
const rows=ref<AuditEvent[]>([]),loading=ref(false),error=ref(''),expanded=ref('')
async function load(reset=false) {
 if(reset)page.value=1
 loading.value=true;error.value='';rows.value=[];total.value=0
 try {
  if(!import.meta.env.DEV)throw new Error('当前环境尚未开放审计查询')
  await getLocalContext()
  const result=await get<{data:{items:AuditEvent[];total:number};meta:{page:number;size:number;total:number}}>('/audit-events',{
   page:page.value,size:10,...(actorName.value.trim()?{actorName:actorName.value.trim()}:{}),...(taskName.value.trim()?{taskName:taskName.value.trim()}:{}),...(objectId.value.trim()?{objectId:objectId.value.trim()}:{}),...(actorId.value.trim()?{actorId:actorId.value.trim()}:{}),...(from.value.trim()?{from:dateBound(from.value)}:{}),...(to.value.trim()?{to:dateBound(to.value,true)}:{})
  },{_withMeta:true})
  rows.value=result.data.items;total.value=result.meta.total
 }catch(e){error.value=errorMessage(e)}finally{loading.value=false}
}
onMounted(()=>load())
</script>
<template>
 <LocalSession description="查看当前权限范围内的入库操作" />
 <div class="page-head between"><div><div class="eyebrow">AUDIT HISTORY</div><h1 class="t-h1">入库与审计记录</h1><p class="t-sm muted">核查操作者、状态变化和固定来源。记录追加保存，查询不会产生新的操作记录。</p></div><button class="btn btn-secondary" @click="load()" :disabled="loading">刷新</button></div>
 <div class="note warn boundary">这里查询最终版本入库记录；裁定、送审和退回请查看对应案例的历史。</div>
 <section class="card"><form class="card-body filters" @submit.prevent="load(true)">
<div class="field"><label for="audit-name">操作者姓名</label><input id="audit-name" class="input" v-model="actorName" :disabled="loading" placeholder="按姓名查询"></div>
<div class="field"><label for="audit-task">任务名称</label><input id="audit-task" class="input" v-model="taskName" :disabled="loading" placeholder="按任务名查询"></div>
<div class="field"><label for="audit-from">开始日期（本地时间）</label><input id="audit-from" class="input" type="date" v-model="from" :max="to" :disabled="loading"></div>
<div class="field"><label for="audit-to">结束日期（本地时间）</label><input id="audit-to" class="input" type="date" v-model="to" :min="from" :disabled="loading"></div>
<details class="advanced"><summary>高级筛选</summary><div class="field"><label for="audit-object">最终版本 UUID</label><input id="audit-object" class="input" v-model="objectId" :disabled="loading"></div><div class="field"><label for="audit-actor">操作者 UUID</label><input id="audit-actor" class="input" v-model="actorId" :disabled="loading"></div></details>
<button type="submit" class="btn btn-primary" :disabled="loading">查询</button></form>
  <div v-if="loading" class="card-body muted" role="status">正在读取获授权的审计记录…</div>
  <div v-else-if="error" class="note danger" role="alert">{{error}}</div>
  <template v-else><div class="tbl-wrap"><table class="tbl"><thead><tr><th>时间</th><th>操作 / 对象</th><th>操作者</th><th>状态变化</th><th>操作</th></tr></thead><tbody><template v-for="event in rows" :key="event.id"><tr><td>{{formatTime(event.occurredAt)}}</td><td><b>{{'最终版本入库'}}</b><div class="t-xs">{{event.taskName}}</div></td><td><span>{{event.actorName || event.actorUserId}}</span><div class="t-xs muted">{{event.effectiveRole}}</div></td><td>{{event.beforeState.finalVersion?.status}} → {{event.afterState.finalVersion?.status}}</td><td><button class="btn btn-sm btn-secondary" @click="expanded=expanded===event.id?'':event.id">{{expanded===event.id?'收起':'详情'}}</button></td></tr><tr v-if="expanded===event.id"><td colspan="5"><div class="details stack-3"><p>最终版本：{{event.objectId}} · 操作者：{{event.actorUserId}}</p><p>理由：{{event.reason}}</p><p>请求 ID：<span class="mono">{{event.requestId}}</span></p><p>事务 ID：<span class="mono">{{event.transactionId}}</span></p><p>内容摘要：<span class="mono">{{event.contentDigest}}</span></p><p>源版本：<span class="mono">{{event.sourceVersionIds.join(' / ')}}</span></p><p>裁定决定：<span class="mono">{{event.decisionIds.join(' / ')}}</span></p><div v-for="(state,key) in event.afterState" :key="key" class="panel state-line"><b>{{key}}</b><span>{{event.beforeState[key]?.status}} → {{state.status}}</span><span>版本 {{event.beforeState[key]?.recordVersion}} → {{state.recordVersion}}</span></div></div></td></tr></template><tr v-if="!rows.length"><td colspan="5" class="empty muted">当前筛选与权限范围内没有入库审计记录。尚未批准的版本不能入库。</td></tr></tbody></table></div><div class="card-foot between"><span class="muted t-xs">共 {{total}} 条获授权记录 · 第 {{page}} 页</span><div class="row"><button class="btn btn-secondary" :disabled="page===1" @click="page--;load()">上一页</button><button class="btn btn-secondary" :disabled="page*10>=total" @click="page++;load()">下一页</button></div></div></template>
 </section>
</template>
<style scoped>.t-h1{margin:6px 0}.boundary{margin-bottom:20px}.filters{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px;align-items:end}.filters>button{justify-self:start}.empty{padding:42px 20px;text-align:center;white-space:normal}.details{max-width:100%;overflow-wrap:anywhere;white-space:normal;padding:14px}.state-line{display:flex;gap:18px;padding:12px;flex-wrap:wrap}.card-foot{gap:12px;flex-wrap:wrap}.card-foot .row{gap:10px}.tbl td{overflow-wrap:anywhere}.tbl{font-size:12px}@media(max-width:760px){.filters{grid-template-columns:1fr}.page-head{align-items:flex-start;gap:12px;flex-direction:column}}</style>
