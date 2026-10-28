<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listMyExpertReviews, getExpertReviewDetail } from '@/api/expert'
import type { ExpertReviewSummary, ExpertReviewDetail } from '@/types/expert'

interface Entry {
  summary: ExpertReviewSummary
  detail: ExpertReviewDetail | null
  failed: boolean
}

const loading = ref(true)
const error = ref(false)
const entries = ref<Entry[]>([])

async function load() {
  loading.value = true
  error.value = false
  try {
    const r = await listMyExpertReviews({ status: 'CONFIRMED' })
    // 先建条目，再并行拉每张的详情；单张失败不影响整页。
    entries.value = r.items.map((s) => ({ summary: s, detail: null, failed: false }))
    await Promise.allSettled(
      entries.value.map(async (e) => {
        try {
          e.detail = await getExpertReviewDetail(e.summary.review.id)
        } catch {
          e.failed = true
        }
      })
    )
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="page-head between">
      <div>
        <h1 class="t-h1">疑难问题说明文档</h1>
        <p class="t-sm muted" style="margin: 8px 0 0">已确认的核心概念分歧、专家结论与指南依据。只读。</p>
      </div>
    </div>

    <div v-if="loading" class="card"><div class="card-body">加载已确认问题…</div></div>
    <div v-else-if="error" class="card">
      <div class="card-body between">
        <span>加载失败。</span>
        <button class="btn btn-primary" @click="load">重试</button>
      </div>
    </div>
    <div v-else-if="entries.length === 0" class="card">
      <div class="card-body">还没有已确认的复核单。</div>
    </div>

    <div v-else class="stack-5">
      <div v-for="e in entries" :key="e.summary.review.id" class="card">
        <div class="card-head">
          <div class="row" style="gap: 8px">
            <span class="badge success">CONFIRMED</span>
            <span class="t-xs muted">会话 {{ e.summary.review.sessionId.slice(0, 8) }}</span>
          </div>
        </div>
        <div class="card-body">
          <div v-if="e.failed" class="note">该复核单详情加载失败，可稍后刷新重试。</div>
          <template v-else-if="e.detail">
            <div v-for="issue in e.detail.issues" :key="issue.id" class="card" style="margin-bottom: 8px">
              <div class="card-body tight">
                <div class="row" style="gap: 8px">
                  <span class="badge outline">{{ issue.itemKind }}</span>
                  <span class="t-xs muted">{{ issue.status }}</span>
                </div>
                <p class="t-sm" style="margin: 6px 0">{{ issue.question }}</p>
                <div class="note">
                  <div><strong>专家结论：</strong>{{ issue.resolution || '—' }}</div>
                  <div style="margin-top: 4px"><strong>指南依据：</strong>{{ issue.guideBasis || '—' }}</div>
                </div>
              </div>
            </div>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>
