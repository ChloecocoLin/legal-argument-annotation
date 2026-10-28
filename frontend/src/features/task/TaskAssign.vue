<template>
  <div class="page" v-loading="loading">
    <div class="page-head">
      <h1>任务分配</h1>
      <p class="muted">先邀请成员加入任务，再为每案分配不同标注员与独立裁定者。</p>
    </div>
    <div v-if="sourceTaskId && sourceFinalVersionId" class="note accent reannotation-lineage">重标任务：原任务 <router-link :to="`/tasks/${sourceTaskId}`">{{sourceTaskId}}</router-link><br>来源最终版：<span class="mono">{{sourceFinalVersionId}}</span>。已绑定同一文本及范围的新案例，原结果只读；人员和标注从本任务重新开始。</div>
    <div v-if="taskStatus==='COMPLETED'" class="note">任务已完成，配置及人员只读；重新标注须从入库成果创建新任务。</div>
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
            <el-button type="primary" :disabled="taskStatus==='COMPLETED'" @click="join">加入人员池</el-button>
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
            <p>案例 {{ doc.id }} · N={{ doc.requiredAnnotators }}</p>
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
            <el-button style="margin-top: 8px" :disabled="!['DRAFT','ASSIGNING'].includes(taskStatus)" @click="assign(doc)">保存分配</el-button>
          </div>
          <el-button type="primary" :disabled="taskStatus!=='DRAFT'" @click="publish">发布任务</el-button>
          <el-button :disabled="!['DRAFT','ASSIGNING'].includes(taskStatus)" @click="start">启动任务</el-button>
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
const taskVersion = ref(1),taskStatus=ref(''),sourceTaskId=ref(''),sourceFinalVersionId=ref('')

const annotators = computed(() =>
  members.value.filter((m) => m.memberRole === 'ANNOTATOR' && m.status === 'ACTIVE')
)
const arbiters = computed(() => members.value.filter((m) => m.memberRole === 'ARBITER' && m.status === 'ACTIVE'))

async function reload() {
  loading.value = true
  try {
    const setup = await getTaskSetup(taskId.value)
    taskStatus.value=String(setup.task.status);sourceTaskId.value=String(setup.task.sourceTaskId||'');sourceFinalVersionId.value=String(setup.task.sourceFinalVersionId||'')
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
.reannotation-lineage{display:block;margin-bottom:16px;line-height:1.8;overflow-wrap:anywhere}

.doc-block {
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color);
}
</style>
