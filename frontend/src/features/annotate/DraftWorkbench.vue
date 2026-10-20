<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { get, post, patch, del } from '@/api/request'
import { errorMessage, errorCode, mutationAttempt } from '@/features/versions/api'
import ElementTypeSelector from '@/shared/ElementTypeSelector.vue'
import type { AnnotationDraft, Element } from '@/generated/api-client/types.gen'
import type { LabelDefinition } from '@/types/guide'
import { useUserStore } from '@/stores/user'
interface Bound {startOffset:number;endOffset:number}
interface Workspace {draft:AnnotationDraft|null;assignment:{id:string};segmentationCheckpoint:{id:string;unifiedBoundaries:Bound[]};elements:Element[];labels:LabelDefinition[];text:{normalizedText:string};canWrite:boolean;canCreate:boolean}
interface Command {method:'POST'|'PATCH'|'DELETE';url:string;body:object;actorId?:string}
const route=useRoute(),view=ref<Workspace>(),error=ref(''),loading=ref(false),busy=ref(false),pending=ref(false),selected=ref(0),label=ref<string|null>(null)
const attempt=mutationAttempt<Command>()
const user=useUserStore()
const rows=computed(()=>view.value?.segmentationCheckpoint.unifiedBoundaries.map(b=>({...b,element:view.value?.elements.find(e=>e.startOffset===b.startOffset&&e.endOffset===b.endOffset)}))||[])
const row=computed(()=>rows.value[selected.value]),remaining=computed(()=>rows.value.filter(r=>!r.element).length)
function select(index:number){if(pending.value||busy.value)return;selected.value=index;label.value=rows.value[index]?.element?.labelCode||null}
async function load(){loading.value=true;error.value='';view.value=undefined;try{view.value=await get<Workspace>(`/integration/cases/${route.params.caseId}/draft-workspace`);selected.value=Math.min(selected.value,Math.max(rows.value.length-1,0));label.value=row.value?.element?.labelCode||null}catch(e){error.value=errorMessage(e)}finally{loading.value=false}}
async function execute(command:Command,version=0){
  if(busy.value)return;busy.value=true;error.value=''
  const saved=attempt.get(JSON.parse(JSON.stringify({...command,actorId:user.id})),version);pending.value=true
  if(saved.body.actorId!==user.id){attempt.reset();pending.value=false;busy.value=false;error.value='账号已变化，请重新加载本人草稿';return}
  try{const config={headers:{'Idempotency-Key':saved.key,...(saved.version?{'If-Match':saved.version}:{})}};const c=saved.body
    if(c.method==='POST')await post(c.url,c.body,config)
    else if(c.method==='PATCH')await patch(c.url,c.body,config)
    else await del(c.url,{...config,data:c.body})
    attempt.reset();pending.value=false;await load();ElMessage.success('已保存本人草稿')
  }catch(e){error.value=errorMessage(e);if(errorCode(e)){attempt.reset();pending.value=false}}
  finally{busy.value=false}
}
function retry(){const saved=attempt.pending();if(saved)execute({method:'POST',url:'',body:{}})}
function create(){if(view.value?.canCreate)execute({method:'POST',url:`/task-documents/${route.params.caseId}/drafts`,body:{assignmentId:view.value.assignment.id,segmentationCheckpointId:view.value.segmentationCheckpoint.id}})}
function classify(){if(!view.value?.canWrite||!view.value.draft||!row.value||!label.value||pending.value)return;const e=row.value.element;execute({method:e?'PATCH':'POST',url:e?`/elements/${e.id}`:`/annotation-drafts/${view.value.draft.id}/elements`,body:e?{labelCode:label.value}:{startOffset:row.value.startOffset,endOffset:row.value.endOffset,labelCode:label.value}},e?.recordVersion||0)}
async function remove(){const e=row.value?.element;if(!e||!view.value?.canWrite||pending.value)return;try{const result=await ElMessageBox.prompt('删除分类后，该统一边界将恢复为未分类。请填写理由。','删除本人要素',{inputValidator:v=>!!v?.trim()||'请填写删除理由',inputType:'textarea'});execute({method:'DELETE',url:`/elements/${e.id}`,body:{reason:result.value.trim()}},e.recordVersion)}catch{/* 用户关闭对话框 */}}
function save(){const draft=view.value?.draft;if(draft&&view.value?.canWrite&&!pending.value)execute({method:'PATCH',url:`/annotation-drafts/${draft.id}`,body:{phase:draft.phase}},draft.recordVersion)}
onBeforeRouteLeave(()=>!busy.value&&!pending.value)
watch(()=>route.params.caseId,()=>{selected.value=0;load()},{immediate:true})
watch(()=>user.id,()=>{attempt.reset();pending.value=false;view.value=undefined;load()})
</script>

<template>
  <div v-loading="loading" class="page">
    <div class="page-head"><h1>本人命题标注</h1><p class="muted">沿用已确认的统一边界，逐条阅读原文并选择当前指南中的类型。</p></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <div class="toolbar"><RouterLink :to="`/tasks/${route.params.taskId}/cases/${route.params.caseId}/segmentation`">查看统一切割</RouterLink><el-button :disabled="busy||pending" @click="load">刷新本人数据</el-button><el-button v-if="pending" :loading="busy" @click="retry">使用原请求重试保存</el-button></div>
    <template v-if="view">
      <el-alert v-if="!view.draft" type="info" :closable="false" title="统一切割已确认。点击创建后，才会建立属于当前账号的空草稿。" />
      <el-button v-if="!view.draft" type="primary" :disabled="!view.canCreate||busy||pending" @click="create">创建本人草稿</el-button>
      <template v-else>
        <div class="toolbar"><b>{{ view.canWrite?'本人草稿':'本人草稿（只读）' }}</b><span>已分类 {{ rows.length-remaining }} / {{ rows.length }} · 保存修订 {{ view.draft.revision }}</span><el-button :disabled="!view.canWrite||busy||pending" @click="save">保存草稿</el-button></div>
        <el-row :gutter="20"><el-col :span="13"><el-card><template #header>原文与统一边界</template>
          <p class="source">{{ view.text.normalizedText }}</p>
          <button v-for="(item,index) in rows" :key="item.startOffset" class="boundary" :class="{selected:selected===index}" :disabled="busy||pending" @click="select(index)"><b>{{ item.element?.displayNo|| (item.element?'非命题':'未分类') }}</b><span>{{ view.text.normalizedText.slice(item.startOffset,item.endOffset) }}</span><small>[{{ item.startOffset }}, {{ item.endOffset }}) · {{ item.element?.labelCode||'待选择类型' }}</small></button>
        </el-card></el-col><el-col :span="11"><el-card><template #header>要素类型</template>
          <p v-if="row">{{ view.text.normalizedText.slice(row.startOffset,row.endOffset) }}</p>
          <ElementTypeSelector v-model="label" :labels="view.labels" :disabled="!view.canWrite||busy||pending" />
          <div class="toolbar"><el-button type="primary" :disabled="!view.canWrite||!row||!label||busy||pending" @click="classify">{{ row?.element?'保存类型修改':'确认分类' }}</el-button><el-button v-if="row?.element" type="danger" :disabled="!view.canWrite||busy||pending" @click="remove">删除分类</el-button></div>
          <p class="muted">命题编号随原文顺序自动更新。当前阶段可修改类型；切割边界调整需等待对应模型接入。</p>
        </el-card></el-col></el-row>
        <el-alert style="margin-top:20px" type="info" :closable="false" :title="remaining?`还有 ${remaining} 条统一边界未分类。`:'命题分类已保存。下一阶段接入关系与正式图验证后，才能完成完整标注提交。'" />
      </template>
    </template>
  </div>
</template>

<style scoped>
.toolbar{display:flex;gap:14px;align-items:center;flex-wrap:wrap;margin:16px 0}.source{white-space:pre-wrap;line-height:1.9;border-bottom:1px solid #e5e7eb;padding-bottom:18px}.boundary{width:100%;display:flex;flex-direction:column;gap:10px;text-align:left;padding:16px;margin:10px 0;border:1px solid #d8dee8;border-radius:8px;background:white;cursor:pointer;line-height:1.7;color:#334155}.boundary.selected{border-color:#3b82f6;background:#eff6ff}.boundary small,.muted{color:#64748b}.boundary:disabled{cursor:default}
</style>
