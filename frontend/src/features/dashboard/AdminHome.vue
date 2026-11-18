<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { get } from '@/api/request'
import type { U08Success } from '@/generated/api-client/types.gen'
import { errorMessage, getLocalContext } from '@/features/versions/api'
import { listExports, type ExportRecord } from '@/features/export/api'
import { formatTime } from '@/features/versions/api'
import LocalSession from '@/features/versions/LocalSession.vue'

const recentExports=ref<ExportRecord[]>([]), exportsError=ref(''), exportsLoading=ref(false)
async function loadRecentExports(){exportsLoading.value=true;exportsError.value='';recentExports.value=[];try{const r=await listExports(1);recentExports.value=r.data.items.slice(0,5)}catch(e){exportsError.value=errorMessage(e)}finally{exportsLoading.value=false}}
const summary = ref<U08Success['data']>(), loading = ref(false), error = ref('')
async function load() {
  summary.value = undefined; loading.value = true; error.value = ''
  try {
    if (import.meta.env.DEV) await getLocalContext()
    summary.value = await get<U08Success['data']>('/dashboard/summary')
  } catch (e) { error.value = errorMessage(e) } finally { loading.value = false }
}
const cards = [
  ['guideCount', '指南总数', '同一指南的多个版本只计一次'],
  ['documentCount', '在库文书', '当前身份范围内的文书'],
  ['taskCount', '标注任务', '当前身份范围内的全部任务'],
  ['pendingReviews', '待裁定与审查', '版本齐备且尚未入库的案例']
] as const
onMounted(()=>{load();loadRecentExports()})
</script>
<template>
  <LocalSession description="查看当前账号权限范围内的统计" />
  <div class="page-head between"><div><div class="eyebrow">ADMIN CONSOLE</div><h1 class="t-h1">管理工作台</h1><p class="t-sm muted">查看指南、文书与任务概况；任务创建者仅查看本人任务范围。</p></div><div class="row"><router-link class="btn btn-secondary" to="/guide">指南管理</router-link><button class="btn btn-primary" :disabled="loading" @click="load();loadRecentExports()">刷新统计</button></div></div>
  <div v-if="loading" class="card card-body muted" role="status">正在读取统计…</div>
  <div v-else-if="error" class="note danger" role="alert">{{ error }} <button class="btn btn-secondary" @click="load">重试</button></div>
  <template v-else-if="summary">
    <section class="grid-4 stats"><div v-for="[key,title,hint] in cards" :key="key" class="card stat-card accent"><div class="stat-k">{{ title }}</div><div class="stat-num">{{ summary[key] }}</div><div class="stat-sub">{{ hint }}</div></div></section>
    <section class="grid-2 dashboard-body">
      <div class="stack-5">
        <div class="card"><div class="card-head"><div class="card-title"><span class="dot"></span>待处理事项</div><span class="badge warn">{{ summary.pendingReviews }} 个案例待裁定或审查</span></div><div class="card-body"><p class="muted t-sm">具体处理入口由当前身份与案例分配决定。管理统计不授予查看他人原始标注的权限。</p><router-link to="/tasks" class="btn btn-secondary action">查看任务进度 →</router-link></div></div>
        <div class="card"><div class="card-head"><div class="card-title"><span class="dot"></span>指南版本</div><router-link to="/guide" class="btn btn-sm btn-ghost">全部版本 →</router-link></div><div class="card-body t-sm muted">指南的版本、标签与关系定义由指南管理模块维护，进入该模块读取详情。</div></div>
        <div class="card"><div class="card-head"><div class="card-title"><span class="dot"></span>最近导出记录</div><router-link to="/export-records" class="btn btn-sm btn-ghost">全部记录 →</router-link></div><div class="card-body t-sm"><p v-if="exportsLoading" role="status">正在读取导出记录…</p><p v-else-if="exportsError" role="alert">{{exportsError}} <button class="btn btn-sm" @click="loadRecentExports">重试</button></p><template v-else><p v-for="record in recentExports" :key="record.id"><router-link :to="{path:'/export-records',query:{id:record.id}}">{{record.format}} · {{formatTime(record.requestedAt)}} · {{({QUEUED:'排队中',RUNNING:'生成中',COMPLETED:'已完成',FAILED:'失败'} as Record<string,string>)[record.status] || record.status}}</router-link></p><p v-if="!recentExports.length">还没有导出。<router-link to="/export">前往导出中心</router-link></p></template></div></div>
      </div>
      <div class="stack-5">
        <div class="card"><div class="card-head"><div class="card-title"><span class="dot"></span>统计范围</div></div><div class="card-body stack-3 t-sm"><p>管理员查看全局聚合；任务创建者查看本人负责的任务、关联文书与指南。</p><p class="muted">待裁定与审查按案例去重：至少 N 名不同标注员提交有效版本，且尚未完成入库。撤回或已退回重标的旧版本不计入。</p><p class="muted">一致率、类型分布及审计查询在对应模型和算法确认后接入。</p></div></div>
        <div class="card"><div class="card-head"><div class="card-title"><span class="dot"></span>自动标注队列</div></div><div class="card-body"><div class="stat-num">{{ summary.autoQueueCount }}</div><p class="t-sm muted">排队中与运行中的任务数。队列管理仅向获授权的任务创建者开放。</p></div></div>
        <div class="card"><div class="card-head"><div class="card-title"><span class="dot"></span>研究数据导出</div></div><div class="card-body"><p class="t-sm muted">只有已入库的最终标注版本可以进入导出范围。</p><router-link to="/export" class="btn btn-secondary action">前往导出中心 →</router-link></div></div>
      </div>
    </section>
  </template>
</template>
<style scoped>
.t-h1{margin:6px 0}.page-head .row{gap:10px;flex-wrap:wrap}.dashboard-body{grid-template-columns:1.6fr 1fr;margin-top:20px;align-items:start}.card-head{justify-content:space-between;gap:12px}.action{margin-top:14px}.stats{gap:16px}@media(max-width:1050px){.stats{grid-template-columns:repeat(2,minmax(0,1fr))}.dashboard-body{grid-template-columns:1fr}}@media(max-width:620px){.stats{grid-template-columns:1fr}.page-head{align-items:flex-start;gap:16px;flex-direction:column}}
</style>
