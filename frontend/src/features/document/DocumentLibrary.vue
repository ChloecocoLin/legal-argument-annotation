<template>
  <div class="page">
    <div class="page-head">
      <h1>文书总库</h1>
      <p class="muted">检索现有裁判文书，查看规范化原文及文本版本。</p>
    </div>
    <el-form inline @submit.prevent="load">
      <el-input v-model="q" placeholder="检索法院 / 案由" clearable style="width: 240px" />
      <el-button type="primary" @click="load">检索</el-button>
    </el-form>
    <el-table :data="items" v-loading="loading" @row-click="open">
      <el-table-column prop="id" label="文书 ID" min-width="280" />
      <el-table-column prop="courtName" label="法院" min-width="220" />
      <el-table-column prop="causeOfAction" label="案由" min-width="160" />
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listDocuments } from '@/api/memberA'

const router = useRouter()
const q = ref('')
const loading = ref(false)
const items = ref<{ id: string; courtName: string; causeOfAction: string }[]>([])

async function load() {
  loading.value = true
  try {
    const page = await listDocuments({ q: q.value || undefined, page: 1, size: 20 })
    items.value = page.items
  } finally {
    loading.value = false
  }
}

function open(row: { id: string }) {
  router.push(`/documents/${row.id}`)
}

onMounted(load)
</script>
