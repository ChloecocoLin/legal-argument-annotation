<template>
  <div class="page" v-loading="loading">
    <div class="page-head">
      <h1>{{ heading }}</h1>
      <p class="muted">查看裁判文书及规范化原文。范围绑定任务后不可覆盖。</p>
    </div>
    <el-descriptions :column="1" border v-if="detail">
      <el-descriptions-item v-if="detail.courtName" label="法院">{{ detail.courtName }}</el-descriptions-item>
      <el-descriptions-item v-if="detail.causeOfAction" label="案由">{{ detail.causeOfAction }}</el-descriptions-item>
      <el-descriptions-item v-if="detail.caseNumber" label="案号">{{ detail.caseNumber }}</el-descriptions-item>
    </el-descriptions>
    <el-card v-if="text" style="margin-top: 16px">
      <template #header>规范化原文</template>
      <p class="source">{{ text.normalizedText }}</p>
    </el-card>
    <el-collapse v-if="detail" style="margin-top: 16px">
      <el-collapse-item title="技术信息" name="ids">
        <p>文书 ID：{{ detail.id }}</p>
        <p>原件编号：{{ detail.originalFileId }}</p>
        <p>文本版本：{{ detail.textVersionIds.join(', ') }}</p>
        <p v-if="text">SHA-256：{{ text.contentSha256 }}</p>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getDocument, getTextVersion } from '@/api/memberA'

const route = useRoute()
const loading = ref(false)
const detail = ref<{
  id: string
  title?: string
  caseNumber?: string
  courtName?: string
  causeOfAction?: string
  originalFileId: string
  textVersionIds: string[]
} | null>(null)
const text = ref<{ id: string; normalizedText: string; contentSha256: string } | null>(null)
const heading = computed(() => {
  if (!detail.value) return '文书详情'
  return detail.value.title || detail.value.caseNumber || text.value?.normalizedText.slice(0, 24) || '文书详情'
})

onMounted(async () => {
  loading.value = true
  try {
    const documentId = String(route.params.documentId)
    detail.value = await getDocument(documentId)
    const tvId = detail.value.textVersionIds[0]
    if (tvId) text.value = await getTextVersion(tvId)
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.source {
  white-space: pre-wrap;
  line-height: 1.7;
  font-size: 15px;
}
</style>
