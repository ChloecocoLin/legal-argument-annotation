<template>
  <div class="page" v-loading="loading">
    <div class="page-head">
      <h1>{{ taskName || '任务详情' }}</h1>
      <p class="muted">查看任务配置和案例提交进度。</p>
    </div>
    <el-descriptions :column="2" border v-if="task">
      <el-descriptions-item label="任务 ID">{{ task.id }}</el-descriptions-item>
      <el-descriptions-item label="状态">{{ statusName(task.status) }}</el-descriptions-item>
      <el-descriptions-item label="创建者">{{ ownerName || task.ownerUserId }}</el-descriptions-item>
      <el-descriptions-item label="每案 N">{{ task.defaultRequiredAnnotators }}</el-descriptions-item>
    </el-descriptions>
    <el-card v-if="canManage && options" style="margin-top: 16px">
      <template #header>添加文书</template>
      <el-select v-model="scopeId" placeholder="选择已确认标注范围的文书" style="width: 70%">
        <el-option v-for="doc in options.documents" :key="doc.annotationScopeId" :label="doc.label" :value="doc.annotationScopeId" />
      </el-select>
      <el-button :disabled="!scopeId" :loading="adding" @click="bindDocument">添加到任务</el-button>
    </el-card>
    <h3 style="margin-top: 20px">案例进度（正式提交 X/N）</h3>
    <el-table :data="progress">
      <el-table-column prop="taskDocumentId" label="案例" />
      <el-table-column prop="submittedCount" label="已收到 X" width="120" />
      <el-table-column prop="requiredAnnotators" label="需要 N" width="120" />
      <el-table-column label="状态" width="160"><template #default="{ row }">{{ statusName(row.status) }}</template></el-table-column>
      <el-table-column v-if="user.roles.some(role=>['annotator','arbiter'].includes(role))" label="操作" width="220"><template #default="{ row }"><el-button link type="primary" @click="$router.push(`/tasks/${taskId}/cases/${row.taskDocumentId}/segmentation`)">独立切割</el-button><el-button v-if="user.roles.includes('annotator')" link type="primary" @click="$router.push(`/tasks/${taskId}/cases/${row.taskDocumentId}/annotate`)">本人标注工作台</el-button></template></el-table-column>
    </el-table>
    <div style="margin-top: 16px">
      <el-button v-if="canManage" @click="$router.push(`/tasks/${taskId}/assign`)">去分配</el-button>
      <el-button type="primary" @click="$router.push(`/tasks/${taskId}/progress`)">版本与提交入口</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { addTaskDocument, getProgress, getTask, getTaskOptions, getTaskSetup } from '@/api/memberA'

import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { statusName } from '@/features/versions/api'
const taskName = ref(''), ownerName = ref('')
const user = useUserStore()
const route = useRoute()
const taskId = computed(() => String(route.params.taskId))
const loading = ref(false)
const task = ref<{ id: string; ownerUserId: string; status: string; defaultRequiredAnnotators: number } | null>(null)
const progress = ref<
  { taskDocumentId: string; submittedCount: number; requiredAnnotators: number; status: string }[]
>([])

const canManage = computed(() => task.value?.ownerUserId === user.id && ['DRAFT', 'ASSIGNING'].includes(task.value?.status || ''))
const options = ref<Awaited<ReturnType<typeof getTaskOptions>> | null>(null)
const scopeId = ref('')
const adding = ref(false)
async function bindDocument() {
  const doc = options.value?.documents.find(d => d.annotationScopeId === scopeId.value)
  if (!doc || !task.value) return
  adding.value = true
  try {
    const { label, ...source } = doc
    await addTaskDocument(taskId.value, { ...source, requiredAnnotators: task.value.defaultRequiredAnnotators })
    progress.value = (await getProgress(taskId.value)).items
    scopeId.value = ''
    ElMessage.success('文书已加入任务')
  } finally { adding.value = false }
}

onMounted(async () => {
  loading.value = true
  try {
    task.value = await getTask(taskId.value)
    const setup = await getTaskSetup(taskId.value)
    taskName.value = String(setup.task.name || '')
    ownerName.value = String(setup.members.find(m => m.userId === task.value?.ownerUserId)?.displayName || '')
    progress.value = (await getProgress(taskId.value)).items
    if (canManage.value) options.value = await getTaskOptions()
  } finally {
    loading.value = false
  }
})
</script>
