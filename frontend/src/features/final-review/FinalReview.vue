<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { openCorrection } from './api'
import { get } from '@/api/request'
import LocalSession from '@/features/versions/LocalSession.vue'
import { getLocalContext, errorMessage, errorCode, mutationAttempt, formatTime } from '@/features/versions/api'
import { localSessions } from '@/features/adjudication/api'
import type { AdjudicationSession } from '@/generated/api-client/types.gen'
import { formula } from '@/features/versions/formula'
import FinalGraphPreview from './FinalGraphPreview.vue'
import { getFinal, createFinal, listLocalFinals, transitionFinal, type FinalWorkspace, type ReviewAction, type Provenance } from './api'
const route = useRoute(), router = useRouter(), local = import.meta.env.DEV
const view = ref<FinalWorkspace>(), candidates = ref<AdjudicationSession[]>([]), selectedSession = ref(''), nextVersionNumber = ref(1)
const loading = ref(false), busy = ref(false), error = ref(''), notice = ref(''), dialog = ref<ReviewAction['operation']>(), reason = ref(''), confirmed = ref(false), pending = ref(false)
const correctionAttempt = mutationAttempt<{id:string;reason:string}>()
const createAttempt = mutationAttempt<{ sessionId: string; number: number }>(), actionAttempt = mutationAttempt<ReviewAction>()
const version = computed(() => view.value?.snapshot.version), snapshot = computed(() => view.value?.snapshot)
const stateNames: Record<string, string> = { DRAFT: '待送审草稿', IN_REVIEW: '审查中', APPROVED: '已批准 · 尚未入库', IN_DATABASE: '已入库' }
const operationNames: Record<string, string> = { FV01: '生成独立快照', FV03: '提交审查', FV04: '退回修改', FV05: '批准最终版本', FV06: '确认入库（试行）', TRIAL_FV05:'试行批准',TRIAL_FV06:'试行入库' }
const canRebuild = computed(() => view.value?.latest && version.value?.status === 'DRAFT' && !version.value.correctionSessionId && view.value.history.some(h => h.operation === 'FV04'))
const sourceLabel = (id: string) => view.value?.sources?.find(s => s.id === id)?.displayCode || id
const origin = (id: string) => version.value?.provenance.filter(p => p.resultId === id) || []
const provenanceName = (p: Provenance) => `${p.sourceVersionIds.map(sourceLabel).join(' / ')}${p.expertIssueId ? ' · 专家依据' : ''}`
function resetAttempts() { pending.value = false; createAttempt.reset(); actionAttempt.reset() }
async function load(reset = true) {
 loading.value = true; error.value = ''; view.value = undefined
 if (reset) { resetAttempts(); dialog.value = undefined }
 try {
  if (local) await getLocalContext()
  if (route.params.finalVersionId) { view.value = await getFinal(String(route.params.finalVersionId)); return }
  if (route.query.sessionId) {
   selectedSession.value = String(route.query.sessionId)
   if (local) { const versions = await listLocalFinals(selectedSession.value); nextVersionNumber.value=versions.nextVersionNumber; if (versions.items.length) { await router.replace(`/final-review/${versions.items[versions.items.length - 1]!.id}`); return } }
   candidates.value = [await get<AdjudicationSession>(`/adjudication-sessions/${selectedSession.value}`)]
  } else if (local) {
   const context = await getLocalContext(), sessions = await Promise.all(context.taskDocuments.map(d => localSessions(d.id)))
   candidates.value = sessions.flatMap(s => s.items).filter(s => s.status === 'FINAL_REVIEW' || s.status === 'COMPLETED')
   if (candidates.value.length === 1) { selectedSession.value = candidates.value[0]!.id; const finals = await listLocalFinals(selectedSession.value); if (finals.items.length) await router.replace(`/final-review/${finals.items[finals.items.length - 1]!.id}`) }
  } else throw new Error('请从已完成两阶段裁定的案例进入最终审查')
 } catch (e) { error.value = errorMessage(e) } finally { loading.value = false }
}
async function generate(rebuild = false) {
 if (busy.value) return
 busy.value = true; error.value = ''
 const a = createAttempt.get({ sessionId: rebuild ? version.value!.sessionId : selectedSession.value, number: rebuild ? version.value!.versionNumber + 1 : nextVersionNumber.value }, 1); pending.value = true
 try { const result = await createFinal(a.body.sessionId, a.body.number, a.key); resetAttempts(); notice.value = '独立最终快照已生成，尚未送审'; await router.push(`/final-review/${result.id}`) }
 catch (e) { error.value = errorMessage(e); if (errorCode(e)) resetAttempts() } finally { busy.value = false }
}
function open(operation: ReviewAction['operation']) { if (pending.value || busy.value) return; dialog.value = operation; reason.value = ''; confirmed.value = false }
async function execute() {
 if (!version.value || !dialog.value || busy.value) return
 const command: ReviewAction = dialog.value === 'return' ? { operation: 'return', body: { reason: reason.value.trim() } } : dialog.value === 'approve' || dialog.value === 'commit' ? { operation: dialog.value, trial:!!view.value?.quality.trialReady&&!view.value?.quality.formalReady, body: { confirmed: true } } : { operation: 'review', body: { action: 'SUBMIT' } }
 busy.value = true; error.value = ''; const a = actionAttempt.get(command, version.value.recordVersion); pending.value = true
 try { const result = await transitionFinal(version.value.id, a.body, a.key, a.version); notice.value = result.status === 'IN_DATABASE' ? '试行入库已完成，最终结果只读，永久审计已同步保存' : result.status === 'APPROVED' ? '最终版本已批准，尚未入库，也不能导出科研数据' : result.status === 'DRAFT' ? '已退回草稿，理由与审查轨迹已保留' : '已提交最终审查'; resetAttempts(); dialog.value = undefined; await load(false) }
 catch (e) { error.value = errorMessage(e); if (errorCode(e)) resetAttempts() } finally { busy.value = false }
}
async function correction() {
 if(!version.value || busy.value) return
 try {
  let a
  if(correctionAttempt.pending()) a=correctionAttempt.get({id:version.value.id,reason:''},version.value.recordVersion)
  else { const r=await ElMessageBox.prompt('填写需要实质调整的内容。新会话需重新完成命题及关系裁定，旧快照保留。','创建补正轮次（试行）',{inputValidator:v=>!!v?.trim()&&v.length<=2000||'请输入 1–2000 字'}); a=correctionAttempt.get({id:version.value.id,reason:r.value.trim()},version.value.recordVersion) }
  busy.value=true
  const result=await openCorrection(a.body.id,a.body.reason,a.key,a.version);correctionAttempt.reset()
  await router.push({path:'/adjudication/conflict',query:{sessionId:result.sessionId,caseId:version.value.taskDocumentId}})
 }catch(e){if(e!=='cancel'&&e!=='close'){error.value=errorMessage(e);if(errorCode(e))correctionAttempt.reset()}}finally{busy.value=false}
}
watch(() => route.fullPath, () => load(), { immediate: true })
</script>
<template>
 <LocalSession description="入库与审计按 D 分支提案试行，待团队评审；正式验证依赖的批准门禁仍保留" />
 <div class="page-head between"><div><div class="eyebrow">FINAL REVIEW</div><h1 class="t-h1">最终版本审查</h1><p class="t-sm muted">审查由裁定决定生成的独立最终快照。批准与入库分开执行，原始来源永久保留。</p></div><div v-if="view" class="row head-actions"><router-link v-if="local && version" class="btn btn-secondary" :to="{path:'/audit',query:{objectId:version.id}}">入库审计</router-link><button v-if="version?.status === 'IN_REVIEW'" class="btn btn-secondary" :disabled="busy || pending || !view.latest" @click="open('return')">退回修改</button><button v-if="version?.status === 'DRAFT'" class="btn btn-primary" :disabled="busy || pending || !view.latest || !view.quality.canSubmit" @click="open('review')">提交最终审查</button><button v-else-if="version?.status === 'APPROVED' && local" class="btn btn-primary" :disabled="busy || pending || !view.latest || (!view.quality.formalReady && !view.quality.trialReady) || !view.quality.healthy" @click="open('commit')">确认入库（试行）</button><span v-else-if="version?.status === 'IN_DATABASE'" class="badge success">已入库 · 永久只读</span><button v-else class="btn btn-primary" :disabled="busy || pending || !view.latest || (!view.quality.canApprove && !(local && view.quality.canApproveTrial))" @click="open('approve')">{{ version?.status === 'APPROVED' ? '已批准 · 尚未入库' : view.quality.trialReady ? '试行批准最终版本' : '批准最终版本' }}</button></div></div>
 <div v-if="loading" class="card card-body muted" role="status">正在读取最终快照与质量检查…</div>
 <div v-if="error" class="note danger" role="alert">{{ error }} <button class="btn btn-secondary" :disabled="busy" @click="load()">刷新</button></div>
 <div v-if="notice" class="note accent" role="status">{{ notice }}</div>
 <div v-if="version?.correctionSessionId" class="note accent">此退回快照已开启独立补正轮次，原内容保留。<router-link :to="{path:'/adjudication/conflict',query:{sessionId:version.correctionSessionId,caseId:version.taskDocumentId}}">进入补正裁定</router-link></div>
 <div v-if="local && canRebuild" class="note warn">需要修改命题或关系时，请创建独立补正轮次。<button class="btn btn-secondary" :disabled="busy" @click="correction">{{ correctionAttempt.pending() ? '重试创建补正轮次' : '创建补正轮次（试行）' }}</button></div>
 <div v-if="pending && !busy" class="note warn">上次操作结果尚未确认。<button v-if="dialog" class="btn btn-secondary" @click="execute">重试相同操作</button><button v-else class="btn btn-secondary" @click="generate(!!version)">重试生成</button></div>
 <div v-if="!view && !loading" class="card"><div class="card-head"><div class="card-title">生成待审最终快照</div></div><div class="card-body stack-3"><p>只有全部命题、非命题及关系项已有生效决定的会话才能生成快照。</p><label for="final-session">选择裁定会话</label><select id="final-session" v-model="selectedSession" class="select" :disabled="busy || pending"><option value="">请选择</option><option v-for="s in candidates" :key="s.id" :value="s.id">案例 {{ s.taskDocumentId }} · {{ s.id }}</option></select><p v-if="!candidates.length" class="muted">尚无待最终审查会话，请先完成两阶段裁定。</p><button class="btn btn-primary" :disabled="busy || !selectedSession" @click="generate()">{{ busy ? '正在生成…' : '生成或读取首个最终快照' }}</button></div></div>
 <template v-if="view && snapshot && version && !loading">
  <div v-if="version.status !== 'IN_DATABASE' && !view.quality.formalReady" class="note warn">{{ view.quality.mode === 'LOCAL_PREVIEW' ? '本地联调：可核查完整快照、送审和退回；切割/命题映射及正式图验证尚未接入，服务端禁止批准与入库。' : view.quality.trialReady ? '真实输入已复算，可按 D 分支授权方案试行批准与入库；生产模型仍待团队评审。' : '真实结构验证已接入，当前缺少完整试行凭证；禁止批准与入库。' }}</div>
  <div v-if="!view.latest" class="note warn">这是保留的历史最终快照，内容与审查记录只读。</div>
  <div v-if="version.status === 'APPROVED'" class="note accent">已批准，尚未入库。当前版本不具备科研数据导出资格。</div>
  <div v-if="version.status === 'IN_DATABASE'" class="note accent">已入库，当前最终结果永久只读。入库时间：{{ version.committedAt ? formatTime(version.committedAt) : '—' }}。需要重新标注时须通过新任务保留原任务与最终版本双来源；原任务不能直接改写。</div>
  <section class="grid-2 review-layout">
   <div class="stack-5">
    <div class="card"><div class="card-head between"><div class="card-title"><span class="dot"></span>最终要素清单</div><span class="badge success">已合并裁定结果 · F{{ version.versionNumber }}</span></div><div class="tbl-wrap"><table class="tbl"><thead><tr><th>序号</th><th>文本</th><th>类型</th><th>裁定来源</th></tr></thead><tbody><tr v-for="e in snapshot.elements" :key="e.id"><td class="mono strong">{{ e.displayNo || '非命题' }}</td><td class="cell-main text-cell">{{ e.text }}<div class="t-xs muted">[{{ e.startOffset }}, {{ e.endOffset }})</div></td><td><span class="badge outline">{{ e.labelCode }}</span></td><td><details><summary>{{ origin(e.id).map(provenanceName).join('；') || '来源缺失' }}</summary><div v-for="p in origin(e.id)" :key="p.decisionId || e.id" class="t-xs source-detail"><p>来源对象：{{ p.sourceObjectIds.join(' / ') }}</p><p>决定：{{ p.decisionId }}</p><p v-if="p.expertIssueId">专家事项：{{ p.expertIssueId }}</p></div></details></td></tr></tbody></table><p v-if="!snapshot.elements.length" class="empty muted">没有最终要素，请查看质量检查。</p></div></div>
    <div class="card"><div class="card-head"><div class="card-title"><span class="dot"></span>最终关系清单</div></div><div class="card-body"><p v-if="!snapshot.relations.length" class="muted">本最终版本未采纳关系，排除依据保留在来源记录中。</p><div v-for="r in snapshot.relations" :key="r.id" class="rel-item"><span class="badge outline">{{ r.relationCode }}</span><span class="formula">{{ formula(r, snapshot.elements, snapshot.relations, snapshot.participants) }}</span><span class="t-xs muted">{{ origin(r.id).map(provenanceName).join('；') }}</span></div></div></div>
    <FinalGraphPreview :snapshot="snapshot" :graph="view.graphView" />
    <div class="card"><div class="card-head"><div class="card-title">排除结果与来源</div><span class="badge default">{{ view.summary.excluded }} 条</span></div><div class="card-body"><p v-if="!view.summary.excluded" class="muted">没有排除结果。</p><details v-for="(p, i) in version.provenance.filter(p => p.resultKind.startsWith('EXCLUDED_'))" :key="i" class="excluded"><summary>{{ p.resultKind === 'EXCLUDED_ELEMENT' ? '排除要素候选' : '排除关系候选' }} · {{ provenanceName(p) }}</summary><p class="t-xs source-detail">对象：{{ p.sourceObjectIds.join(' / ') }}<br />决定：{{ p.decisionId }}</p></details></div></div>
   </div>
   <aside class="stack-5">
    <div class="card"><div class="card-head tight"><div class="card-title">文档信息</div></div><div class="card-body stack-3"><div class="between"><span class="muted">文书</span><b>{{ view.document.title }}</b></div><div v-if="view.document.caseNumber" class="between"><span class="muted">案号</span><b>{{ view.document.caseNumber }}</b></div><div class="between"><span class="muted">指南版本</span><b>{{ view.guideVersion.versionCode }}</b></div><div class="between"><span class="muted">生成时间</span><b>{{ formatTime(version.createdAt) }}</b></div><div class="between"><span class="muted">版本状态</span><span class="badge" :class="version.status === 'APPROVED' ? 'success' : 'accent'">{{ stateNames[version.status] }}</span></div><p class="mono t-xs muted">{{ version.id }}</p><p v-if="version.approvedAt" class="t-xs">批准时间：{{ formatTime(version.approvedAt) }}</p></div></div>
    <div class="card"><div class="card-head tight"><div class="card-title">裁定摘要</div></div><div class="card-body stack-3"><div class="between"><span>命题及非命题项</span><b>{{ view.summary.propositions }} 项已决定</b></div><div class="between"><span>关系项</span><b>{{ view.summary.relations }} 项已决定</b></div><div class="between"><span>专家介入</span><b>{{ view.summary.expertReviews }} 次复核</b></div><div class="between"><span>来源版本</span><b>{{ view.session.sourceVersionIds.map(sourceLabel).join(' / ') }}</b></div><router-link class="btn btn-secondary" :to="{ path: '/adjudication/conflict', query: { sessionId: version.sessionId } }">查看裁定决定</router-link></div></div>
    <div class="card"><div class="card-head tight"><div class="card-title">质量检查</div></div><div class="card-body stack-3"><div v-for="(check, i) in view.quality.checks" :key="i" class="row quality-row"><span class="badge" :class="check.code.endsWith('PENDING') ? 'warn' : check.passed ? 'success' : 'danger'">{{ check.code.endsWith('PENDING') ? '待评审' : check.passed ? '✓' : '!' }}</span><span class="t-sm">{{ check.message }}</span></div><p v-if="!view.quality.formalReady" class="muted t-xs">正式生产批准仍待模型评审；试行批准必须通过独立输入复算、当前决定和专家资格核验。</p><button class="btn btn-secondary" :disabled="busy" @click="load()">重新检查</button></div></div>
    <div class="card"><div class="card-head tight"><div class="card-title">审查轨迹</div></div><div class="card-body"><article v-for="h in view.history" :key="h.id" class="history-row"><b>{{ operationNames[h.operation] || h.operation }}</b><span class="t-xs muted">{{ formatTime(h.createdAt) }}</span><p class="t-sm">{{ stateNames[h.after.status] }}</p><p v-if="h.command.reason" class="t-sm">理由：{{ h.command.reason }}</p></article><p v-if="!view.history.length" class="muted">尚无审查记录。</p></div></div>
    <div v-if="canRebuild" class="card"><div class="card-body"><p class="t-sm">依赖或裁定结果完成补正后，可重新生成独立快照。旧内容与退回理由会保留。</p><button class="btn btn-secondary" :disabled="busy || pending" @click="generate(true)">重建下一快照 F{{ version.versionNumber + 1 }}</button><p class="muted t-xs">这里不直接编辑原版；命题重裁与拆分合并修订仍需已确认的模型及成员接口。</p></div></div>
   </aside>
  </section>
 </template>
 <el-dialog :model-value="!!dialog" :title="dialog === 'return' ? '退回最终版修改' : dialog === 'approve' ? (view?.quality.trialReady ? '试行批准最终版本' : '批准最终版本') : dialog === 'commit' ? '确认入库（试行）' : '提交最终审查'" width="500px" :show-close="!busy && !pending" :close-on-click-modal="!busy && !pending" :close-on-press-escape="!busy && !pending" @close="dialog = undefined"><template v-if="dialog === 'return'"><p>将恢复为待送审草稿，保留本次快照和全部审查轨迹。</p><label for="final-return-reason">退回理由</label><textarea id="final-return-reason" class="textarea" v-model="reason" :disabled="busy || pending" /></template><template v-else-if="dialog === 'approve' || dialog === 'commit'"><p>{{ dialog === 'commit' ? '入库会原子保存永久审计，并锁定本案例结果；任务在全部案例入库后完成。此功能仍待团队评审。' : view?.quality.trialReady ? '本次按 D 分支授权方案试行批准，验证证据将独立保留；生产模型仍待团队评审。入库须另行确认。' : '批准后保持只读；入库与导出仍需后续独立操作。' }}</p><label class="row confirm-row"><input type="checkbox" v-model="confirmed" :disabled="busy || pending" />{{ dialog === 'commit' ? '我确认将当前已批准最终版本入库并保留完整来源与审计' : '我已审查全部要素、关系、独立图结构和来源依据' }}</label></template><p v-else>提交前将重新检查文本、标签、关系、来源与专家状态。联调送审不会绕过正式批准门禁。</p><template #footer><button class="btn btn-secondary" :disabled="busy || pending" @click="dialog = undefined">取消</button><button class="btn btn-primary" :disabled="busy || dialog === 'return' && !reason.trim() || (dialog === 'approve' || dialog === 'commit') && !confirmed" @click="execute">{{ busy ? '正在处理…' : pending ? '重试相同操作' : '确认' }}</button></template></el-dialog>
</template>
<style scoped>.review-layout{grid-template-columns:1.7fr 1fr;align-items:start}.t-h1{margin:6px 0}.page-head p{margin-top:6px}.head-actions{gap:10px;flex-wrap:wrap}.page-head{gap:16px}.note{margin-bottom:18px}.card-head{gap:12px;justify-content:space-between}.text-cell{min-width:190px;max-width:360px;white-space:pre-wrap}.tbl td{vertical-align:top}.tbl-wrap{overflow-x:auto}.source-detail{overflow-wrap:anywhere;max-width:320px;line-height:1.7}.rel-item{flex-wrap:wrap}.excluded{padding:12px 0;border-bottom:1px solid var(--border)}.quality-row{gap:9px;align-items:flex-start}.quality-row .badge{flex-shrink:0}.history-row{padding:12px 0;border-bottom:1px solid var(--border)}.history-row>span{display:block;margin-top:6px}.history-row p{margin-top:7px}.card-body>p{margin:12px 0}.textarea{width:100%;margin-top:8px;min-height:100px}.confirm-row{gap:10px;margin:14px 0;align-items:flex-start}.empty{padding:20px}.mono{overflow-wrap:anywhere}@media(max-width:980px){.review-layout{grid-template-columns:1fr}}@media(max-width:760px){.page-head{align-items:flex-start;flex-direction:column}.head-actions{width:100%}}</style>
