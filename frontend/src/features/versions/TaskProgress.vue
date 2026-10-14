<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import LocalSession from './LocalSession.vue'
import { getLocalContext, getProgress, errorMessage, statusName } from './api'
import type { LocalContext } from './types'
import type { T13Success } from '@/generated/api-client/types.gen'

const route = useRoute()
const context = ref<LocalContext>()
const progress = ref<T13Success['data']>()
const loading = ref(false), error = ref('')
const taskId = computed(() => String(route.params.taskId || context.value?.tasks[0]?.id || ''))
const task = computed(() => context.value?.tasks.find(t => t.id === taskId.value))
const arbiter = computed(() => context.value?.roles.includes('ARBITER'))
const total = computed(() => progress.value?.items.reduce((a, x) => ({ got: a.got + x.submittedCount, need: a.need + x.requiredAnnotators }), { got: 0, need: 0 }))
async function load() {
  loading.value = true; error.value = ''; progress.value = undefined
  try {
    if (import.meta.env.DEV) context.value = await getLocalContext()
    if (!taskId.value) throw new Error('请选择任务；任务列表将在成员 A 接入后提供')
    progress.value = await getProgress(taskId.value)
  } catch (e) { error.value = errorMessage(e) } finally { loading.value = false }
}
const doc = (id: string) => context.value?.taskDocuments.find(d => d.id === id)
const draft = (id: string) => context.value?.drafts.find(d => d.taskDocumentId === id)
onMounted(load)
</script>
<template>
  <LocalSession />
  <div class="page-head between"><div><div class="eyebrow">TASK DETAIL</div><h1 class="t-h1">任务详情</h1><p class="muted t-sm">查看每个案例已收到的独立标注版本数（已收到 X / 需要 N）。</p></div><button class="btn btn-secondary" @click="load" :disabled="loading">刷新进度</button></div>
  <div v-if="loading" class="card card-body muted" role="status">正在读取任务进度…</div>
  <div v-else-if="error" class="note danger" role="alert">{{ error }} <button class="btn btn-secondary" @click="load">重试</button></div>
  <section v-else-if="progress" class="grid-2 d-task-layout">
    <div class="card"><div class="card-head"><div class="card-title"><span class="dot"></span>案例与标注版本</div><span class="badge accent">{{ progress.items.filter(x => x.submittedCount >= x.requiredAnnotators).length }}/{{ progress.items.length }} 个案例版本齐备</span></div>
      <div class="card-body"><div class="panel between d-total"><span>版本收集总进度</span><b class="mono">{{ total?.got }} / {{ total?.need }} 个版本</b></div>
        <div v-if="!progress.items.length" class="muted">没有获授权的案例</div>
        <article v-for="item in progress.items" :key="item.taskDocumentId" class="case-row">
          <div class="between"><div><b>{{ doc(item.taskDocumentId)?.document.title || '任务案例' }}</b><div class="muted t-xs mono">{{ item.taskDocumentId }}</div></div><span class="badge" :class="item.submittedCount >= item.requiredAnnotators ? 'success' : 'warn'">已收到 {{ item.submittedCount }}/{{ item.requiredAnnotators }}</span></div>
          <div class="progress d-progress"><i :style="{ width: Math.min(100, item.submittedCount / item.requiredAnnotators * 100) + '%' }"></i></div>
          <p class="t-xs muted">{{ statusName(item.status) }} · {{ item.requiredAnnotators }} 份独立标注</p>
          <div v-if="item.submittedCount < item.requiredAnnotators" class="note warn">还差 {{ item.requiredAnnotators - item.submittedCount }} 个版本，满足后该案例方可进入多版本比对与裁定。</div>
          <div class="row d-actions">
            <router-link v-if="arbiter || draft(item.taskDocumentId)" class="btn btn-secondary" :to="`/tasks/${taskId}/cases/${item.taskDocumentId}/versions`">{{ arbiter ? '查看来源版本' : '查看本人版本' }}</router-link>
            <router-link v-if="draft(item.taskDocumentId)?.status === 'ACTIVE'" class="btn btn-primary" :to="`/annotations/${draft(item.taskDocumentId)?.id}/submit`">提交确认</router-link>
            <router-link v-if="arbiter && item.submittedCount >= item.requiredAnnotators" class="btn btn-primary" :to="{ path: '/adjudication/conflict', query: { caseId: item.taskDocumentId } }">进入多版本裁定</router-link>
          </div>
        </article>
      </div>
    </div>
    <div class="stack-5"><div class="card"><div class="card-head"><div class="card-title"><span class="dot"></span>任务概要</div></div><div class="card-body"><b>{{ task?.name || '标注任务' }}</b><p class="t-xs mono muted">{{ taskId }}</p><div class="panel d-summary"><span>任务状态</span><b>{{ task ? statusName(task.status) : '—' }}</b></div><div class="panel d-summary"><span>截止日期</span><b>{{ task?.dueAt ? new Date(task.dueAt).toLocaleString('zh-CN') : '未设置' }}</b></div></div></div>
      <div class="card"><div class="card-head tight"><div class="card-title">标注配置</div></div><div class="card-body stack-3"><div class="panel d-summary"><span>默认每案标注人数</span><b>{{ task?.defaultRequiredAnnotators || '—' }} 人</b></div><div class="panel d-summary"><span>分配方式</span><b>{{ task?.assignmentMode === 'MANUAL' ? '手动分配' : task ? '自动分配' : '—' }}</b></div><div class="panel d-summary"><span>指南版本</span><b>{{ context?.taskDocuments[0]?.guideVersion.versionCode || '—' }}</b></div></div></div>
      <div class="card"><div class="card-head tight"><div class="card-title">任务操作</div></div><div class="card-body muted t-sm">各案例收到所需版本数后，由裁定者对比多版本并确认最终结果。标注员只能查看自己的版本；任务创建者查看聚合进度。</div></div>
    </div>
  </section>
</template>
<style scoped>.d-task-layout{grid-template-columns:1.6fr 1fr;align-items:start}.d-total,.d-summary{padding:11px 13px}.d-summary{display:flex;justify-content:space-between;gap:12px;margin-top:10px}.case-row{padding:20px 0;border-bottom:1px solid var(--border)}.d-progress{margin:12px 0}.d-actions{gap:10px;margin-top:14px;flex-wrap:wrap}.card-head{justify-content:space-between;gap:12px}.t-h1{margin:6px 0}.page-head p{margin-top:6px}@media(max-width:980px){.d-task-layout{grid-template-columns:1fr}}</style>
