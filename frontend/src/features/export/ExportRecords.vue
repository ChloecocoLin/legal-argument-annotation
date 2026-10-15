<script setup lang="ts">
import { onMounted, ref } from 'vue'
import LocalSession from '@/features/versions/LocalSession.vue'
import { getLocalContext, errorMessage } from '@/features/versions/api'
import { useUserStore } from '@/stores/user'
const error = ref(''), loading = ref(true)
onMounted(async () => {
  try {
    if (import.meta.env.DEV) await getLocalContext()
    if (!useUserStore().roles.some(r => r === 'admin' || r === 'creator')) throw new Error('仅管理员或获授权任务创建者可查看导出记录')
  } catch (e) { error.value = errorMessage(e) } finally { loading.value = false }
})
</script>
<template>
  <LocalSession description="导出记录页面预览；记录查询和下载流程等待公共模型确认" />
  <div class="page-head between"><div><div class="eyebrow">EXPORT RECORDS</div><h1 class="t-h1">导出记录</h1><p class="t-sm muted">导出文件生成后保持不可变，保留来源版本与下载记录，确保研究数据可复现。</p></div><router-link to="/export" class="btn btn-primary">＋ 新建导出</router-link></div>
  <div v-if="loading" class="card card-body muted" role="status">正在核对当前身份…</div>
  <div v-else-if="error" class="note danger" role="alert">{{ error }}</div>
  <template v-else>
    <div class="note warn boundary" role="status">导出记录查询尚未接入，当前无法读取历史记录。正式下载流程将先创建下载记录与短时凭证，再读取文件。</div>
    <section class="card"><div class="card-head"><div class="toolbar"><input class="input input-sm" aria-label="检索导出记录" placeholder="检索编号 / 文书（待接入）" disabled><div class="seg"><button class="is-active" disabled>全部</button><button disabled>已完成</button><button disabled>部分失败</button></div></div><span class="t-xs muted">记录不可删除或覆盖</span></div><div class="tbl-wrap"><table class="tbl"><thead><tr><th>编号</th><th>任务 / 文书</th><th>指南</th><th>格式</th><th>导出人</th><th>时间</th><th>大小</th><th>状态</th><th>操作</th></tr></thead><tbody><tr><td colspan="9" class="empty muted">记录服务待接入</td></tr></tbody></table></div><div class="card-foot muted t-xs">尚未加载记录，暂不显示记录总数或下载操作。</div></section>
  </template>
</template>
<style scoped>.t-h1{margin:6px 0}.boundary{margin-bottom:20px}.empty{padding:64px 20px;text-align:center}.card-head{gap:16px;justify-content:space-between;flex-wrap:wrap}.toolbar{flex-wrap:wrap}@media(max-width:760px){.page-head{flex-direction:column;align-items:flex-start;gap:16px}}</style>
