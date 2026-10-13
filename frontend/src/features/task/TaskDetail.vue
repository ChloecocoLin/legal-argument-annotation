<template>
  <div class="page" v-loading="loading">
    <div class="page-head">
      <h1>任务详情</h1>
      <p class="muted">T03 配置；T13 为 D 的进度接口，此处用 Mock 适配核对本组 X/N，不宣称完整版本框架。</p>
    </div>
    <el-descriptions :column="2" border v-if="task">
      <el-descriptions-item label="任务 ID">{{ task.id }}</el-descriptions-item>
      <el-descriptions-item label="状态">{{ task.status }}</el-descriptions-item>
      <el-descriptions-item label="创建者">{{ task.ownerUserId }}</el-descriptions-item>
      <el-descriptions-item label="每案 N">{{ task.defaultRequiredAnnotators }}</el-descriptions-item>
    </el-descriptions>
    <h3 style="margin-top: 20px">案例进度（T13 Mock）</h3>
    <el-table :data="progress">
      <el-table-column prop="taskDocumentId" label="案例" />
      <el-table-column prop="submittedCount" label="已收到 X" width="120" />
      <el-table-column prop="requiredAnnotators" label="需要 N" width="120" />
      <el-table-column prop="status" label="状态" width="160" />
    </el-table>
    <div style="margin-top: 16px">
      <el-button @click="$router.push(`/tasks/${taskId}/assign`)">去分配</el-button>
      <el-button type="primary" @click="$router.push(`/annotate/${taskId}`)">打开工作台</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getProgress, getTask } from '@/api/memberA'

const route = useRoute()
const taskId = computed(() => String(route.params.taskId))
const loading = ref(false)
const task = ref<{ id: string; ownerUserId: string; status: string; defaultRequiredAnnotators: number } | null>(null)
const progress = ref<
  { taskDocumentId: string; submittedCount: number; requiredAnnotators: number; status: string }[]
>([])

onMounted(async () => {
  loading.value = true
  try {
    task.value = await getTask(taskId.value)
    progress.value = (await getProgress(taskId.value)).items
  } finally {
    loading.value = false
  }
})
</script>
