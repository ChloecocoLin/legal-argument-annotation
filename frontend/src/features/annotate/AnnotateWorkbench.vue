<template>
  <div class="page workbench" v-loading="loading">
    <div class="page-head">
      <h1>标注工作台 · 原文与命题边界</h1>
      <p class="muted">
        未分类边界还不是 Element。标签从 G06 加载仅作对照，不会把 Non-O 当成未分类。S04 关口只读预置，不在此确认。
      </p>
    </div>
    <el-alert
      v-if="labels.length"
      type="info"
      :closable="false"
      :title="`指南标签（G06）：${labels.map((l) => l.code).join(' / ')}`"
      style="margin-bottom: 12px"
    />
    <el-row :gutter="16">
      <el-col :span="14">
        <el-card>
          <template #header>
            原文
            <el-button size="small" @click="createFromSelection">用选区创建边界</el-button>
            <el-button size="small" @click="suggest">句号/分号候选</el-button>
          </template>
          <div class="source" ref="sourceEl" @mouseup="rememberSelection">{{ displayText }}</div>
          <p class="muted">标注范围 UTF-16 [{{ scopeStart }}, {{ scopeEnd }}) · 全文长度 {{ fullText.length }}</p>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card>
          <template #header>命题边界（未分类）</template>
          <div class="checks">
            <span>重叠 {{ overlapCount }}</span>
            <span>遗漏 {{ gapCount }}</span>
          </div>
          <div v-for="(b, i) in boundaries" :key="i" class="bound">
            <code>[{{ b.startOffset }}, {{ b.endOffset }})</code>
            <span>{{ slice(b) }}</span>
            <el-button link @click="splitAt(i)">拆分</el-button>
            <el-button link @click="mergeNext(i)" :disabled="i === boundaries.length - 1">合并下一条</el-button>
            <el-button link type="danger" @click="remove(i)">删</el-button>
          </div>
          <el-button type="primary" :disabled="!ctx?.assignmentId" @click="submit">S01 提交切割</el-button>
          <p class="muted" v-if="!ctx?.assignmentId">当前身份没有本案标注员分配，不能提交。</p>
        </el-card>
        <el-card style="margin-top: 12px">
          <template #header>本人切割轨迹 S02 / 裁定者审核 S03</template>
          <el-table :data="records" size="small">
            <el-table-column prop="attemptNumber" label="#" width="50" />
            <el-table-column prop="status" label="状态" width="110" />
            <el-table-column label="操作">
              <template #default="{ row }">
                <el-button
                  v-if="row.status === 'SUBMITTED'"
                  link
                  type="primary"
                  @click="review(row, 'ACCEPT')"
                >接受</el-button>
                <el-button
                  v-if="row.status === 'SUBMITTED'"
                  link
                  type="warning"
                  @click="review(row, 'RETURN')"
                >退回</el-button>
              </template>
            </el-table-column>
          </el-table>
          <p class="muted">预置关口只读，不在此调用 S04 写入。</p>
          <pre v-if="checkpoint" class="mini">{{ JSON.stringify(checkpoint, null, 2) }}</pre>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getPresetCheckpoint,
  getTextVersion,
  getWorkbench,
  listLabels,
  listSegmentations,
  reviewSegmentation,
  submitSegmentation
} from '@/api/memberA'
import { get } from '@/api/request'

interface Bound {
  startOffset: number
  endOffset: number
}

const route = useRoute()
const loading = ref(false)
const fullText = ref('')
const scopeStart = ref(0)
const scopeEnd = ref(0)
const boundaries = ref<Bound[]>([])
const labels = ref<{ code: string; name: string; category: string }[]>([])
const ctx = ref<Record<string, unknown> | null>(null)
const records = ref<Record<string, unknown>[]>([])
const checkpoint = ref<Record<string, unknown> | null>(null)
const pendingSel = ref<{ start: number; end: number } | null>(null)
const sourceEl = ref<HTMLElement | null>(null)

const displayText = computed(() => fullText.value)

const overlapCount = computed(() => {
  const sorted = [...boundaries.value].sort((a, b) => a.startOffset - b.startOffset)
  let n = 0
  for (let i = 1; i < sorted.length; i++) {
    if (sorted[i].startOffset < sorted[i - 1].endOffset) n++
  }
  return n
})

const gapCount = computed(() => {
  const sorted = [...boundaries.value].sort((a, b) => a.startOffset - b.startOffset)
  let n = 0
  let cursor = scopeStart.value
  for (const b of sorted) {
    if (b.startOffset > cursor) n++
    cursor = Math.max(cursor, b.endOffset)
  }
  if (cursor < scopeEnd.value) n++
  return n
})

function slice(b: Bound) {
  return fullText.value.slice(b.startOffset, b.endOffset)
}

function rememberSelection() {
  const sel = window.getSelection()
  if (!sel || sel.rangeCount === 0 || !sourceEl.value) return
  const range = sel.getRangeAt(0)
  if (!sourceEl.value.contains(range.commonAncestorContainer)) return
  const pre = document.createRange()
  pre.selectNodeContents(sourceEl.value)
  pre.setEnd(range.startContainer, range.startOffset)
  const start = pre.toString().length
  const end = start + range.toString().length
  if (end > start) pendingSel.value = { start, end }
}

function createFromSelection() {
  if (!pendingSel.value) {
    ElMessage.warning('请先在原文中选择一段')
    return
  }
  boundaries.value = [
    ...boundaries.value,
    { startOffset: pendingSel.value.start, endOffset: pendingSel.value.end }
  ].sort((a, b) => a.startOffset - b.startOffset)
}

function suggest() {
  const text = fullText.value
  const next: Bound[] = []
  let start = scopeStart.value
  for (let i = scopeStart.value; i < scopeEnd.value; i++) {
    const ch = text[i]
    if (ch === '。' || ch === '；' || ch === ';') {
      next.push({ startOffset: start, endOffset: i + 1 })
      start = i + 1
    }
  }
  if (start < scopeEnd.value) next.push({ startOffset: start, endOffset: scopeEnd.value })
  boundaries.value = next
}

function splitAt(i: number) {
  const b = boundaries.value[i]
  const mid = Math.floor((b.startOffset + b.endOffset) / 2)
  if (mid <= b.startOffset || mid >= b.endOffset) return
  const copy = [...boundaries.value]
  copy.splice(i, 1, { startOffset: b.startOffset, endOffset: mid }, { startOffset: mid, endOffset: b.endOffset })
  boundaries.value = copy
}

function mergeNext(i: number) {
  const copy = [...boundaries.value]
  copy[i] = { startOffset: copy[i].startOffset, endOffset: copy[i + 1].endOffset }
  copy.splice(i + 1, 1)
  boundaries.value = copy
}

function remove(i: number) {
  boundaries.value = boundaries.value.filter((_, idx) => idx !== i)
}

async function reloadRecords(taskDocumentId: string) {
  const page = await listSegmentations(taskDocumentId)
  const extra = await get<{ items: Record<string, unknown>[] }>('/dev/a/segmentation-records', { taskDocumentId })
  records.value = extra.items
  void page
}

async function submit() {
  if (!ctx.value) return
  await submitSegmentation(String(ctx.value.taskDocumentId), {
    assignmentId: ctx.value.assignmentId,
    boundaries: boundaries.value,
    supersedesSubmissionId: null
  })
  ElMessage.success('已提交独立切割（追加新记录，不覆盖旧提交）')
  await reloadRecords(String(ctx.value.taskDocumentId))
}

async function review(row: Record<string, unknown>, action: 'ACCEPT' | 'RETURN') {
  await reviewSegmentation(
    String(row.id),
    { action, reviewReason: action === 'ACCEPT' ? '边界覆盖范围且无重叠。' : '请按指南调整边界后重提。' },
    Number(row.recordVersion)
  )
  ElMessage.success(action === 'ACCEPT' ? '已接受' : '已退回，请追加新记录')
  await reloadRecords(String(ctx.value?.taskDocumentId))
}

onMounted(async () => {
  loading.value = true
  try {
    const taskId = String(route.params.taskId)
    const wb = await getWorkbench(taskId)
    const item = wb.items[0]
    if (!item) {
      ElMessage.error('当前身份没有可打开的案例')
      return
    }
    ctx.value = item
    const tv = await getTextVersion(String(item.documentTextVersionId))
    fullText.value = tv.normalizedText
    scopeStart.value = Number(item.scopeStartOffset ?? 0)
    scopeEnd.value = Number(item.scopeEndOffset ?? tv.normalizedText.length)
    try {
      labels.value = await listLabels('v-g-12')
    } catch {
      labels.value = []
    }
    try {
      checkpoint.value = await getPresetCheckpoint(String(item.taskDocumentId))
    } catch {
      checkpoint.value = null
    }
    await reloadRecords(String(item.taskDocumentId))
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.source {
  white-space: pre-wrap;
  line-height: 1.8;
  font-size: 16px;
  min-height: 240px;
  user-select: text;
}
.bound {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 13px;
  margin-bottom: 8px;
}
.bound span {
  flex: 1;
}
.checks {
  display: flex;
  gap: 16px;
  margin-bottom: 8px;
  font-size: 13px;
}
.mini {
  max-height: 160px;
  overflow: auto;
  font-size: 11px;
}
</style>
