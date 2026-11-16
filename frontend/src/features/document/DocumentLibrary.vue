<template>
  <div class="page">
    <div class="page-head">
      <h1>文书总库</h1>
      <p class="muted">检索现有裁判文书，查看规范化原文及文本版本。</p>
    </div>
    <el-form inline @submit.prevent="load">
      <el-input v-model="q" placeholder="检索法院 / 案由 / 案号" clearable style="width: 240px" />
      <el-button type="primary" @click="load">检索</el-button>
    </el-form>
    <el-alert v-if="loadError" :title="loadError" type="error" :closable="false" style="margin-bottom: 16px" />
    <el-table :data="items" v-loading="loading" @row-click="open">
      <el-table-column label="标题 / 案号" min-width="220">
        <template #default="{ row }">{{ row.title || row.caseNumber || '预置文书' }}</template>
      </el-table-column>
      <el-table-column prop="courtName" label="法院" min-width="200" />
      <el-table-column prop="causeOfAction" label="案由" min-width="160" />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click.stop="open(row)">详情</el-button>
        </template>
      </el-table-column>
      <template #empty>{{ emptyText }}</template>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { errorMessage } from '@/features/versions/api'
import { listDocuments } from '@/api/memberA'

const router = useRouter()
const user = useUserStore()
const q = ref('')
const loading = ref(false)
const loadError = ref('')
const items = ref<{ id: string; title?: string; caseNumber?: string; courtName: string; causeOfAction: string }[]>([])
const emptyText = computed(() => {
  if (loadError.value) return loadError.value
  if (user.roles.includes('student')) return '当前账号不能查看科研文书'
  if (user.roles.some((role) => ['teacher', 'expert'].includes(role))) return '当前角色无权访问文书总库'
  return '目前只能使用已经入库的预置文书'
})

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const page = await listDocuments({ q: q.value || undefined, page: 1, size: 20 })
    items.value = page.items
  } catch (e) {
    loadError.value = errorMessage(e)
    items.value = []
  } finally {
    loading.value = false
  }
}

function open(row: { id: string }) {
  router.push(`/documents/${row.id}`)
}

onMounted(load)
</script>
