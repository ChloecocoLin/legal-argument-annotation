<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import LocalSession from './LocalSession.vue'
import { getVersion, getLocalContext, withdrawVersion, mutationAttempt, errorMessage, errorCode, formatTime, statusName } from './api'
import { formula } from './formula'
import type { SnapshotView } from './types'
import { useUserStore } from '@/stores/user'
const route = useRoute(), view = ref<SnapshotView>(), actorId = ref(useUserStore().id), error = ref(''), loading = ref(false), busy = ref(false), dialog = ref(false), reason = ref('')
const attempt = mutationAttempt<{ reason: string }>(), pending = ref(false)
const own = computed(() => view.value?.version.submittedById === actorId.value)
const back = computed(() => route.query.taskId && route.query.caseId ? `/tasks/${route.query.taskId}/cases/${route.query.caseId}/versions` : '/tasks')
async function load() {
  loading.value = true; error.value = ''; view.value = undefined; attempt.reset(); pending.value = false
  try {
    if (import.meta.env.DEV) actorId.value = (await getLocalContext()).actorId
    view.value = await getVersion(String(route.params.versionId))
  } catch (e) { error.value = errorMessage(e) } finally { loading.value = false }
}
async function withdraw() {
  if (!view.value || busy.value || !reason.value.trim()) return
  busy.value = true; error.value = ''; const current = attempt.get({ reason: reason.value.trim() }, view.value.version.recordVersion)
  pending.value = true
  try {
    view.value.version = await withdrawVersion(view.value.version.id, current.body, current.key, current.version)
    dialog.value = false; attempt.reset(); pending.value = false
  } catch (e) {
    error.value = errorMessage(e)
    if (['VERSION_CONFLICT', 'IDEMPOTENCY_CONFLICT', 'ALREADY_IN_ADJUDICATION', 'LOCKED_RESOURCE', 'FORBIDDEN'].includes(errorCode(e) || '')) { attempt.reset(); pending.value = false }
  } finally { busy.value = false }
}
onMounted(load)
</script>
<template>
  <LocalSession />
  <div class="page-head between"><div><div class="eyebrow">ANNOTATION VERSION</div><h1 class="t-h1">完整标注版本 {{ view?.version.displayCode }}</h1><p class="muted t-sm">原始提交内容永久保留，展示编号属于本版本。</p></div><router-link class="btn btn-secondary" :to="back">返回版本列表</router-link></div>
  <div v-if="loading" class="card card-body muted" role="status">正在读取完整快照…</div>
  <div v-if="error" class="note danger" role="alert">{{ error }} <button class="btn btn-secondary" :disabled="busy" @click="load">刷新</button></div>
  <section v-if="view" class="grid-2 detail-layout">
    <div class="stack-5"><div class="card"><div class="card-head"><div class="card-title"><span class="dot"></span>要素清单</div><span class="badge accent">{{ view.elements.length }} 个要素</span></div><div class="card-body"><div v-for="element in view.elements" :key="element.id" class="element-row"><div class="row"><b class="mono">{{ element.displayNo || '非命题' }}</b><span class="badge outline">{{ element.labelCode }}</span><span class="muted t-xs">[{{ element.startOffset }}, {{ element.endOffset }})</span></div><p>{{ element.text }}</p><details class="t-xs muted"><summary>查看来源</summary><p class="mono">{{ element.sourceElementIds.join(' / ') }}</p></details></div></div></div>
      <div class="card"><div class="card-head"><div class="card-title"><span class="dot"></span>关系清单</div></div><div class="card-body"><p v-if="!view.relations.length" class="muted">本版本没有标注关系</p><div v-for="relation in view.relations" :key="relation.id" class="rel-item"><span class="badge outline">{{ relation.relationCode }}</span><span class="formula">{{ formula(relation, view.elements, view.relations, view.participants) }}</span></div></div></div>
    </div>
    <div class="stack-5"><div class="card"><div class="card-head"><div class="card-title">版本信息</div><span class="badge" :class="view.version.status === 'WITHDRAWN' ? 'default' : 'success'">{{ statusName(view.version.status) }}</span></div><div class="card-body stack-3"><div><span class="muted t-xs">提交时间</span><p>{{ formatTime(view.version.submittedAt) }}</p></div><div><span class="muted t-xs">版本 ID</span><p class="mono t-xs">{{ view.version.id }}</p></div><div><span class="muted t-xs">指南版本 ID</span><p class="mono t-xs">{{ view.version.guideVersionId }}</p></div><div><span class="muted t-xs">原文版本 ID</span><p class="mono t-xs">{{ view.version.documentTextVersionId }}</p></div></div></div>
      <div class="card"><div class="card-head"><div class="card-title">版本操作</div></div><div class="card-body"><button v-if="own && view.version.status === 'SUBMITTED'" class="btn btn-secondary" @click="dialog = true">撤回修改</button><p v-else class="muted t-sm">当前版本只读</p><p class="muted t-xs">开始裁定或已被裁定会话引用后不能撤回。撤回后保留本版本，后续提交产生新版本。</p></div></div>
    </div>
  </section>
  <el-dialog v-model="dialog" title="撤回本版本" width="460px" :close-on-click-modal="!busy" :show-close="!busy"><p>请说明需要补正的内容。原始提交会保留。</p><label for="withdraw-reason">撤回理由</label><textarea id="withdraw-reason" class="textarea" v-model="reason" :disabled="busy || pending" /><p v-if="pending && !busy" class="note warn">上次请求结果尚未确认，重试会使用相同理由。</p><template #footer><button class="btn btn-secondary" :disabled="busy" @click="dialog = false">取消</button><button class="btn btn-primary" :disabled="busy || !reason.trim()" @click="withdraw">{{ busy ? '正在撤回…' : '确认撤回' }}</button></template></el-dialog>
</template>
<style scoped>.detail-layout{grid-template-columns:1.6fr 1fr;align-items:start}.t-h1{margin:6px 0}.element-row{padding:15px 0;border-bottom:1px solid var(--border)}.element-row .row{gap:12px}.element-row p{margin:10px 0;white-space:pre-wrap}.card-head{justify-content:space-between;gap:12px}.mono{overflow-wrap:anywhere}.textarea{margin-top:8px;width:100%}.btn+.btn{margin-left:10px}@media(max-width:980px){.detail-layout{grid-template-columns:1fr}}</style>
