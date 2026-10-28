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
 sourceVersionIds:string[];decisionIds:string[]
}
const route=useRoute(), objectId=ref(String(route.query.objectId||'')), actorId=ref(''), from=ref(''), to=ref(''), page=ref(1), total=ref(0)
const rows=ref<AuditEvent[]>([]),loading=ref(false),error=ref(''),expanded=ref('')
async function load(reset=false) {
 if(reset)page.value=1
 loading.value=true;error.value='';rows.value=[];total.value=0
 try {
  if(!import.meta.env.DEV)throw new Error('审计试行尚未开放到生产环境，等待团队模型评审')
  await getLocalContext()
  const result=await get<{data:{items:AuditEvent[];total:number};meta:{page:number;size:number;total:number}}>('/audit-events',{
   page:page.value,size:10,...(objectId.value.trim()?{objectId:objectId.value.trim()}:{}),...(actorId.value.trim()?{actorId:actorId.value.trim()}:{}),...(from.value.trim()?{from:from.value.trim()}:{}),...(to.value.trim()?{to:to.value.trim()}:{})
  },{_withMeta:true})
  rows.value=result.data.items;total.value=result.meta.total
 }catch(e){error.value=errorMessage(e)}finally{loading.value=false}
}
onMounted(()=>load())
</script>
<template>
 <LocalSession description="永久审计按 D 分支提案试行；管理员与任务授权者只读查询，仍待团队评审" />
 <div class="page-head between"><div><div class="eyebrow">AUDIT HISTORY</div><h1 class="t-h1">入库与审计记录</h1><p class="t-sm muted">核查操作者、状态变化和固定来源。记录追加保存，查询不会产生新的操作记录。</p></div><button class="btn btn-secondary" @click="load()" :disabled="loading">刷新</button></div>
 <div class="note warn boundary">试行范围：本批记录最终版本入库事件；此前的裁定、送审与退回仍保留在各对象历史中。公共审计模型待团队评审。</div>
 <section class="card"><form class="card-body filters" @submit.prevent="load(true)"><div class="field"><label for="audit-object">对象 ID</label><input id="audit-object" class="input" v-model="objectId" placeholder="最终版本 UUID" :disabled="loading"></div><div class="field"><label for="audit-actor">操作者 ID</label><input id="audit-actor" class="input" v-model="actorId" placeholder="用户 UUID" :disabled="loading"></div><div class="field"><label for="audit-from">开始时间（UTC）</label><input id="audit-from" class="input" v-model="from" placeholder="2026-10-15T00:00:00Z" :disabled="loading"></div><div class="field"><label for="audit-to">结束时间（UTC）</label><input id="audit-to" class="input" v-model="to" placeholder="2026-10-15T23:59:59Z" :disabled="loading"></div><button type="submit" class="btn btn-primary" :disabled="loading">查询</button></form>
  <div v-if="loading" class="card-body muted" role="status">正在读取获授权的审计记录…</div>
  <div v-else-if="error" class="note danger" role="alert">{{error}}</div>
  <template v-else><div class="tbl-wrap"><table class="tbl"><thead><tr><th>时间</th><th>操作 / 对象</th><th>操作者</th><th>状态变化</th><th>操作</th></tr></thead><tbody><template v-for="event in rows" :key="event.id"><tr><td>{{formatTime(event.occurredAt)}}</td><td><b>{{event.operationCode==='TRIAL_FV06'?'试行最终版本入库':'最终版本入库'}}</b><div class="mono t-xs">{{event.objectId}}</div></td><td><span>{{event.effectiveRole}}</span><div class="mono t-xs">{{event.actorUserId}}</div></td><td>{{event.beforeState.finalVersion?.status}} → {{event.afterState.finalVersion?.status}}</td><td><button class="btn btn-sm btn-secondary" @click="expanded=expanded===event.id?'':event.id">{{expanded===event.id?'收起':'详情'}}</button></td></tr><tr v-if="expanded===event.id"><td colspan="5"><div class="details stack-3"><p>理由：{{event.reason}}</p><p>请求 ID：<span class="mono">{{event.requestId}}</span></p><p>事务 ID：<span class="mono">{{event.transactionId}}</span></p><p>内容摘要：<span class="mono">{{event.contentDigest}}</span></p><p>源版本：<span class="mono">{{event.sourceVersionIds.join(' / ')}}</span></p><p>裁定决定：<span class="mono">{{event.decisionIds.join(' / ')}}</span></p><div v-for="(state,key) in event.afterState" :key="key" class="panel state-line"><b>{{key}}</b><span>{{event.beforeState[key]?.status}} → {{state.status}}</span><span>版本 {{event.beforeState[key]?.recordVersion}} → {{state.recordVersion}}</span></div></div></td></tr></template><tr v-if="!rows.length"><td colspan="5" class="empty muted">当前筛选与权限范围内没有入库审计记录。尚未批准的版本不能入库。</td></tr></tbody></table></div><div class="card-foot between"><span class="muted t-xs">共 {{total}} 条获授权记录 · 第 {{page}} 页</span><div class="row"><button class="btn btn-secondary" :disabled="page===1" @click="page--;load()">上一页</button><button class="btn btn-secondary" :disabled="page*10>=total" @click="page++;load()">下一页</button></div></div></template>
 </section>
</template>
<style scoped>.t-h1{margin:6px 0}.boundary{margin-bottom:20px}.filters{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px;align-items:end}.filters>button{justify-self:start}.empty{padding:42px 20px;text-align:center;white-space:normal}.details{max-width:100%;overflow-wrap:anywhere;white-space:normal;padding:14px}.state-line{display:flex;gap:18px;padding:12px;flex-wrap:wrap}.card-foot{gap:12px;flex-wrap:wrap}.card-foot .row{gap:10px}.tbl td{overflow-wrap:anywhere}.tbl{font-size:12px}@media(max-width:760px){.filters{grid-template-columns:1fr}.page-head{align-items:flex-start;gap:12px;flex-direction:column}}</style>
