<template>
  <div class="page" v-loading="loading">
    <div class="page-head">
      <h1>任务分配</h1>
      <p class="muted">人员池 T06/T07 与逐案分配 T10 分开。正式任务标注员互异，裁定者独立。</p>
    </div>
    <el-row :gutter="16">
      <el-col :span="12">
        <el-card>
          <template #header>加入人员池</template>
          <el-form label-width="100px">
            <el-form-item label="用户">
              <el-select v-model="userId" filterable style="width: 100%">
                <el-option v-for="u in users" :key="u.id" :label="u.displayName" :value="u.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="角色">
              <el-select v-model="memberRole">
                <el-option label="标注员" value="ANNOTATOR" />
                <el-option label="裁定者" value="ARBITER" />
                <el-option label="专家" value="EXPERT" />
              </el-select>
            </el-form-item>
            <el-button type="primary" @click="join">T07 加入</el-button>
          </el-form>
          <el-table :data="members" size="small" style="margin-top: 12px">
            <el-table-column prop="displayName" label="姓名" />
            <el-table-column prop="memberRole" label="角色" />
            <el-table-column prop="status" label="状态" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>逐案分配</template>
          <div v-for="doc in docs" :key="String(doc.id)" class="doc-block">
            <p>案例 {{ doc.id }} · N={{ doc.requiredAnnotators }} · ver {{ doc.recordVersion }}</p>
            <el-select v-model="picked[String(doc.id)].annotators" multiple placeholder="标注员" style="width: 100%">
              <el-option
                v-for="m in annotators"
                :key="String(m.id)"
                :label="String(m.displayName)"
                :value="String(m.id)"
              />
            </el-select>
            <el-select v-model="picked[String(doc.id)].arbiter" placeholder="裁定者" style="width: 100%; margin-top: 8px">
              <el-option
                v-for="m in arbiters"
                :key="String(m.id)"
                :label="String(m.displayName)"
                :value="String(m.id)"
              />
            </el-select>
            <el-button style="margin-top: 8px" @click="assign(doc)">T10 保存分配</el-button>
          </div>
          <el-button type="primary" @click="publish">T15/T12 发布</el-button>
          <el-button @click="start">T12 启动</el-button>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { addMember, getTaskSetup, listUsers, setAssignments, transitionTask } from '@/api/memberA'

const route = useRoute()
const taskId = computed(() => String(route.params.taskId))
const loading = ref(false)
const users = ref<{ id: string; displayName: string }[]>([])
const members = ref<Record<string, unknown>[]>([])
const docs = ref<Record<string, unknown>[]>([])
const userId = ref('')
const memberRole = ref('ANNOTATOR')
const picked = reactive<Record<string, { annotators: string[]; arbiter: string }>>({})
const taskVersion = ref(1)

const annotators = computed(() =>
  members.value.filter((m) => m.memberRole === 'ANNOTATOR' && m.status === 'ACTIVE')
)
const arbiters = computed(() => members.value.filter((m) => m.memberRole === 'ARBITER' && m.status === 'ACTIVE'))

async function reload() {
  loading.value = true
  try {
    const setup = await getTaskSetup(taskId.value)
    members.value = setup.members
    docs.value = setup.taskDocuments
    taskVersion.value = Number(setup.task.recordVersion)
    for (const doc of docs.value) {
      const id = String(doc.id)
      if (!picked[id]) picked[id] = { annotators: [], arbiter: '' }
    }
    users.value = (await listUsers({ page: 1, size: 50 })).items
  } finally {
    loading.value = false
  }
}

async function join() {
  await addMember(taskId.value, { userId: userId.value, memberRole: memberRole.value })
  ElMessage.success('已加入人员池')
  await reload()
}

async function assign(doc: Record<string, unknown>) {
  const id = String(doc.id)
  await setAssignments(
    id,
    {
      annotatorMemberIds: picked[id].annotators,
      arbiterMemberId: picked[id].arbiter
    },
    Number(doc.recordVersion)
  )
  ElMessage.success('已保存逐案分配')
  await reload()
}

async function publish() {
  const r = await transitionTask(taskId.value, 'PUBLISH', taskVersion.value)
  ElMessage.success(`已发布：${r.status}`)
  await reload()
}

async function start() {
  const r = await transitionTask(taskId.value, 'START', taskVersion.value)
  ElMessage.success(`已启动：${r.status}`)
  await reload()
}

onMounted(reload)
</script>

<style scoped>
.doc-block {
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color);
}
</style>
