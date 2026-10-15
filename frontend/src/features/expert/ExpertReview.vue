<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { toast } from '@/shared/toast'
import {
  listMyExpertReviews,
  getExpertReviewDetail,
  startExpertReview,
  submitIssueResolution,
  confirmExpertReview
} from '@/api/expert'
import type {
  ExpertReviewSummary,
  ExpertReviewDetail,
  ExpertIssue
} from '@/types/expert'

const STATUS_OPTIONS = ['', 'REQUESTED', 'IN_REVIEW', 'CONFIRMED']

const loadingList = ref(true)
const listError = ref(false)
const summaries = ref<ExpertReviewSummary[]>([])
const filterStatus = ref('')
const selectedId = ref<string>('')

const detailLoading = ref(false)
const detailError = ref(false)
const detail = ref<ExpertReviewDetail | null>(null)

const accepting = ref(false)
const confirming = ref(false)
const resolvingId = ref('')
const resolutionDraft = ref<Record<string, { resolution: string; guideBasis: string }>>({})

const openCount = computed(() => {
  if (!detail.value) return 0
  return detail.value.issues.filter((i) => i.status === 'OPEN').length
})

async function loadList() {
  loadingList.value = true
  listError.value = false
  try {
    const r = await listMyExpertReviews({
      status: filterStatus.value || undefined
    })
    summaries.value = r.items
    if (!selectedId.value && r.items.length) {
      selectedId.value = r.items[0].review.id
      await loadDetail()
    }
  } catch {
    listError.value = true
  } finally {
    loadingList.value = false
  }
}

async function loadDetail() {
  if (!selectedId.value) {
    detail.value = null
    return
  }
  detailLoading.value = true
  detailError.value = false
  try {
    detail.value = await getExpertReviewDetail(selectedId.value)
    for (const issue of detail.value.issues) {
      if (!resolutionDraft.value[issue.id]) {
        resolutionDraft.value[issue.id] = { resolution: '', guideBasis: '' }
      }
    }
  } catch {
    detailError.value = true
  } finally {
    detailLoading.value = false
  }
}

async function select(id: string) {
  selectedId.value = id
  await loadDetail()
}

async function onAccept() {
  if (!detail.value) return
  accepting.value = true
  try {
    await startExpertReview(detail.value.review.id, detail.value.review.recordVersion)
    await loadDetail()
    toast('已接受复核申请')
  } catch {
    /* 错误已由拦截器提示 */
  } finally {
    accepting.value = false
  }
}

async function onResolve(issue: ExpertIssue) {
  const draft = resolutionDraft.value[issue.id] || { resolution: '', guideBasis: '' }
  if (!draft.resolution.trim() || !draft.guideBasis.trim()) {
    toast('结论和指南依据都要填', 'err')
    return
  }
  resolvingId.value = issue.id
  try {
    await submitIssueResolution(
      issue.id,
      { resolution: draft.resolution.trim(), guideBasis: draft.guideBasis.trim() },
      issue.recordVersion
    )
    await loadDetail()
    toast('已提交结论')
  } catch {
    /* 拦截器已提示 */
  } finally {
    resolvingId.value = ''
  }
}

async function onConfirm() {
  if (!detail.value) return
  confirming.value = true
  try {
    await confirmExpertReview(detail.value.review.id, detail.value.review.recordVersion)
    await Promise.all([loadList(), loadDetail()])
    toast('复核已确认完成')
  } catch {
    /* 拦截器已提示 */
  } finally {
    confirming.value = false
  }
}

function contextFor(issueId: string) {
  if (!detail.value) return null
  return detail.value.contexts.find((c) => c.issueId === issueId) || null
}

onMounted(loadList)
</script>

<template>
  <div>
    <div class="page-head between">
      <div>
        <h1 class="t-h1">专家复核工作台</h1>
        <div class="row" style="margin-top: 8px; gap: 8px">
          <span class="badge outline">当前专家：演示专家（尾号 0010）</span>
        </div>
      </div>
    </div>

    <!-- 状态过滤 -->
    <div class="card" style="margin: 16px 0">
      <div class="card-body">
        <div class="row wrap" style="gap: 8px; align-items: center">
          <span class="t-xs muted">状态</span>
          <button
            v-for="s in STATUS_OPTIONS"
            :key="s || 'all'"
            class="btn"
            :class="filterStatus === s ? 'btn-primary' : 'btn-secondary'"
            @click="filterStatus = s"
          >{{ s || '全部' }}</button>
        </div>
      </div>
    </div>

    <div class="split">
      <!-- 左：复核单列表 -->
      <div class="left">
        <div v-if="loadingList" class="card"><div class="card-body">加载待办…</div></div>
        <div v-else-if="listError" class="card">
          <div class="card-body between">
            <span>加载失败。</span>
            <button class="btn btn-primary" @click="loadList">重试</button>
          </div>
        </div>
        <div v-else-if="summaries.length === 0" class="card">
          <div class="card-body">当前没有复核单。</div>
        </div>
        <div v-else class="stack-5">
          <div
            v-for="s in summaries"
            :key="s.review.id"
            class="card clickable"
            :class="{ active: selectedId === s.review.id }"
            @click="select(s.review.id)"
          >
            <div class="card-body">
              <div class="row between">
                <span class="badge">{{ s.review.status }}</span>
                <span class="t-xs muted">{{ s.review.id.slice(0, 8) }}</span>
              </div>
              <p class="t-sm" style="margin: 8px 0 0">
                问题 {{ s.issueCount }} · 未解决 {{ s.openIssueCount }}
              </p>
            </div>
          </div>
        </div>
      </div>

      <!-- 右：详情 -->
      <div class="right">
        <div v-if="!selectedId" class="card">
          <div class="card-body">从左侧选择一张复核单。</div>
        </div>
        <div v-else-if="detailLoading" class="card">
          <div class="card-body">加载详情…</div>
        </div>
        <div v-else-if="detailError" class="card">
          <div class="card-body between">
            <span>详情加载失败。</span>
            <button class="btn btn-primary" @click="loadDetail">重试</button>
          </div>
        </div>
        <div v-else-if="detail" class="stack-5">
          <div class="card">
            <div class="card-head">
              <div class="row" style="gap: 8px">
                <span class="badge">{{ detail.review.status }}</span>
                <span class="t-xs muted">会话 {{ detail.review.sessionId.slice(0, 8) }}</span>
              </div>
              <span class="t-xs muted">版本 v{{ detail.review.recordVersion }}</span>
            </div>
            <div class="card-body" v-if="detail.review.status === 'REQUESTED'">
              <button class="btn btn-primary" :disabled="accepting" @click="onAccept">
                {{ accepting ? '接受中…' : '接受任务' }}
              </button>
            </div>
          </div>

          <div v-for="issue in detail.issues" :key="issue.id" class="card">
            <div class="card-head">
              <div class="row" style="gap: 8px">
                <span class="badge outline">{{ issue.itemKind }}</span>
                <span class="badge" :class="issue.status === 'OPEN' ? '' : 'success'">{{ issue.status }}</span>
              </div>
            </div>
            <div class="card-body">
              <p class="t-sm" style="margin-top: 0">{{ issue.question }}</p>

              <!-- 原文摘录（PROPOSITION 问题） -->
              <template v-if="contextFor(issue.id)">
                <div
                  v-for="g in contextFor(issue.id)!.candidateGroups"
                  :key="g.versionId"
                  class="card"
                  style="margin-bottom: 8px"
                >
                  <div class="card-body tight">
                    <div class="section-label">源版本 {{ g.displayCode }}</div>
                    <p v-for="(t, i) in g.texts" :key="i" class="t-sm" style="margin: 4px 0 0">{{ t }}</p>
                  </div>
                </div>
              </template>

              <!-- 已解决：只读展示 -->
              <div v-if="issue.status === 'RESOLVED'" class="note">
                <div><strong>专家结论：</strong>{{ issue.resolution }}</div>
                <div style="margin-top: 4px"><strong>指南依据：</strong>{{ issue.guideBasis }}</div>
              </div>

              <!-- OPEN：填结论 -->
              <div v-else style="margin-top: 12px">
                <input
                  v-model="resolutionDraft[issue.id].resolution"
                  class="input"
                  placeholder="专家结论"
                  style="width: 100%; margin-bottom: 8px; padding: 6px 8px"
                />
                <input
                  v-model="resolutionDraft[issue.id].guideBasis"
                  class="input"
                  placeholder="指南依据"
                  style="width: 100%; margin-bottom: 8px; padding: 6px 8px"
                />
                <button
                  class="btn btn-primary"
                  :disabled="resolvingId === issue.id"
                  @click="onResolve(issue)"
                >{{ resolvingId === issue.id ? '提交中…' : '提交结论' }}</button>
              </div>
            </div>
          </div>

          <!-- 全部解决后可确认 -->
          <div v-if="detail.review.status === 'IN_REVIEW' && openCount === 0" class="card">
            <div class="card-body between">
              <span>所有问题已解决，可以确认完成。</span>
              <button class="btn btn-primary" :disabled="confirming" @click="onConfirm">
                {{ confirming ? '确认中…' : '确认完成' }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.split {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}
.left {
  flex: 0 0 300px;
  min-width: 260px;
}
.right {
  flex: 1;
  min-width: 320px;
}
.clickable {
  cursor: pointer;
}
.clickable.active {
  outline: 2px solid var(--el-color-primary, #409eff);
}
</style>
