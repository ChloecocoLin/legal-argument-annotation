<template>
  <div class="page">
    <div class="page-head">
      <h1>创建任务</h1>
      <p class="muted">选择已发布指南创建任务，再添加文书并分配人员。正式科研任务每案至少两名标注员。</p>
    </div>
    <el-form label-width="160px" style="max-width: 720px">
      <el-form-item label="任务名称">
        <el-input v-model="form.name" />
      </el-form-item>
      <el-form-item label="指南版本">
        <el-select v-model="form.guideVersionId" style="width: 100%">
          <el-option v-for="guide in guides" :key="guide.id" :label="guide.versionCode" :value="guide.id" />
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
        <el-button type="primary" :loading="saving" @click="submit">创建任务</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createTask, getTaskOptions } from '@/api/memberA'

const router = useRouter()
const saving = ref(false)
const form = reactive({
  name: '',
  guideVersionId: '',
  taskType: 'FORMAL_RESEARCH',
  defaultRequiredAnnotators: 2,
  autoPreannotationEnabled: false,
  expertConfirmationRequired: false,
  adjudicationReasonRequired: true
})

const guides = ref<{ id: string; versionCode: string }[]>([])
onMounted(async () => { guides.value = (await getTaskOptions()).guides })

async function submit() {
  if (!form.name.trim() || !form.guideVersionId) { ElMessage.warning('请填写名称并选择已发布指南'); return }
  saving.value = true
  try {
    const created = await createTask({ ...form })
    ElMessage.success('已创建任务')
    router.push(`/tasks/${created.id}`)
  } finally {
    saving.value = false
  }
}
</script>
