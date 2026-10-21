<script setup lang="ts">
import {computed,onMounted,ref} from 'vue'
import {onBeforeRouteLeave,useRoute,useRouter} from 'vue-router'
import ElementTypeSelector from '@/shared/ElementTypeSelector.vue'
import ArgumentGraph from '@/features/annotation/ArgumentGraph.vue'
import {errorMessage} from '@/features/versions/api'
import {loadWorkspace,read,useTeachingMutation,type Row} from './api'
const clone=<T,>(value:T):T=>JSON.parse(JSON.stringify(value))
const route=useRoute(),router=useRouter(),assignments=ref<Row[]>([]),selected=ref(String(route.params.assignmentId||'')),context=ref<Row>(),draft=ref<Row>(),elements=ref<Row[]>([]),relations=ref<Row[]>([]),dirty=ref(false),message=ref(''),confirmed=ref(false)
const {busy,pending,error,mutate}=useTeachingMutation(),blocked=computed(()=>busy.value||pending.value)
const start=ref(0),end=ref(0),label=ref<string|null>(null),code=ref('S'),parts=ref<Row[]>([])
const guided=computed(()=>context.value?.assignment.exerciseMode==='RELATION_GUIDED')
const targets=computed(()=>guided.value?context.value?.material.givenElements||[]:elements.value.map((e,i)=>({...e,id:e.clientKey,displayNo:`要素 ${i+1}`})))
async function load(){message.value='';try{assignments.value=(await loadWorkspace()).assignments;if(selected.value){const loaded=await read(`/assignments/${selected.value}/context`);context.value=loaded;draft.value=loaded.draft;elements.value=clone(draft.value?.payload?.elements||[]);relations.value=clone(draft.value?.payload?.relations||[]);start.value=loaded.material.offsetBase;end.value=start.value+loaded.material.scopeText.length;dirty.value=false}}catch(e){message.value=errorMessage(e)}}
async function act(path:string,body:Row,version=0,method:'POST'|'PATCH'='POST'){const n=await mutate(path,body,version,method);if(n)await load();return n}
function addElement(){if(!label.value)return;elements.value.push({clientKey:crypto.randomUUID(),startOffset:start.value,endOffset:end.value,labelCode:label.value});dirty.value=true}
function addPart(){parts.value.push({role:code.value==='J'||code.value==='I'?'MEMBER':parts.value.length?'TARGET':'SOURCE',targetKind:'ELEMENT',targetId:targets.value[0]?.id||null,targetRef:null})}
function changeKind(p:Row){p.targetId=p.targetKind==='ELEMENT'?targets.value[0]?.id||null:null;p.targetRef=p.targetKind==='RELATION'?relations.value[0]?.ref||null:null}
function addRelation(){relations.value.push({ref:crypto.randomUUID(),code:code.value,participants:clone(parts.value)});parts.value=[];dirty.value=true}
async function save(){if(draft.value)await act(`/answers/${draft.value.id}`,{elements:elements.value,relations:relations.value},draft.value.recordVersion,'PATCH')}
async function submit(){if(!draft.value||!confirmed.value||dirty.value)return;const n=await act(`/answers/${draft.value.id}/submit`,{confirmed:true},draft.value.recordVersion);if(n)await router.push({path:'/student-compare',query:{id:n.resultId}})}
onBeforeRouteLeave(()=>!blocked.value&&!dirty.value)
onMounted(load)
</script>
<template><section class="page"><div class="page-head"><h1>学生标注练习</h1><router-link class="btn" to="/teaching">教学工作台</router-link></div>
<p class="notice">教学试行：每次提交独立保留。标准答案与关系专项评分在提交且到达开放时间后显示；总分暂不汇总。</p>
<p v-if="message||error" role="alert">{{message||error}}</p><button v-if="pending&&!busy" class="btn" @click="act('',{})">重试未确认的操作</button>
<fieldset :disabled="blocked||dirty"><label>选择作业 <select v-model="selected" @change="load"><option value="">请选择</option><option v-for="a in assignments" :key="a.id" :value="a.id">{{a.name}}</option></select></label></fieldset>
<template v-if="context"><p>截止：{{context.assignment.dueAt}} · 答案开放：{{context.assignment.answerOpensAt}} · 最多 {{context.assignment.maxAttempts}} 次</p><article class="card" style="white-space:pre-wrap">{{context.material.scopeText}}</article>
<h2>作答记录</h2><p v-for="a in context.attempts" :key="a.id">第 {{a.attemptNumber}} 次 · {{a.status}} <router-link v-if="a.resultId" :to="{path:'/student-compare',query:{id:a.resultId}}">查看结果</router-link></p>
<button v-if="!draft" class="btn btn-primary" :disabled="blocked||!context.canCreate" @click="act(`/assignments/${selected}/attempts`,{reason:'学生主动开始独立练习'})">开始新一次练习</button>
<fieldset v-else :disabled="blocked"><legend>第 {{draft.attemptNumber}} 次草稿</legend>
<template v-if="guided"><h2>给定命题</h2><p v-for="e in targets" :key="e.id">{{e.displayNo}} · {{e.text}}</p></template>
<template v-else><h2>划分要素</h2><p>位置采用原文 UTF-16 左闭右开区间，范围 {{context.material.offsetBase}}—{{context.material.offsetBase+context.material.scopeText.length}}。</p><label>起点 <input v-model.number="start" type="number"></label><label>终点 <input v-model.number="end" type="number"></label><ElementTypeSelector v-model="label" :labels="context.material.labels"/><button class="btn" @click="addElement">添加要素</button><p v-for="(e,i) in elements" :key="e.clientKey">要素 {{i+1}} · {{e.labelCode}} [{{e.startOffset}}, {{e.endOffset}}) {{context.material.scopeText.substring(e.startOffset-context.material.offsetBase,e.endOffset-context.material.offsetBase)}} <button class="btn" @click="elements.splice(i,1);dirty=true">删除</button></p></template>
<h2>关系标注</h2><label>类型 <select v-model="code" @change="parts=[]"><option v-for="c in ['S','A','J','M','I']" :key="c">{{c}}</option></select></label><button class="btn" @click="addPart">添加参与项</button>
<div v-for="(p,i) in parts" :key="i"><select v-model="p.role"><option>SOURCE</option><option>TARGET</option><option>MEMBER</option></select><select v-model="p.targetKind" @change="changeKind(p)"><option>ELEMENT</option><option>RELATION</option></select><select v-if="p.targetKind==='ELEMENT'" v-model="p.targetId"><option v-for="t in targets" :key="t.id" :value="t.id">{{t.displayNo}} {{t.text||t.labelCode}}</option></select><select v-else v-model="p.targetRef"><option v-for="(r,j) in relations" :key="r.ref" :value="r.ref">关系 {{j+1}} {{r.code}}</option></select><button class="btn" @click="parts.splice(i,1)">删除参与项</button></div>
<button class="btn" :disabled="!parts.length" @click="addRelation">添加关系</button><p v-for="(r,i) in relations" :key="r.ref">关系 {{i+1}} · {{r.code}} · {{r.participants.length}} 个参与项 <button class="btn" @click="relations.splice(i,1);dirty=true">删除</button></p>
<p v-if="dirty">有未保存修改，请先保存。保存时校验关系与图结构。</p><button class="btn btn-primary" @click="save">保存草稿</button><label><input v-model="confirmed" type="checkbox">确认提交并保留本次记录</label><button class="btn" :disabled="dirty||!confirmed" @click="submit">提交本次作答</button>
<h2>已保存的本人论证图</h2><ArgumentGraph v-if="draft.graph" :nodes="draft.graph.nodes||[]" :edges="draft.graph.edges||[]"/>
</fieldset></template></section></template>

<style scoped>
.page{display:flex;flex-direction:column;gap:16px}.page-head{display:flex;align-items:center;justify-content:space-between}h1{font-size:24px;font-weight:700}h2{font-size:16px;margin:18px 0 10px}.notice{padding:14px 18px;border:1px solid #e5d4a5;background:#fff5de;border-radius:8px;color:#8c6c35}fieldset{border:0;min-width:0;padding:20px;background:white;border-radius:12px;box-shadow:0 1px 3px #182a4520}legend{font-weight:700}label{display:inline-flex;gap:8px;align-items:center;margin:6px}input,select{padding:8px;border:1px solid #cbd2dd;border-radius:6px;max-width:100%}.card{padding:20px;line-height:2}p{line-height:1.8}.btn{margin:4px}.page :deep(.argument-graph){min-height:240px}
</style>
