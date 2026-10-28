<template>
  <div class="page">
    <div class="page-head between">
      <div>
        <h1>任务列表</h1>
        <p class="muted">T01 可见任务。T15 只是页面上的发布入口，内部仍走 T12 PUBLISH。</p>
      </div>
      <el-button type="primary" @click="$router.push('/tasks/create')">创建任务</el-button>
    </div>
    <el-table :data="rows" v-loading="loading">
      <el-table-column prop="id" label="任务 ID" min-width="280" />
      <el-table-column prop="name" label="名称" min-width="200" />
      <el-table-column prop="status" label="状态" width="140" />
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button link type="primary" @click="$router.push(`/tasks/${row.id}`)">详情</el-button>
          <el-button link type="primary" @click="$router.push(`/tasks/${row.id}/assign`)">分配</el-button>
          <el-button link type="primary" @click="$router.push(`/annotate/${row.id}`)">工作台</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getTaskSetup, listTasks } from '@/api/memberA'

const loading = ref(false)
const rows = ref<{ id: string; status: string; name: string }[]>([])

onMounted(async () => {
  loading.value = true
  try {
    const page = await listTasks({ page: 1, size: 50 })
    const enriched = await Promise.all(
      page.items.map(async (item) => {
        try {
          const setup = await getTaskSetup(item.id)
          return { ...item, name: String(setup.task.name ?? '（契约未返回名称，取自配置适配）') }
        } catch {
          return { ...item, name: item.id.slice(0, 8) }
        }
      })
    )
    rows.value = enriched
  } finally {
    loading.value = false
  }
})
</script>
