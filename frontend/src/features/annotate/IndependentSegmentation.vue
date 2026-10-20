<script setup lang="ts">
// 原文选区与边界操作复用 A 4b8cb14 的 AnnotateWorkbench；审核及计算接共享持久化数据。
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { get, post } from '@/api/request'
import { getProgress, getTaskSetup, listTasks } from '@/api/memberA'
import { useUserStore } from '@/stores/user'
import { errorMessage, mutationAttempt, errorCode } from '@/features/versions/api'
interface Bound { startOffset: number; endOffset: number }
interface Submission { id: string; createdById:string; assignmentId: string; attemptNumber: number; status: string; reviewedAt: string | null; reviewReason: string | null; recordVersion: number; boundaries: Bound[] }
interface Checkpoint { id: string; agreementScore: number; unifiedBoundaries: Bound[] }
interface View { taskDocument: {id:string;taskId:string}; scope:Bound; text:{normalizedText:string}; assignment?:{id:string}; canReview:boolean; canWrite:boolean; submissions:Submission[]; checkpoints:Checkpoint[] }
interface Calculation {id:string;aggregateScore:number;passed:boolean;inputSubmissionIds:string[]}
const taskName=ref('')
const user=useUserStore(), submitterNames=ref<Record<string,string>>({})
const route=useRoute(),router=useRouter(),view=ref<View>(),loading=ref(false),busy=ref(false),error=ref('')
const boundaries=ref<Bound[]>([]),sourceEl=ref<HTMLElement|null>(null),selection=ref<Bound>(),reviewReasons=ref<Record<string,string>>({}),reason=ref(''),calculation=ref<Calculation>()
const options=ref<{caseId:string;taskId:string;name:string}[]>([]),selectedCase=ref('')
const caseId=computed(()=>String(route.params.caseId||'')),isTrial=import.meta.env.DEV
const attempt=mutationAttempt<{url:string;body:object}>(),pending=ref(false)
const latest=computed(()=>view.value?.submissions.filter(s=>s.assignmentId===view.value?.assignment?.id).sort((a,b)=>b.attemptNumber-a.attemptNumber)[0])
const canSubmit=computed(()=>view.value?.canWrite&&view.value.assignment&&(!latest.value||latest.value.status==='RETURNED'))
const canEdit=computed(()=>view.value?.canWrite&&(view.value.canReview||canSubmit.value))
const text=computed(()=>view.value?.text.normalizedText||'')
const status=(s:string)=>({SUBMITTED:'待审核',ACCEPTED:'已接受',RETURNED:'已退回'}[s]||s)
const rangesValid=computed(()=>{
  if(!view.value||!boundaries.value.length)return false
  let next=view.value.scope.startOffset
  for(const row of boundaries.value){if(row.startOffset!==next||row.endOffset<=next||row.endOffset>view.value.scope.endOffset)return false;next=row.endOffset}
  return next===view.value.scope.endOffset
})
async function load(preserve=false){
  const previous=view.value?.taskDocument.id===caseId.value?boundaries.value.map(b=>({...b})):undefined
  loading.value=true;error.value=''
  try{
    if(!caseId.value){
      options.value=[]
      for(let page=1;;page++){
        const tasks=await listTasks({page,size:100})
        for(const task of tasks.items){
          const [progress,setup]=await Promise.all([getProgress(task.id),getTaskSetup(task.id)])
          progress.items.forEach(item=>options.value.push({caseId:item.taskDocumentId,taskId:task.id,name:`${String(setup.task.name||'标注任务')} · ${item.taskDocumentId.slice(0,8)}`}))
        }
        if(page*tasks.size>=tasks.total)break
      }
      return
    }
    view.value=await get<View>(`/integration/cases/${caseId.value}/segmentation`)
    submitterNames.value={[user.id]:user.name}
    const setup=await getTaskSetup(view.value.taskDocument.taskId);taskName.value=String(setup.task.name||'标注任务');setup.members.forEach(member=>submitterNames.value[String(member.userId)]=String(member.displayName||'标注员'))
    const original=view.value.checkpoints[0]?.unifiedBoundaries||(preserve&&previous?.length?previous:latest.value?.boundaries)
    boundaries.value=original?original.map(b=>({...b})):[]
    if(!boundaries.value.length)suggest()
  }catch(e){error.value=errorMessage(e)}finally{loading.value=false}
}
function open(){const picked=options.value.find(o=>o.caseId===selectedCase.value);if(picked)router.push(`/tasks/${picked.taskId}/cases/${picked.caseId}/segmentation`)}
function suggest(){
  if(!view.value)return
  const result:Bound[]=[];let start=view.value.scope.startOffset
  // A 的句号/分号候选仅辅助人工，不自动提交或接受。
  for(let i=start;i<view.value.scope.endOffset;i++)if('。；;'.includes(text.value[i])){result.push({startOffset:start,endOffset:i+1});start=i+1}
  if(start<view.value.scope.endOffset)result.push({startOffset:start,endOffset:view.value.scope.endOffset})
  boundaries.value=result
}
function rememberSelection(){
  const selected=window.getSelection();if(!selected?.rangeCount||!sourceEl.value)return
  const range=selected.getRangeAt(0);if(!sourceEl.value.contains(range.commonAncestorContainer))return
  const pre=document.createRange();pre.selectNodeContents(sourceEl.value);pre.setEnd(range.startContainer,range.startOffset)
  const start=pre.toString().length,end=start+range.toString().length
  selection.value=end>start?{startOffset:start,endOffset:end}:undefined
}
function fromSelection(){if(!selection.value||!view.value)return;const row=selection.value;if(row.startOffset<view.value.scope.startOffset||row.endOffset>view.value.scope.endOffset){ElMessage.warning('选区超出标注范围');return}boundaries.value=[...boundaries.value,{...row}].sort((a,b)=>a.startOffset-b.startOffset)}
function split(index:number){const row=boundaries.value[index];let middle=Math.floor((row.startOffset+row.endOffset)/2);if(middle>0&&/[\uD800-\uDBFF]/.test(text.value[middle-1])&&/[\uDC00-\uDFFF]/.test(text.value[middle]))middle++;if(middle>=row.endOffset)return;boundaries.value.splice(index,1,{startOffset:row.startOffset,endOffset:middle},{startOffset:middle,endOffset:row.endOffset})}
function merge(index:number){boundaries.value.splice(index,2,{startOffset:boundaries.value[index].startOffset,endOffset:boundaries.value[index+1].endOffset})}
async function mutate(url:string,body:object,version=0){
  if(busy.value)return;busy.value=true;error.value=''
  try{const saved=attempt.get({url,body:JSON.parse(JSON.stringify(body))},version);pending.value=true;const result=await post<unknown>(saved.body.url,saved.body.body,{baseURL:'',headers:{'Idempotency-Key':saved.key,...(saved.version?{'If-Match':saved.version}:{})}});if(saved.body.url.endsWith('/calculations'))calculation.value=result as Calculation;attempt.reset();pending.value=false;await load(true);ElMessage.success('已保存');return result}
  catch(e){error.value=errorMessage(e);if(errorCode(e)){attempt.reset();pending.value=false}}
  finally{busy.value=false}
}
async function submit(){if(!view.value?.assignment)return;await mutate(`/api/v1/task-documents/${caseId.value}/segmentation-submissions`,{assignmentId:view.value.assignment.id,boundaries:boundaries.value,supersedesSubmissionId:latest.value?.status==='RETURNED'?latest.value.id:null})}
async function review(row:Submission,action:'ACCEPT'|'RETURN'){if(!reviewReasons.value[row.id]?.trim()){ElMessage.warning('请填写审核理由');return}await mutate(`/api/v1/segmentation-submissions/${row.id}/reviews`,{action,reviewReason:reviewReasons.value[row.id].trim()},row.recordVersion);calculation.value=undefined}
async function calculate(){const result=await mutate(`/api/local/member-d/segmentation/cases/${caseId.value}/calculations`,{});if(result)calculation.value=result as Calculation}
async function confirm(){if(!calculation.value||!reason.value.trim()){ElMessage.warning('请先计算并填写确认理由');return}await mutate(`/api/local/member-d/segmentation/cases/${caseId.value}/checkpoints`,{calculationId:calculation.value.id,unifiedBoundaries:boundaries.value,reason:reason.value.trim()})}
onMounted(()=>load());watch(()=>route.params.caseId,()=>{view.value=undefined;calculation.value=undefined;load()})
</script>

<template>
  <div v-loading="loading" class="page">
    <div class="page-head"><h1>独立切割与统一关口</h1><p class="muted">{{ taskName ? taskName + ' · ' : '' }}先独立检查原文边界，由负责裁定者逐份审核，再确认统一切割。</p></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" style="margin-bottom:16px" />
    <template v-if="!caseId">
      <el-select v-model="selectedCase" placeholder="选择已分配案例" style="width:70%"><el-option v-for="option in options" :key="option.caseId" :label="option.name" :value="option.caseId" /></el-select>
      <el-button :disabled="!selectedCase" @click="open">打开案例</el-button><el-empty v-if="!loading&&!options.length" description="暂无已分配案例" />
    </template>
    <template v-else-if="view">
      <el-alert v-if="view.checkpoints.length" type="success" :closable="false" :title="`统一切割已确认：一致性 ${(view.checkpoints[0].agreementScore*100).toFixed(2)}%。本轮独立输入只读保留，后续标注须引用此关口。`" style="margin-bottom:16px" />
      <div v-if="view.assignment&&view.checkpoints.length" class="toolbar"><RouterLink :to="`/tasks/${view.taskDocument.taskId}/cases/${caseId}/annotate`">进入本人命题标注</RouterLink></div><el-row :gutter="16">
        <el-col :span="14"><el-card><template #header>规范化原文</template>
          <div ref="sourceEl" class="source" @mouseup="rememberSelection">{{ text }}</div>
          <p class="muted">标注范围 [{{ view.scope.startOffset }}, {{ view.scope.endOffset }})</p>
        </el-card></el-col>
        <el-col :span="10"><el-card><template #header>{{ view.canReview?'统一边界候选':'本人独立边界' }}</template>
          <div v-if="canEdit" class="toolbar"><el-button size="small" :disabled="pending" @click="suggest">句号 / 分号候选</el-button><el-button size="small" :disabled="pending" @click="fromSelection">添加原文选区</el-button></div>
          <div v-for="(row,index) in boundaries" :key="index" class="boundary"><span>{{ text.slice(row.startOffset,row.endOffset) }}</span><small>[{{ row.startOffset }}, {{ row.endOffset }})</small>
            <div v-if="canEdit"><el-button link :disabled="pending" @click="split(index)">拆分</el-button><el-button link :disabled="index===boundaries.length-1||pending" @click="merge(index)">合并下一条</el-button><el-button link type="danger" :disabled="pending" @click="boundaries.splice(index,1)">移除</el-button></div>
          </div>
          <p :class="rangesValid?'valid':'invalid'">{{ rangesValid?'已连续覆盖完整标注范围':'存在遗漏或重叠，请检查边界' }}</p>
          <el-button v-if="view.assignment" type="primary" :loading="busy" :disabled="!canSubmit||!rangesValid||pending" @click="submit">{{ latest?.status==='RETURNED'?'提交新的切割记录':'提交独立切割' }}</el-button>
          <p v-if="latest?.status==='SUBMITTED'" class="muted">已提交，等待裁定者审核。</p><p v-if="latest?.status==='ACCEPTED'" class="muted">已接受，等待本案统一关口。</p>
        </el-card></el-col>
      </el-row>
      <el-card style="margin-top:16px"><template #header>{{ view.canReview?'独立提交与审核轨迹':'本人提交与审核轨迹' }}</template>
        <el-empty v-if="!view.submissions.length" description="暂无独立提交" />
        <div v-for="row in view.submissions" :key="row.id" class="submission"><b>{{ submitterNames[row.createdById] || '标注员' }} · 第 {{ row.attemptNumber }} 次 · {{ status(row.status) }}</b><p>{{ row.boundaries.map(b=>text.slice(b.startOffset,b.endOffset)).join(' ｜ ') }}</p><p v-if="row.reviewReason" class="muted">审核理由：{{ row.reviewReason }}</p>
          <template v-if="view.canReview&&view.canWrite&&row.status==='SUBMITTED'"><el-input v-model="reviewReasons[row.id]" placeholder="填写审核理由" maxlength="2000" /><div class="toolbar"><el-button :disabled="busy||pending" type="primary" @click="review(row,'ACCEPT')">接受</el-button><el-button :disabled="busy||pending" @click="review(row,'RETURN')">退回重切</el-button></div></template>
        </div>
      </el-card>
      <el-card v-if="view.canReview&&view.canWrite" style="margin-top:16px"><template #header>统一切割确认</template>
        <p class="muted">当前一致性算法为团队待评审的试行版本。仅使用不同账号最新审核接受且分配有效的输入，全部两两边界 F1 均值须严格大于 0.8。</p>
        <el-button :disabled="!isTrial||busy||pending" @click="calculate">计算一致性</el-button><p v-if="calculation">参与 {{ calculation.inputSubmissionIds.length }} 份 · 一致性 {{ (calculation.aggregateScore*100).toFixed(2) }}% · {{ calculation.passed?'达到门槛':'未达到门槛' }}</p>
        <el-input v-model="reason" placeholder="检查上方统一边界并填写确认理由" maxlength="2000" style="margin:12px 0" />
        <el-button type="primary" :disabled="!isTrial||!calculation?.passed||!rangesValid||!reason.trim()||busy||pending" @click="confirm">确认统一关口（试行）</el-button>
      </el-card>
      <el-alert v-if="pending" type="warning" :closable="false" title="上次请求结果未确认，重试会保留原内容和幂等键。" style="margin-top:16px" /><el-button v-if="pending" :loading="busy" @click="mutate('',{})">重试上次请求</el-button>
    </template>
  </div>
</template>
<style scoped>
.source{white-space:pre-wrap;line-height:1.9;font-size:16px;min-height:200px;user-select:text}.toolbar{display:flex;gap:8px;margin:10px 0;flex-wrap:wrap}.boundary,.submission{padding:12px 0;border-bottom:1px solid var(--border)}.boundary span{display:block;line-height:1.6}.boundary small{color:var(--muted)}.valid{color:var(--ok)}.invalid{color:var(--danger)}
</style>
