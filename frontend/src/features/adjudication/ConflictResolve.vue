<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { post } from '@/api/request'
import { onBeforeRouteLeave } from 'vue-router'
import { useRoute, useRouter } from 'vue-router'
import LocalSession from '@/features/versions/LocalSession.vue'
import { errorMessage, errorCode, mutationAttempt, formatTime } from '@/features/versions/api'
import { formula } from '@/features/versions/formula'
import { availableVersions, createSession, localSessions, workspace, decide, completePhase, returnSession, type Workspace, type PropositionAdjudicationItem, type RelationAdjudicationItem, type J07Request } from './api'
import type { AnnotationVersion, SnapshotView } from '@/features/versions/types'
const route = useRoute(), router = useRouter(), view = ref<Workspace>(), versions = ref<AnnotationVersion[]>([])
const caseId = ref(String(route.query.caseId || '')), selected = ref<string[]>([])
const loading = ref(false), busy = ref(false), error = ref(''), notice = ref(''), reason = ref(''), returnDialog = ref(false), targets = ref<string[]>([])
const local = import.meta.env.DEV
const expertTarget=ref<{id:string;kind:'PROPOSITION'|'RELATION'}>(),expertId=ref(''),expertQuestion=ref('')
 type Item = PropositionAdjudicationItem | RelationAdjudicationItem
 type Command = { action: 'decision' | 'propositions' | 'relations' | 'return' | 'expert'; itemId?: string; kind?: 'PROPOSITION' | 'RELATION'; body: J07Request | { confirmed: true } | { targetAssignmentIds: string[]; reason: string } | {expertUserId:string;itemKind:'PROPOSITION'|'RELATION';itemId:string;question:string} }
const choice = ref<{ item: Item; kind: 'PROPOSITION' | 'RELATION'; body: J07Request }>()
const createAttempt = mutationAttempt<{ sourceVersionIds: string[] }>(), actionAttempt = mutationAttempt<Command>(), pending = ref(false), pendingAction = ref<Command['action']>()
const active = computed(() => view.value?.session.status)
const phaseNames: Record<string, string> = { PROPOSITION_REVIEW: '命题裁定', RELATION_REVIEW: '关系裁定', FINAL_REVIEW: '待最终审查', RETURNED: '已退回补正', EXPERT_PENDING: '等待专家', COMPLETED: '已完成' }
const conflictNames: Record<string, string> = { BOUNDARY: '边界分歧', LABEL: '标签分歧', EXISTENCE: '存在性分歧', AGREEMENT: '一致项', TYPE: '类型分歧', DIRECTION: '方向分歧', PARTICIPANTS: '参与项分歧' }
const decisionNames: Record<string, string> = { ADOPT_VERSION: '采用来源版本', CONFIRM_AGREEMENT: '人工确认一致', DELETE: '删除', CUSTOM: '采用自定义快照' }
const propDone = computed(() => view.value?.propositionItems.filter(i => i.status === 'DECIDED').length || 0)
const relDone = computed(() => view.value?.relationItems.filter(i => i.status === 'DECIDED').length || 0)
const readOnly = computed(() => !['PROPOSITION_REVIEW', 'RELATION_REVIEW'].includes(active.value || ''))
function resetAttempts() { createAttempt.reset(); actionAttempt.reset(); pending.value = false; pendingAction.value = undefined }
async function load(reset = true) {
 loading.value = true; error.value = ''; if (reset) resetAttempts()
 try {

  if (route.query.sessionId) { view.value = await workspace(String(route.query.sessionId)); if(route.query.customAnnotationId){const kind=route.query.itemKind==='RELATION'?'RELATION':'PROPOSITION';const item=(kind==='RELATION'?view.value.relationItems:view.value.propositionItems).find(i=>i.id===route.query.itemId);if(item&&!view.value.decisions.some(d=>d.id===item.decisionId&&d.customAnnotationId===String(route.query.customAnnotationId))&&active.value===(kind==='RELATION'?'RELATION_REVIEW':'PROPOSITION_REVIEW'))choice.value={item,kind,body:{action:'CUSTOM',reason:'',customAnnotationId:String(route.query.customAnnotationId)}}} }
  else {
   if (!caseId.value) throw new Error('请从任务详情选择要裁定的案例')
   { const sessions = await localSessions(caseId.value); const latest = sessions.items.filter(s => s.status !== 'RETURNED').sort((a, b) => b.createdAt.localeCompare(a.createdAt))[0]; if (latest) { await router.replace({ path: route.path, query: { caseId: caseId.value, sessionId: latest.id } }); view.value = await workspace(latest.id); return } }
   versions.value = await availableVersions(caseId.value)
   // Default to the latest version per author, while J01 enforces actual eligibility.
   const latest = new Map<string, AnnotationVersion>()
   versions.value.filter(v => ['SUBMITTED', 'UNDER_ADJUDICATION'].includes(v.status)).forEach(v => { if ((latest.get(v.submittedById)?.versionNumber || 0) < v.versionNumber) latest.set(v.submittedById, v) })
   selected.value = [...latest.values()].map(v => v.id)
  }
 } catch (e) { error.value = errorMessage(e) } finally { loading.value = false }
}
async function start() {
 busy.value = true; error.value = ''; const a = createAttempt.get({ sourceVersionIds: [...selected.value] }, 1); pending.value = true
 try { const s = await createSession(caseId.value, a.body, a.key); resetAttempts(); await router.replace({ path: route.path, query: { caseId: caseId.value, sessionId: s.id } }); await load() }
 catch (e) { error.value = errorMessage(e); if (errorCode(e)) resetAttempts() } finally { busy.value = false }
}
function editable(kind: 'PROPOSITION' | 'RELATION') { return !busy.value && !pending.value && active.value === (kind === 'PROPOSITION' ? 'PROPOSITION_REVIEW' : 'RELATION_REVIEW') && (kind !== 'PROPOSITION' || local) }
function pick(item: Item, kind: 'PROPOSITION' | 'RELATION', action: 'ADOPT_VERSION' | 'DELETE' | 'CONFIRM_AGREEMENT', snapshot?: SnapshotView, objectId?: string) {
 reason.value = ''; choice.value = { item, kind, body: action === 'ADOPT_VERSION' ? { action, reason: '', sourceVersionId: snapshot!.version.id, sourceObjectId: objectId! } : { action, reason: '' } }
}
async function mutate(action: Command['action']) {
 if (!view.value || busy.value) return
 busy.value = true; error.value = ''; notice.value = ''
 const command: Command = action === 'expert' ? {action,body:{expertUserId:expertId.value,itemKind:expertTarget.value!.kind,itemId:expertTarget.value!.id,question:expertQuestion.value.trim()}} : action === 'decision' ? { action, itemId: choice.value!.item.id, kind: choice.value!.kind, body: { ...choice.value!.body, reason: reason.value.trim() } } : action === 'return' ? { action, body: { targetAssignmentIds: [...targets.value], reason: reason.value.trim() } } : { action, body: { confirmed: true } }
 const a = actionAttempt.get(command, action === 'decision' ? choice.value!.item.recordVersion : view.value.session.recordVersion); pending.value = true; pendingAction.value = action
 try {
  if(a.body.action==='expert')await post(`/adjudication-sessions/${view.value.session.id}/expert-reviews`,a.body.body,{headers:{'Idempotency-Key':a.key,'If-Match':a.version}})
  else if (a.body.action === 'decision') await decide(a.body.itemId!, a.body.kind!, a.body.body as J07Request, a.key, a.version)
  else if (a.body.action === 'return') await returnSession(view.value.session.id, a.body.body as { targetAssignmentIds: string[]; reason: string }, a.key, a.version)
  else await completePhase(view.value.session.id, a.body.action, a.key, a.version)
  notice.value = action === 'decision' && choice.value?.kind === 'PROPOSITION' ? '命题试行决定已保存；正式 J04 模型仍待评审' : '操作已保存'
  expertTarget.value=undefined;expertQuestion.value='';choice.value = undefined; returnDialog.value = false; resetAttempts(); await load(false)
 } catch (e) { error.value = errorMessage(e); if (errorCode(e)) resetAttempts() } finally { busy.value = false }
}
function candidates(item: Item, s: SnapshotView) { return 'candidateElementIds' in item ? s.elements.filter(e => item.candidateElementIds.includes(e.id)) : s.relations.filter(r => item.candidateRelationIds.includes(r.id)) }
function current(item: Item) { return view.value?.decisions.find(d => d.id === item.decisionId) }
onBeforeRouteLeave(()=>!busy.value&&!pending.value)
onMounted(() => load())
</script>
<template>
 <LocalSession />
 <div class="page-head between"><div><div class="eyebrow">CONFLICT RESOLUTION</div><h1 class="t-h1">冲突解决 · 多版本裁定</h1><p class="t-sm muted">对比独立来源版本。严格遵循“先裁定命题、后裁定关系”的两阶段顺序。</p></div><router-link class="btn btn-secondary" to="/tasks">返回任务进度</router-link></div>
 <section class="card phase-card"><div class="card-body steps"><div v-for="(label, n) in ['命题裁定', '关系裁定', '确认最终版本']" :key="label" class="step" :class="{ 'is-current': n === (active === 'RELATION_REVIEW' ? 1 : active === 'FINAL_REVIEW' ? 2 : 0) }"><span class="step-n">{{ n + 1 }}</span>{{ label }}</div></div></section>
 <div v-if="loading" class="card card-body muted" role="status">正在读取裁定会话…</div>
 <div v-if="error" class="note danger" role="alert">{{ error }} <button class="btn btn-secondary" :disabled="busy" @click="load()">刷新</button></div>
 <div v-if="notice" class="note accent" role="status">{{ notice }}</div>
 <div v-if="pending && !busy" class="note warn">上次操作结果尚未确认，请重试相同操作或刷新核实。<button v-if="pendingAction" class="btn btn-secondary" @click="mutate(pendingAction)">重试相同操作</button><button v-else class="btn btn-secondary" @click="start">重试创建会话</button></div>
 <div v-if="!loading && !view" class="card"><div class="card-head"><div class="card-title">选择来源版本</div></div><div class="card-body stack-3"><p class="muted t-sm">需满足案例人数要求，每人选择一份有效成果；会话建立后来源固定。</p><label v-for="v in versions.filter(v => ['SUBMITTED', 'UNDER_ADJUDICATION'].includes(v.status))" :key="v.id" class="panel version-option"><input type="checkbox" v-model="selected" :value="v.id" :disabled="busy || pending" /><b>{{ v.displayCode }}</b><span class="muted t-xs">{{ formatTime(v.submittedAt) }} · {{ v.elementIds.length }} 个要素</span></label><p v-if="!versions.length" class="muted">尚无可用版本，请先完成独立提交。</p><button class="btn btn-primary" :disabled="busy || selected.length < 2" @click="start">{{ busy ? '正在建立…' : '获取或创建裁定会话' }}</button></div></div>
 <section v-if="view && !loading" class="grid-2 conflict-layout">
  <div class="stack-5">
   <div v-if="view.decisionMode" class="note warn">来源为真实固定版本；命题逐项决定继续使用已授权试行入口。当前映射支持一对一，拆分合并仍待 M13 评审。</div>
   <div v-if="active === 'RETURNED'" class="note warn">本会话已退回，原版本和决定只读。指定成员补交后，从任务详情建立新会话。</div>
   <div v-for="kind in (['PROPOSITION', 'RELATION'] as const)" :key="kind" class="card" :class="{ 'locked-card': kind === 'RELATION' && !view.session.propositionCompletedAt }">
    <div class="card-head between"><div class="card-title"><span class="dot"></span>{{ kind === 'PROPOSITION' ? '第一阶段 · 命题裁定' : '第二阶段 · 关系裁定' }}</div><span class="badge warn">{{ (kind === 'PROPOSITION' ? view.propositionItems : view.relationItems).filter(i => i.status !== 'DECIDED').length }} 项待定</span></div>
    <div class="card-body"><div class="note accent">{{ kind === 'PROPOSITION' ? '命题与非命题均需逐项决定，一致项也须人工确认。' : '关系裁定在命题确认后进行；删除内层关系时须处理外层引用。' }}</div>
     <p v-if="kind === 'RELATION' && !view.session.propositionCompletedAt" class="muted t-sm">命题阶段完成后加载关系候选。</p>
     <p v-else-if="!(kind === 'PROPOSITION' ? view.propositionItems : view.relationItems).length" class="muted t-sm">本阶段无候选项。</p>
     <article v-for="(item, index) in (kind === 'PROPOSITION' ? view.propositionItems : view.relationItems)" :key="item.id" class="cf-row" :class="{ conflict: item.conflictType !== 'AGREEMENT' }">
      <div class="cf-head"><b class="mono">{{ kind === 'PROPOSITION' ? '命题项' : '关系项' }} {{ index + 1 }}</b><span class="badge" :class="item.conflictType === 'AGREEMENT' ? 'success' : 'danger'">{{ conflictNames[item.conflictType] }}</span><span class="muted t-xs">{{ item.status === 'DECIDED' ? decisionNames[current(item)?.action || ''] || '已决定' : '未完成' }}</span></div>
      <div class="cf-body" :style="{ gridTemplateColumns: `repeat(${view.snapshots.length}, minmax(190px, 1fr))` }"><div v-for="s in view.snapshots" :key="s.version.id" class="cf-side"><div class="who">{{ s.version.displayCode }} · <router-link :to="`/versions/${s.version.id}`">完整版本</router-link></div><p v-if="!candidates(item, s).length" class="muted">该版本未标注</p><div v-for="obj in candidates(item, s)" :key="obj.id" class="candidate"><template v-if="'text' in obj"><span class="badge outline">{{ obj.labelCode }}</span><span class="t-xs muted"> [{{ obj.startOffset }}, {{ obj.endOffset }})</span><p>{{ obj.text }}</p></template><template v-else><span class="badge outline">{{ obj.relationCode }}</span><p class="mono">{{ formula(obj, s.elements, s.relations, s.participants) }}</p></template><button v-if="item.conflictType !== 'AGREEMENT'" class="btn btn-secondary" :disabled="!editable(kind)" @click="pick(item, kind, 'ADOPT_VERSION', s, obj.id)">采用 {{ s.version.displayCode }}</button></div></div></div>
      <div class="cf-decide"><button v-if="item.conflictType === 'AGREEMENT'" class="btn btn-secondary" :disabled="!editable(kind)" @click="pick(item, kind, 'CONFIRM_AGREEMENT')">人工确认一致</button><button v-else class="btn btn-secondary" :disabled="!editable(kind)" @click="pick(item, kind, 'DELETE')">删除此项</button><router-link class="btn btn-secondary" :to="{ path: '/adjudication/annotate', query: { sessionId: view.session.id, itemKind: kind, itemId: item.id } }">自定义上下文</router-link><button class="btn btn-secondary" :disabled="!editable(kind)" @click="expertTarget={id:item.id,kind};expertQuestion=''">请专家复核</button><span v-if="current(item)" class="muted t-xs">理由：{{ current(item)?.reason || '未要求填写' }}</span></div>
     </article>
     <button v-if="active === (kind === 'PROPOSITION' ? 'PROPOSITION_REVIEW' : 'RELATION_REVIEW')" class="btn btn-primary" :disabled="busy || pending || (kind === 'PROPOSITION' ? propDone !== view.propositionItems.length : relDone !== view.relationItems.length)" @click="mutate(kind === 'PROPOSITION' ? 'propositions' : 'relations')">{{ kind === 'PROPOSITION' ? '确认命题阶段完成' : '确认关系阶段完成' }}</button>
    </div>
   </div>
  </div>
  <aside class="stack-5">
   <div v-if="expertTarget" class="card"><div class="card-head">提交专家复核</div><div class="card-body"><select v-model="expertId" class="select" :disabled="busy||pending"><option value="">选择本任务专家</option><option v-for="e in view.experts||[]" :key="e.id" :value="e.id">{{ e.name }}</option></select><textarea v-model="expertQuestion" class="textarea" :disabled="busy||pending" placeholder="描述需专家判断的问题" /><button class="btn btn-primary" :disabled="busy||pending||!expertId||!expertQuestion.trim()||active!==(expertTarget.kind==='RELATION'?'RELATION_REVIEW':'PROPOSITION_REVIEW')" @click="mutate('expert')">提交专家</button><button class="btn btn-secondary" :disabled="busy||pending" @click="expertTarget=undefined">取消</button><p v-if="!view.experts?.length" class="muted">本任务尚无有效专家成员，请任务创建者添加。</p></div></div>
   <div v-if="view.expertReviews?.length" class="card"><div class="card-head">专家复核记录</div><div class="card-body"><p v-for="r in view.expertReviews" :key="r.id">{{ view.experts?.find(e=>e.id===r.expertUserId)?.name||'专家' }} · {{ r.status }}</p></div></div>
<div class="card"><div class="card-head"><div class="card-title">对比版本</div></div><div class="card-body"><div v-for="s in view.snapshots" :key="s.version.id" class="source-row"><span class="avatar">{{ s.version.displayCode }}</span><div><b>{{ s.version.displayCode }}</b><p class="muted t-xs">{{ formatTime(s.version.submittedAt) }}</p></div><router-link :to="`/versions/${s.version.id}`">查看</router-link></div></div></div>
   <div class="card"><div class="card-head"><div class="card-title">裁定进度</div><span class="badge accent">{{ phaseNames[active || ''] || active }}</span></div><div class="card-body"><div class="between t-xs"><span>命题及非命题项</span><span>{{ propDone }}/{{ view.propositionItems.length }}</span></div><div class="progress"><i :style="{ width: (view.propositionItems.length ? propDone / view.propositionItems.length * 100 : 0) + '%' }"></i></div><div class="between t-xs"><span>关系项</span><span>{{ relDone }}/{{ view.relationItems.length }}</span></div><div class="progress warm"><i :style="{ width: (view.relationItems.length ? relDone / view.relationItems.length * 100 : 0) + '%' }"></i></div><p class="muted t-xs">未决专家问题会阻止阶段完成；专家确认后返回原裁定项，仍需裁定者作出决定。</p><button v-if="!readOnly" class="btn btn-secondary" :disabled="busy || pending" @click="targets = []; reason = ''; returnDialog = true">退回标注员补正</button></div></div>
   <div class="card"><div class="card-body"><b>{{ active === 'FINAL_REVIEW' ? '两阶段裁定已完成' : '请先完成两阶段裁定' }}</b><p class="muted t-sm">{{ active === 'FINAL_REVIEW' ? '已进入待最终审查阶段，可生成独立最终快照并核查来源。' : '全部项目确认后进入最终审查。' }}</p><router-link v-if="active === 'FINAL_REVIEW'" class="btn btn-primary" :to="{ path: '/final-review', query: { sessionId: view.session.id } }">进入最终审查</router-link><button v-else class="btn btn-primary" disabled>最终审查</button></div></div><button class="btn btn-secondary" :disabled="busy" @click="load()">刷新会话</button>
  </aside>
 </section>
 <el-dialog :model-value="!!choice" :title="choice?.kind === 'PROPOSITION' ? '本地命题决定（待团队评审）' : '确认关系决定'" width="480px" :show-close="!busy && !pending" :close-on-click-modal="!busy && !pending" @close="choice = undefined"><p>确认 {{ decisionNames[choice?.body.action || ''] }}，原始来源版本会保留。</p><label for="decision-reason">裁定理由</label><textarea id="decision-reason" v-model="reason" class="textarea" :disabled="busy || pending" /><template #footer><button class="btn btn-secondary" :disabled="busy || pending" @click="choice = undefined">取消</button><button class="btn btn-primary" :disabled="busy" @click="mutate('decision')">{{ busy ? '正在保存…' : pending ? '重试相同决定' : '确认保存' }}</button></template></el-dialog>
 <el-dialog v-model="returnDialog" title="退回指定标注员补正" width="480px" :show-close="!busy && !pending" :close-on-click-modal="!busy && !pending"><p>原会话与来源版本保留，只恢复指定成员的草稿。补交后建立新会话。</p><label v-for="s in view?.snapshots" :key="s.version.assignmentId" class="version-option"><input type="checkbox" v-model="targets" :value="s.version.assignmentId" :disabled="busy || pending" />{{ s.version.displayCode }} 的标注员</label><label for="return-reason">补正理由</label><textarea id="return-reason" v-model="reason" class="textarea" :disabled="busy || pending" /><template #footer><button class="btn btn-secondary" :disabled="busy || pending" @click="returnDialog = false">取消</button><button class="btn btn-primary" :disabled="busy || !targets.length || !reason.trim()" @click="mutate('return')">{{ pending ? '重试相同退回' : '确认退回' }}</button></template></el-dialog>
</template>
<style scoped>
.t-h1{margin:6px 0}.page-head p{margin-top:6px}.phase-card{margin-bottom:20px}.steps{justify-content:flex-start;gap:20px}.step:not(:last-child)::after{content:"";width:40px;height:1px;background:var(--border);margin-left:12px}.conflict-layout{grid-template-columns:1.7fr 1fr;align-items:start}.cf-row{border:1px solid var(--border);border-radius:var(--r-md);margin:14px 0;overflow:hidden}.cf-row.conflict{border-color:color-mix(in oklch,var(--danger) 30%,transparent)}.cf-head{display:flex;align-items:center;gap:10px;padding:9px 12px;background:var(--surface-2);border-bottom:1px solid var(--border);flex-wrap:wrap}.cf-body{display:grid;overflow-x:auto}.cf-side{padding:11px 13px;font-size:13px;line-height:1.6;border-right:1px solid var(--border)}.who{font-size:11.5px;color:var(--muted);margin-bottom:7px}.candidate p{margin:8px 0;white-space:pre-wrap}.candidate+.candidate{margin-top:14px}.cf-decide{display:flex;gap:8px;align-items:center;padding:9px 12px;border-top:1px dashed var(--border-strong);flex-wrap:wrap}.locked-card{opacity:.6}.source-row{display:flex;align-items:center;gap:10px;padding:10px 0;border-bottom:1px solid var(--border)}.source-row>a{margin-left:auto}.source-row .avatar{flex-shrink:0}.progress{margin:8px 0 14px}.card-body>p{margin:12px 0}.version-option{display:flex;align-items:center;gap:10px;padding:12px}.textarea{width:100%;margin-top:8px;min-height:90px}.note{margin-bottom:14px}.mono{overflow-wrap:anywhere}@media(max-width:980px){.conflict-layout{grid-template-columns:1fr}}@media(max-width:600px){.steps{flex-direction:column;align-items:flex-start}.page-head{gap:12px;flex-wrap:wrap}}
</style>
