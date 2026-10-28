<template>
  <div class="page">
    <div class="page-head between">
      <div>
        <h1>任务列表</h1>
        <p class="muted">查看参与的任务、案例提交进度和人员分配。</p>
      </div>
      <el-button v-if="user.roles.includes('creator')" type="primary" @click="$router.push('/tasks/create')">创建任务</el-button>
    </div>
    <el-table :data="rows" v-loading="loading">
      <el-table-column prop="name" label="名称" min-width="200" />
      <el-table-column label="状态" width="140"><template #default="{ row }">{{ statusName(row.status) }}</template></el-table-column>
      <el-table-column prop="id" label="任务编号" min-width="280" />
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button link type="primary" @click="$router.push(`/tasks/${row.id}`)">详情</el-button>
          <el-button v-if="user.roles.includes('creator')" link type="primary" @click="$router.push(`/tasks/${row.id}/assign`)">分配</el-button>
          <el-button link type="primary" @click="$router.push(`/tasks/${row.id}/progress`)">版本进度</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useUserStore } from '@/stores/user'
import { statusName } from '@/features/versions/api'
const user = useUserStore()
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
          return { ...item, name: String(setup.task.name ?? '未命名任务') }
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
