<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import LocalSession from '@/features/versions/LocalSession.vue'
import { getLocalContext, errorMessage, formatTime, mutationAttempt, errorCode } from '@/features/versions/api'
import { get } from '@/api/request'
import { listExports, readExport, requestDownload, saveDownload, type ExportRecord } from './api'
const route = useRoute(), error = ref(''), loading = ref(false), busy = ref(false), items = ref<ExportRecord[]>([]), total = ref(0), page = ref(1), status = ref(''), detail = ref<ExportRecord>()
const events=ref<Record<string,unknown>[]>([])
const downloadAttemptId=ref('')
const attempt = mutationAttempt<{id: string; purpose: string}>()
let timer: ReturnType<typeof setTimeout> | undefined, disposed = false
const names: Record<string,string> = {QUEUED:'排队中',RUNNING:'生成中',COMPLETED:'已完成',FAILED:'失败'}
async function load() {
  clearTimeout(timer); loading.value = true; error.value = ''
  try {
    if (import.meta.env.DEV) await getLocalContext()
    const r = await listExports(page.value, status.value || undefined)
    items.value = r.data.items; total.value = r.meta.total
    if(detail.value) detail.value = await readExport(detail.value.id)
    if(!disposed && items.value.some(x => ['QUEUED','RUNNING'].includes(x.status))) timer = setTimeout(load, 3000)
  } catch(e) { error.value = errorMessage(e) } finally { loading.value = false }
}
async function show(id: string) { try { detail.value = await readExport(id); events.value = []; if(import.meta.env.DEV) events.value = (await get<{items:Record<string,unknown>[]}>(`/api/local/member-d/export-records/${id}/events`,undefined,{baseURL:''})).items } catch(e) { error.value = errorMessage(e) } }
async function download(row: ExportRecord) {
  if(busy.value) return
  try {
    let a
    if(attempt.pending()) a = attempt.get({id:row.id,purpose:''},0)
    else {
      const result = await ElMessageBox.prompt('请填写下载目的，将与本次下载记录一同保存。', '申请下载', { inputValidator: v => !!v?.trim() && v.length <= 500 || '请输入 1–500 字符', confirmButtonText:'申请并下载' })
      a = attempt.get({id:row.id,purpose:result.value.trim()},0);downloadAttemptId.value=row.id
    }
    busy.value = true; error.value = ''
    const ticket = await requestDownload(a.body.id, a.body.purpose, a.key)
    await saveDownload(ticket, `legal-annotation-${a.body.id}.${row.format.toLowerCase()}`)
    attempt.reset();downloadAttemptId.value=''
  } catch(e) { if(e !== 'cancel' && e !== 'close') { error.value = errorMessage(e); if(errorCode(e) === 'DOWNLOAD_TOKEN_EXPIRED') {attempt.reset();downloadAttemptId.value=''} } } finally { busy.value = false }
}
onMounted(async () => { await load(); if(typeof route.query.id === 'string') await show(route.query.id) })
onUnmounted(() => { disposed = true; clearTimeout(timer) })
</script>
<template>
  <LocalSession description="D 分支试行：查询真实导出记录，申请短时凭证并下载文件" />
  <div class="page-head between"><div><div class="eyebrow">EXPORT RECORDS</div><h1 class="t-h1">导出记录</h1><p class="t-sm muted">每份文件保留固定来源版本、摘要与下载记录。</p></div><router-link to="/export" class="btn btn-primary">＋ 新建导出</router-link></div>
  <div class="note warn boundary">文件与下载模型待团队评审。本地试行已接通；PNG / SVG 已接入服务端试行渲染，正式渲染模型待团队评审。</div>
  <div v-if="error" class="note danger" role="alert">{{ error }} <button class="btn btn-sm" @click="load">刷新</button></div>
  <section class="card"><div class="card-head between"><select class="select" aria-label="导出状态" v-model="status" @change="page=1;load()"><option value="">全部状态</option><option v-for="(label,value) in names" :key="value" :value="value">{{ label }}</option></select><button class="btn btn-secondary" @click="load" :disabled="loading">{{ loading ? '读取中…' : '刷新' }}</button></div>
  <div class="tbl-wrap"><table class="tbl"><thead><tr><th>编号</th><th>最终版数</th><th>格式</th><th>时间</th><th>大小</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="row in items" :key="row.id"><td class="mono">{{ row.id.slice(0,8) }}</td><td>{{ row.finalVersionIds.length }}</td><td>{{ row.format }}</td><td>{{ formatTime(row.requestedAt) }}</td><td>{{ row.file ? (row.file.sizeBytes/1024).toFixed(1)+' KB' : '—' }}</td><td>{{ names[row.status] || row.status }}</td><td><button class="btn btn-sm" @click="show(row.id)">详情</button> <button class="btn btn-sm btn-primary" :disabled="row.status !== 'COMPLETED' || busy || attempt.pending() && downloadAttemptId !== row.id" @click="download(row)">{{ attempt.pending() && downloadAttemptId === row.id ? '重试下载' : '下载' }}</button></td></tr><tr v-if="!items.length"><td colspan="7" class="empty muted">{{ loading ? '正在读取记录…' : '当前范围内没有导出记录' }}</td></tr></tbody></table></div>
  <div class="card-foot between"><button class="btn btn-sm" :disabled="page <= 1" @click="page--;load()">上一页</button><span>{{ page }} 页 · 共 {{ total }} 条</span><button class="btn btn-sm" :disabled="page * 20 >= total" @click="page++;load()">下一页</button></div></section>
  <section v-if="detail" class="card detail"><div class="card-head between"><b>导出详情</b><button class="btn btn-sm" @click="detail=undefined">关闭</button></div><div class="card-body stack-3"><p class="mono">{{ detail.id }}</p><p>状态：{{ names[detail.status] }} · {{ detail.format }}</p><p v-if="detail.failureMessage" role="alert">{{ detail.failureMessage }}</p><p>文件：{{ detail.fileId || '尚未生成' }}</p><p class="digest">来源摘要：{{ detail.sourceDigest }}</p><p v-if="detail.file" class="digest">文件摘要：{{ detail.file.sha256 }}</p><p v-if="detail.generatedMetadata?.rendererVersion">图示渲染器：{{ detail.generatedMetadata.rendererVersion }} · 包含 {{ detail.generatedMetadata.componentManifest?.length }} 个分量（含孤立命题）</p><details v-if="detail.generatedMetadata"><summary>文件生成与分量清单</summary><pre>{{ JSON.stringify(detail.generatedMetadata,null,2) }}</pre></details><p>来源最终版：</p><ul><li v-for="id in detail.finalVersionIds" :key="id" class="mono">{{ id }}</li></ul><details><summary>完整来源清单</summary><pre>{{ JSON.stringify(detail.sourceManifest,null,2) }}</pre></details><details v-if="events.length"><summary>导出与下载追溯（{{ events.length }} 条）</summary><pre>{{ JSON.stringify(events,null,2) }}</pre></details><p class="t-xs muted">已完成文件不可覆盖。失败后请核对源数据并新建导出；网络失败重试沿用同一请求编号。</p></div></section>
</template>
<style scoped>.t-h1{margin:6px 0}.boundary,.detail{margin:20px 0}.empty{padding:60px 20px;text-align:center}.card-head{gap:16px;flex-wrap:wrap}.select{max-width:200px}.digest,pre,.mono{overflow-wrap:anywhere}pre{white-space:pre-wrap}@media(max-width:760px){.page-head{flex-direction:column;align-items:flex-start;gap:16px}}</style>
