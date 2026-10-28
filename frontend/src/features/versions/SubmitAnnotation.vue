<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import LocalSession from './LocalSession.vue'
import { getDraftPreview, submitDraft, mutationAttempt, errorMessage, errorCode } from './api'
import { formula } from './formula'
import type { SubmissionPreview, AnnotationVersion } from './types'
const route = useRoute(), preview = ref<SubmissionPreview>(), submitted = ref<AnnotationVersion>(), loading = ref(false), busy = ref(false), error = ref(''), note = ref(''), confirmed = ref(false), pending = ref(false), confirmDialog = ref(false)
const attempt = mutationAttempt<{ confirmed: boolean; note: string }>()
const distribution = computed(() => {
  if (!preview.value) return []
  const counts = new Map<string, number>(); preview.value.elements.forEach(e => counts.set(e.labelDefinitionId, (counts.get(e.labelDefinitionId) || 0) + 1))
  return preview.value.labels.filter(l => counts.has(l.id)).sort((a, b) => a.sortOrder - b.sortOrder).map(l => ({ ...l, count: counts.get(l.id) || 0 }))
})
const checks = computed(() => {
  const p = preview.value; if (!p) return []
  return [
    { title: '颗粒度对齐完成', note: '已绑定裁定者确认的统一切割版本', ok: p.checkpoint.status === 'CONFIRMED' },
    { title: '要素标注完成', note: '全部要素已分配本指南的有效标签', ok: p.elements.length > 0 && p.elements.every(e => p.labels.some(l => l.id === e.labelDefinitionId && l.code === e.labelCode)) },
    { title: '论证图已校验', note: '提交时由服务端再次检查关系、嵌套与图覆盖', ok: p.graph.status === 'VALID' },
    { title: submitted.value ? '完整版本已保存' : '草稿可提交', note: submitted.value ? '原始提交保持只读，修改前需按规则撤回' : '使用当前草稿版本，正式提交后不可直接编辑', ok: !!submitted.value || p.draft.status === 'ACTIVE' }
  ]
})
const canSubmit = computed(() => !loading.value && !busy.value && !!preview.value && checks.value.every(x => x.ok) && confirmed.value && !submitted.value)
async function load() {
  loading.value = true; error.value = ''; preview.value = undefined; attempt.reset(); pending.value = false
  try {
    preview.value = await getDraftPreview(String(route.params.draftId))
  } catch (e) { error.value = errorMessage(e) } finally { loading.value = false }
}
async function submit() {
  if (!canSubmit.value || !preview.value) return
  busy.value = true; error.value = ''; confirmDialog.value = false
  const current = attempt.get({ confirmed: true, note: note.value }, preview.value.draft.recordVersion); pending.value = true
  try {
    submitted.value = await submitDraft(preview.value.draft.id, current.body, current.key, current.version)
    preview.value.draft.status = 'SUBMITTED'
    attempt.reset(); pending.value = false
  } catch (e) {
    error.value = errorMessage(e)
    if (['VERSION_CONFLICT', 'IDEMPOTENCY_CONFLICT', 'GRAPH_INVALID', 'LOCKED_RESOURCE', 'FORBIDDEN', 'VALIDATION_ERROR'].includes(errorCode(e) || '')) { attempt.reset(); pending.value = false }
  } finally { busy.value = false }
}
onMounted(load)
</script>
<template>
  <LocalSession />
  <div class="page-head between"><div><div class="eyebrow">SUBMIT ANNOTATION</div><h1 class="t-h1">提交标注</h1><p class="muted t-sm">提交前请确认切割、要素、关系与图示均已按指南完成。提交后将进入多版本比对流程。</p></div><router-link class="btn btn-secondary" :to="preview ? `/tasks/${preview.task.id}` : '/tasks'">返回任务详情</router-link></div>
  <div v-if="loading" class="card card-body muted" role="status">正在读取本人草稿…</div>
  <div v-if="error" class="note danger" role="alert">{{ error }} <button class="btn btn-secondary" :disabled="busy" @click="load">刷新草稿</button></div>
  <div v-if="submitted" class="note success" role="status"><div><b>{{ submitted.displayCode }} 提交成功</b><p>已保存完整版本，等待裁定者处理。</p><router-link class="btn btn-secondary" :to="{ path: `/versions/${submitted.id}`, query: { taskId: preview?.task.id, caseId: submitted.taskDocumentId } }">查看已提交版本</router-link></div></div>
  <section v-if="preview" class="grid-2 submit-layout">
    <div class="card"><div class="card-head"><div class="card-title"><span class="dot"></span>标注成果概览</div><span class="badge" :class="checks.every(c => c.ok) && !submitted ? 'success' : 'default'">{{ submitted ? '已提交' : checks.every(c => c.ok) ? '可提交' : '待完善' }}</span></div>
      <div class="card-body"><div class="grid-4"><div v-for="[name, count] in [['要素总数', preview.elements.length], ['关系总数', preview.relations.length], ['支持 S', preview.relations.filter(r => r.relationCode === 'S').length], ['组合 J / 匹配 M', preview.relations.filter(r => ['J', 'M'].includes(r.relationCode)).length]]" :key="String(name)" class="panel stat"><div class="mono strong">{{ count }}</div><span class="muted t-xs">{{ name }}</span></div></div>
        <div class="section-label section-gap">要素类型分布</div><div v-for="label in distribution" :key="label.id" class="distribution"><div class="between t-xs"><span>{{ label.name }} · {{ label.code }}</span><span class="mono">{{ label.count }}</span></div><div class="progress"><i :style="{ width: label.count / preview.elements.length * 100 + '%' }"></i></div></div>
        <div class="section-label section-gap">关系清单</div><div v-if="!preview.relations.length" class="muted t-sm">没有标注关系；是否存在遗漏需人工核对。</div><div v-for="relation in preview.relations" :key="relation.id" class="rel-item"><span class="badge outline">{{ relation.relationCode }}</span><span class="formula">{{ formula(relation, preview.elements, preview.relations, preview.participants) }}</span></div>
        <details class="section-gap"><summary class="muted t-sm">查看完整要素</summary><div v-for="element in preview.elements" :key="element.id" class="element"><b class="mono">{{ element.displayNo || '非命题' }}</b> <span class="badge outline">{{ element.labelCode }}</span><p>{{ element.text }}</p></div></details>
      </div>
    </div>
    <div class="stack-5"><div class="card"><div class="card-head tight"><div class="card-title">提交前核对</div></div><div class="card-body stack-3"><div v-for="check in checks" :key="check.title" class="row check-row"><span class="badge" :class="check.ok ? 'success' : 'warn'">{{ check.ok ? '✓' : '!' }}</span><div><b class="t-sm">{{ check.title }}</b><div class="t-xs muted">{{ check.note }}</div></div></div></div></div>
      <div class="card"><div class="card-head tight"><div class="card-title">提交说明</div></div><div class="card-body"><div class="field"><label for="submit-note">给裁定者的说明（可选）</label><textarea id="submit-note" class="textarea" v-model="note" :disabled="busy || pending || !!submitted" placeholder="如：某个要素的类型存在不确定，请重点裁定。" /></div><label class="check"><input type="checkbox" v-model="confirmed" :disabled="busy || !!submitted" /><span>我确认已按任务指南完成标注，愿对标注质量负责。</span></label><p v-if="pending && !busy" class="note warn">上次提交结果尚未确认。重试会沿用同一份说明，避免重复提交。</p></div><div class="card-foot row footer"><router-link class="btn btn-secondary" :to="`/tasks/${preview.task.id}`">返回任务</router-link><button class="btn btn-primary" :disabled="!canSubmit" @click="confirmDialog = true">{{ busy ? '正在提交…' : '确认提交' }}</button></div></div>
      <div class="note warn">开始裁定前可按规则撤回修改；一旦进入裁定，原版本将保持只读。</div>
    </div>
  </section>
  <el-dialog v-model="confirmDialog" title="确认提交本版本？" width="460px"><p>你的标注将保存为完整版本，与其他标注者的版本一同进入比对。</p><template #footer><button class="btn btn-secondary" @click="confirmDialog = false">继续核对</button><button class="btn btn-primary" :disabled="!canSubmit" @click="submit">确认提交</button></template></el-dialog>
</template>
<style scoped>.submit-layout{grid-template-columns:1.5fr 1fr;align-items:start}.t-h1{margin:6px 0}.card-head{justify-content:space-between;gap:12px}.stat{padding:12px}.stat .strong{font-size:24px;color:var(--accent)}.section-gap{margin:22px 0 10px}.distribution{margin:12px 0}.distribution .progress{margin-top:5px}.check-row{gap:10px;align-items:flex-start}.footer{justify-content:flex-end;gap:10px}.element{padding:14px 0;border-bottom:1px solid var(--border)}.element p{white-space:pre-wrap}.check{gap:8px}.btn+.btn{margin-left:10px}.textarea{width:100%}.note{margin-bottom:16px}@media(max-width:980px){.submit-layout{grid-template-columns:1fr}}</style>
