<template>
  <div class="page">
    <div class="page-head between">
      <div>
        <h1>任务列表</h1>
        <p class="muted">查看参与的任务、案例提交进度和人员分配。</p>
      </div>
      <el-button v-if="canCreate" type="primary" @click="$router.push('/tasks/create')">创建任务</el-button>
    </div>
    <el-alert v-if="loadError" :title="loadError" type="error" :closable="false" style="margin-bottom: 16px" />
    <el-table :data="rows" v-loading="loading">
      <el-table-column prop="name" label="名称" min-width="220" />
      <el-table-column label="状态" width="140"><template #default="{ row }">{{ statusName(row.status) }}</template></el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="$router.push(`/tasks/${row.id}`)">详情</el-button>
          <el-button v-if="canCreate" link type="primary" @click="$router.push(`/tasks/${row.id}/assign`)">分配</el-button>
          <el-button link type="primary" @click="$router.push(`/tasks/${row.id}/progress`)">版本进度</el-button>
        </template>
      </el-table-column>
      <template #empty>{{ emptyText }}</template>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useUserStore } from '@/stores/user'
import { errorMessage, statusName } from '@/features/versions/api'
import { getTaskSetup, listTasks } from '@/api/memberA'

const user = useUserStore()
const loading = ref(false)
const loadError = ref('')
const rows = ref<{ id: string; status: string; name: string }[]>([])
const canCreate = computed(() => user.roles.some((role) => ['admin', 'creator'].includes(role)))
const emptyText = computed(() => {
  if (loadError.value) return loadError.value
  if (user.roles.includes('student')) return '你还没有被分配任务'
  if (user.roles.some((role) => ['teacher', 'expert'].includes(role))) return '当前角色无权访问标注任务'
  return '暂无任务。创建者请先新建任务并分配文书。'
})

onMounted(async () => {
  loading.value = true
  loadError.value = ''
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
  } catch (e) {
    loadError.value = errorMessage(e)
  } finally {
    loading.value = false
  }
})
</script>
