<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, onBeforeRouteLeave } from 'vue-router'
import { get, post } from '@/api/request'
import LocalSession from '@/features/versions/LocalSession.vue'
import { errorMessage, getLocalContext, mutationAttempt } from '@/features/versions/api'
import { workspace } from './api'
import { formula } from '@/features/versions/formula'
import type { CustomContext, CustomAnnotationResult, J10Request, LabelDefinition, CustomElementInput, Element as AnnotationElement } from '@/generated/api-client/types.gen'
const route = useRoute(), local = import.meta.env.DEV, context = ref<CustomContext>(), labels = ref<LabelDefinition[]>([])
const previewElements = ref<AnnotationElement[]>([]), edited = ref<CustomElementInput[]>([]), result = ref<CustomAnnotationResult>()
const loading = ref(false), busy = ref(false), error = ref(''), reason = ref(''), dirty = ref(false), pending = ref(false)
const sessionId = String(route.query.sessionId || ''), itemId = String(route.query.itemId || ''), kind = String(route.query.itemKind || 'PROPOSITION')
const attempt = mutationAttempt<J10Request>()
const back = computed(() => ({ path: '/adjudication/conflict', query: { sessionId, ...(result.value ? { itemId, itemKind: kind, customAnnotationId: result.value.annotation.id } : {}) } }))
const editable = computed(() => !local && context.value?.allowedActions.includes('CUSTOM') && kind === 'PROPOSITION' && !result.value && !pending.value)
async function load() {
 loading.value = true; error.value = ''; attempt.reset(); pending.value = false
 try {
  if (!sessionId || !itemId || !['PROPOSITION', 'RELATION'].includes(kind)) throw new Error('请从冲突项进入自定义工作台')
  if (local) {
   await getLocalContext(); const w = await workspace(sessionId); const item = (kind === 'PROPOSITION' ? w.propositionItems : w.relationItems).find(i => i.id === itemId)
   if (!item) throw new Error('当前裁定项不存在')
   labels.value = w.labels || []; previewElements.value = w.snapshots.flatMap(s => s.elements).filter(e => 'candidateElementIds' in item ? item.candidateElementIds.includes(e.id) : w.snapshots.some(s => s.participants.some(p => 'candidateRelationIds' in item && item.candidateRelationIds.includes(p.relationId) && p.targetId === e.id)))
   const first = previewElements.value[0]?.ownerId; const selected = previewElements.value.filter(e => e.ownerId === first)
   edited.value = selected.map((e, i) => ({ clientKey: `element-${i + 1}`, startOffset: e.startOffset, endOffset: e.endOffset, labelCode: e.labelCode, sourceElementIds: [e.id] }))
   context.value = { session: w.session, itemKind: kind as 'PROPOSITION' | 'RELATION', item, sourceVersions: w.snapshots.map(s => s.version), candidateElements: previewElements.value, candidateRelations: kind === 'RELATION' ? w.snapshots.flatMap(s => s.relations).filter(r => 'candidateRelationIds' in item && item.candidateRelationIds.includes(r.id)) : [], candidateParticipants: w.snapshots.flatMap(s => s.participants), documentTextVersion: w.documentTextVersion!, annotationScope: w.annotationScope!, guideVersion: w.guideVersion!, allowedActions: [] }
  } else {
   context.value = await get<CustomContext>(`/adjudication-sessions/${sessionId}/custom-context`, { itemKind: kind, itemId })
   labels.value = (await get<{ items: LabelDefinition[] }>(`/guide-versions/${context.value.guideVersion.id}/labels`)).items
   previewElements.value = context.value.candidateElements
   const first = previewElements.value[0]?.ownerId
   edited.value = previewElements.value.filter(e => e.ownerId === first).map((e, i) => ({ clientKey: `element-${i + 1}`, startOffset: e.startOffset, endOffset: e.endOffset, labelCode: e.labelCode, sourceElementIds: [e.id] }))
  }
 } catch (e) { error.value = errorMessage(e) } finally { loading.value = false }
}
async function save() {
 if (!context.value || !reason.value.trim() || busy.value || local || kind !== 'PROPOSITION') return
 busy.value = true; error.value = ''; const a = attempt.get({ itemKind: 'PROPOSITION', itemId, customElements: edited.value, customRelations: [], reason: reason.value.trim() }, 1); pending.value = true
 try { result.value = await post<CustomAnnotationResult>(`/adjudication-sessions/${sessionId}/custom-annotations`, a.body, { headers: { 'Idempotency-Key': a.key } }); dirty.value = false; pending.value = false; attempt.reset() }
 catch (e) { error.value = errorMessage(e) } finally { busy.value = false }
}
onBeforeRouteLeave(() => !dirty.value || window.confirm('尚有未保存的修改，确定返回？'))
function leaving(event: BeforeUnloadEvent) { if (dirty.value) { event.preventDefault(); event.returnValue = '' } }
import { onBeforeUnmount } from 'vue'
onMounted(() => { load(); window.addEventListener('beforeunload', leaving) })
onBeforeUnmount(() => window.removeEventListener('beforeunload', leaving))
</script>
<template>
 <LocalSession />
 <div class="page-head between"><div><div class="eyebrow">ARBITER ANNOTATION</div><h1 class="t-h1">裁定者自定义标注</h1><p class="muted t-sm">参考来源候选，在独立快照中补正；保存快照后仍需回到冲突项确认采用。</p></div><router-link class="btn btn-secondary" :to="back">返回冲突项</router-link></div>
 <div v-if="loading" class="card card-body muted">正在读取编辑上下文…</div><div v-if="error" class="note danger" role="alert">{{ error }}</div>
 <div v-if="local" class="note warn">本地预览来源与编辑区域。B 的 J09/J10 和 C 的图组件尚未接入，本页不模拟正式保存成功。</div>
 <div v-if="result" class="note accent">独立快照已保存。返回冲突项后确认采用，才能形成裁定决定。</div>
 <section v-if="context && !loading" class="grid-2 arbiter-layout">
  <div class="stack-5"><div class="card"><div class="card-head"><div class="card-title"><span class="dot"></span>原文与来源候选</div><span class="badge accent">{{ kind === 'PROPOSITION' ? '命题 / 非命题' : '关系' }}</span></div><div class="card-body"><p v-if="context.documentTextVersion.id" class="document-text">{{ context.documentTextVersion.normalizedText }}</p></div></div>
  </div><aside class="stack-5"><div class="card"><div class="card-head"><div class="card-title">重新标注 · 冲突条目</div></div><div class="card-body"><article v-for="e in previewElements" :key="e.id" class="element-row"><div class="row"><b>{{ context.sourceVersions.find(v => v.id === e.ownerId)?.displayCode }}</b><span class="badge outline">{{ e.labelCode }}</span><span class="t-xs muted">[{{ e.startOffset }}, {{ e.endOffset }})</span></div><p>{{ e.text }}</p></article><div v-for="r in context.candidateRelations" :key="r.id" class="element-row mono">{{ formula(r, context.candidateElements, context.candidateRelations, context.candidateParticipants) }}</div><div v-for="(e, i) in edited" :key="e.clientKey" class="element-row"><b>要素 {{ i + 1 }}</b><p class="document-text">{{ context.documentTextVersion.normalizedText?.slice(e.startOffset, e.endOffset) }}</p><div class="fields"><label>起始位置<input type="number" class="input" v-model.number="e.startOffset" :disabled="!editable" @input="dirty = true" /></label><label>结束位置<input type="number" class="input" v-model.number="e.endOffset" :disabled="!editable" @input="dirty = true" /></label><label>要素类型<select class="select" v-model="e.labelCode" :disabled="!editable" @change="dirty = true"><option v-if="!labels.length" :value="e.labelCode">{{ e.labelCode }}</option><option v-for="label in labels" :key="label.id" :value="label.code">{{ label.code }}</option></select></label></div></div><p v-if="!edited.length" class="muted">此项没有要素候选。</p></div></div>
   <div v-if="kind === 'RELATION'" class="card"><div class="card-head"><div class="card-title">论证图</div></div><div class="card-body graph-slot muted">等待 C 接入统一命题与关系图编辑组件</div></div>
   <div class="card"><div class="card-head"><div class="card-title">当前冲突项</div></div><div class="card-body"><p>会话阶段：{{ context.session.currentPhase }}</p><p>来源：{{ context.sourceVersions.map(v => v.displayCode).join(' / ') }}</p><p class="muted t-xs mono">{{ itemId }}</p></div></div><div class="card"><div class="card-head"><div class="card-title">保存独立快照</div></div><div class="card-body"><label for="custom-reason">补正理由</label><textarea id="custom-reason" class="textarea" v-model="reason" :disabled="!editable" @input="dirty = true" /><p class="muted t-xs">原始来源版本保持只读。保存自定义快照不等于保存裁定决定。</p><button class="btn btn-primary" :disabled="busy || local || kind !== 'PROPOSITION' || !!result || !reason.trim() || !context.allowedActions.includes('CUSTOM')" @click="save">{{ pending ? '重试相同快照' : '保存独立快照' }}</button><p v-if="kind === 'RELATION'" class="muted t-xs">关系编辑在 C 的结构化编辑及校验接入后开放。</p></div></div></aside>
 </section>
</template>
<style scoped>.arbiter-layout{grid-template-columns:46% minmax(0,1fr);align-items:start}.t-h1{margin:6px 0}.note{margin-bottom:18px}.element-row{padding:16px 0;border-bottom:1px solid var(--border)}.element-row p{margin-top:8px;white-space:pre-wrap}.row{gap:10px}.fields{display:flex;gap:12px;flex-wrap:wrap;margin-top:12px}.fields label{flex:1;min-width:110px}.input,.select{width:100%}.textarea{width:100%;margin:8px 0;min-height:100px}.card-body>p{margin:12px 0}.document-text{white-space:pre-wrap}.graph-slot{min-height:200px;display:flex;align-items:center;justify-content:center;background:var(--surface-2)}.mono{overflow-wrap:anywhere}@media(max-width:980px){.arbiter-layout{grid-template-columns:1fr}}</style>
