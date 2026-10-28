<template>
  <div class="page">
    <div class="page-head">
      <h1>创建任务</h1>
      <p class="muted">T02 创建；T05 绑定已确认范围的文书。正式科研 N≥2。</p>
    </div>
    <el-form label-width="160px" style="max-width: 720px">
      <el-form-item label="任务名称">
        <el-input v-model="form.name" />
      </el-form-item>
      <el-form-item label="指南版本">
        <el-select v-model="form.guideVersionId" style="width: 100%">
          <el-option label="B 模块已发布 v1.2（v-g-12）" value="v-g-12" />
          <el-option label="契约 Mock UUID" value="a9055ff9-34b0-4083-9db7-91f82dbe5fc4" />
        </el-select>
      </el-form-item>
      <el-form-item label="任务类型">
        <el-select v-model="form.taskType">
          <el-option label="正式科研" value="FORMAL_RESEARCH" />
          <el-option label="培训" value="TRAINING" />
          <el-option label="试标" value="PILOT" />
        </el-select>
      </el-form-item>
      <el-form-item label="每案人数 N">
        <el-input-number v-model="form.defaultRequiredAnnotators" :min="1" :max="8" />
      </el-form-item>
      <el-form-item>
        <el-checkbox v-model="form.autoPreannotationEnabled">启用自动预标注</el-checkbox>
        <el-checkbox v-model="form.expertConfirmationRequired">需要专家确认</el-checkbox>
        <el-checkbox v-model="form.adjudicationReasonRequired">裁定理由必填</el-checkbox>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="saving" @click="submit">创建并绑定预置文书</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { addTaskDocument, createTask, updateTask, getTaskSetup } from '@/api/memberA'

const router = useRouter()
const saving = ref(false)
const form = reactive({
  name: '劳务合同纠纷裁判理由标注',
  guideVersionId: 'v-g-12',
  taskType: 'FORMAL_RESEARCH',
  defaultRequiredAnnotators: 2,
  autoPreannotationEnabled: true,
  expertConfirmationRequired: false,
  adjudicationReasonRequired: true
})

async function submit() {
  saving.value = true
  try {
    const created = await createTask({ ...form })
    const setup = await getTaskSetup(created.id)
    await updateTask(
      created.id,
      {
        description: '按指南 v1.2 完成双人独立科研标注。',
        dueAt: '2026-10-30T15:59:00Z'
      },
      Number(setup.task.recordVersion)
    )
    await addTaskDocument(created.id, {
      documentId: 'bfd699c7-e225-4263-b077-c247e4d59fbc',
      documentTextVersionId: '86d2b05b-fa18-4d45-a451-edcaa83d673f',
      annotationScopeId: '4c3122b2-90e0-47b4-8e67-0986764897bc',
      requiredAnnotators: form.defaultRequiredAnnotators
    })
    ElMessage.success('已创建任务并绑定预置文书')
    router.push(`/tasks/${created.id}/assign`)
  } finally {
    saving.value = false
  }
}
</script>
