<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import LocalSession from './LocalSession.vue'
import { listVersions, errorMessage, formatTime, statusName } from './api'
import type { VersionList } from './types'
const route = useRoute(), loading = ref(false), error = ref(''), page = ref(1), status = ref('')
const result = ref<VersionList>()
const taskId = computed(() => String(route.params.taskId)), caseId = computed(() => String(route.params.caseId))
async function load(reset = false) {
  if (reset) page.value = 1
  loading.value = true; error.value = ''; result.value = undefined
  try { result.value = await listVersions(caseId.value, page.value, status.value || undefined) }
  catch (e) { error.value = errorMessage(e) } finally { loading.value = false }
}
onMounted(() => load())
</script>
<template>
  <LocalSession />
  <div class="page-head between"><div><div class="eyebrow">ANNOTATION VERSIONS</div><h1 class="t-h1">案例标注版本</h1><p class="muted t-sm">正式版本保留原始内容；按当前身份展示获授权的来源版本。</p></div><router-link class="btn btn-secondary" :to="`/tasks/${taskId}`">返回任务详情</router-link></div>
  <div class="card"><div class="card-head between"><div class="card-title"><span class="dot"></span>独立标注版本</div><select class="select" aria-label="版本状态" v-model="status" @change="load(true)"><option value="">全部状态</option><option v-for="s in ['SUBMITTED', 'WITHDRAWN', 'UNDER_ADJUDICATION', 'ADJUDICATED']" :key="s" :value="s">{{ statusName(s) }}</option></select></div>
    <div class="card-body">
      <div v-if="loading" role="status" class="muted">正在读取版本…</div>
      <div v-else-if="error" role="alert" class="note danger">{{ error }} <button class="btn btn-secondary" @click="load()">重试</button></div>
      <template v-else-if="result"><div class="panel between summary"><span>有效版本收集进度</span><b>已收到 {{ result.data.submittedCount }}/{{ result.data.requiredAnnotators }}</b></div>
        <div v-if="!result.data.items.length" class="empty muted">暂无可见版本</div>
        <div v-for="v in result.data.items" :key="v.id" class="version-row between"><div><b class="mono">{{ v.displayCode }}</b><span class="badge" :class="v.status === 'WITHDRAWN' ? 'default' : 'success'">{{ statusName(v.status) }}</span><div class="t-xs muted">提交于 {{ formatTime(v.submittedAt) }} · {{ v.elementIds.length }} 个要素 / {{ v.relationIds.length }} 个关系</div></div><router-link class="btn btn-secondary" :to="{ path: `/versions/${v.id}`, query: { taskId, caseId } }">查看完整版本</router-link></div>
        <div class="between paging"><span class="muted t-xs">共 {{ result.meta.total }} 个可见版本</span><div class="row"><button class="btn btn-secondary" :disabled="page <= 1" @click="page--; load()">上一页</button><span>{{ page }}</span><button class="btn btn-secondary" :disabled="page * result.meta.size >= result.meta.total" @click="page++; load()">下一页</button></div></div>
      </template>
    </div>
  </div>
</template>
<style scoped>.t-h1{margin:6px 0}.summary{padding:12px}.version-row{padding:18px 0;border-bottom:1px solid var(--border);gap:12px}.version-row .badge{margin-left:12px}.version-row .t-xs{margin-top:8px}.paging{margin-top:18px}.paging .row{gap:12px}.empty{padding:30px;text-align:center}.select{width:160px}</style>
