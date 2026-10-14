<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import Chip from '@/shared/Chip.vue'
import { toast } from '@/shared/toast'
import { listPropositionItems } from '@/api/adjudication'
import type { J03View } from '@/types/adjudication'

const route = useRoute()
const sessionId =
  (route.query.sessionId as string) || 'aba39933-394e-41e0-aa76-1cf8acc57ba7'

const loading = ref(true)
const error = ref(false)
const view = ref<J03View | null>(null)
const filterStatus = ref('')
const filterConflict = ref('')

const STATUS_OPTIONS = ['', 'PENDING', 'DECIDED']
const CONFLICT_OPTIONS = ['', 'LABEL', 'AGREEMENT']

const filtered = computed(() => {
  if (!view.value) return []
  return view.value.items.filter((it) => {
    if (filterStatus.value && it.item.status !== filterStatus.value) return false
    if (filterConflict.value && it.item.conflictType !== filterConflict.value) return false
    return true
  })
})

async function load() {
  loading.value = true
  error.value = false
  try {
    view.value = await listPropositionItems(sessionId, {})
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
}

onMounted(load)

function escalate() {
  toast('提交专家将在 M4.2 接入', 'err')
}
</script>

<template>
  <div>
    <div class="page-head between">
      <div>
        <h1 class="t-h1">冲突解决 · 命题裁定</h1>
        <div class="row wrap" style="margin-top: 8px; gap: 8px">
          <template v-if="view">
            <span class="badge outline">会话 {{ view.session.id.slice(0, 8) }}</span>
            <span class="badge accent">{{ view.session.status }}</span>
            <span class="t-xs muted">共 {{ view.items.length }} 个命题项</span>
          </template>
        </div>
      </div>
      <button class="btn btn-secondary" @click="escalate">提交专家</button>
    </div>

    <!-- 过滤条 -->
    <div class="card" style="margin: 16px 0">
      <div class="card-body">
        <div class="row wrap" style="gap: 8px; align-items: center">
          <span class="t-xs muted">状态</span>
          <button
            v-for="s in STATUS_OPTIONS"
            :key="s || 'all-s'"
            class="btn"
            :class="filterStatus === s ? 'btn-primary' : 'btn-secondary'"
            @click="filterStatus = s"
          >{{ s || '全部' }}</button>
          <span class="t-xs muted" style="margin-left: 16px">分歧类型</span>
          <button
            v-for="c in CONFLICT_OPTIONS"
            :key="c || 'all-c'"
            class="btn"
            :class="filterConflict === c ? 'btn-primary' : 'btn-secondary'"
            @click="filterConflict = c"
          >{{ c || '全部' }}</button>
        </div>
      </div>
    </div>

    <!-- 加载 / 失败 / 空 -->
    <div v-if="loading" class="card"><div class="card-body">加载命题项…</div></div>
    <div v-else-if="error" class="card">
      <div class="card-body between">
        <span>加载失败，请检查后端是否启动。</span>
        <button class="btn btn-primary" @click="load">重试</button>
      </div>
    </div>
    <div v-else-if="filtered.length === 0" class="card">
      <div class="card-body">当前筛选下没有命题项。</div>
    </div>

    <!-- 命题项列表 -->
    <div v-else class="stack-5">
      <div v-for="it in filtered" :key="it.item.id" class="card">
        <div class="card-head">
          <div class="row wrap" style="gap: 8px">
            <span class="badge">{{ it.item.conflictType }}</span>
            <span class="badge outline">{{ it.item.status }}</span>
            <span class="t-xs muted">{{ it.item.candidateElementIds.length }} 个候选</span>
          </div>
          <span class="t-xs muted">裁定结果：{{ it.currentDecision ? '已定' : '未裁定' }}</span>
        </div>
        <div class="card-body">
          <div class="grid-2" style="gap: 16px">
            <div v-for="g in it.candidates" :key="g.versionId">
              <div class="section-label">源版本 {{ g.displayCode }}</div>
              <div v-for="el in g.elements" :key="el.id" class="card" style="margin-bottom: 8px">
                <div class="card-body tight">
                  <div class="row between">
                    <span class="mono t-xs muted">{{ el.displayNo }}</span>
                    <Chip :code="el.labelCode" />
                  </div>
                  <p class="t-sm" style="margin: 6px 0 0">{{ el.text }}</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
