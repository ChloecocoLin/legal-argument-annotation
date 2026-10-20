<template>
  <div class="page" v-loading="loading">
    <div class="page-head">
      <h1>文书详情</h1>
      <p class="muted">查看裁判文书及规范化原文。范围绑定任务后不可覆盖。</p>
    </div>
    <el-descriptions :column="1" border v-if="detail">
      <el-descriptions-item label="文书 ID">{{ detail.id }}</el-descriptions-item>
      <el-descriptions-item label="原件编号">{{ detail.originalFileId }}</el-descriptions-item>
      <el-descriptions-item label="文本版本">{{ detail.textVersionIds.join(', ') }}</el-descriptions-item>
    </el-descriptions>
    <el-card v-if="text" style="margin-top: 16px">
      <template #header>规范化原文 · SHA-256 {{ text.contentSha256 }}</template>
      <p class="source">{{ text.normalizedText }}</p>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getDocument, getTextVersion } from '@/api/memberA'

const route = useRoute()
const loading = ref(false)
const detail = ref<{ id: string; originalFileId: string; textVersionIds: string[] } | null>(null)
const text = ref<{ id: string; normalizedText: string; contentSha256: string } | null>(null)

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
