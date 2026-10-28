<script setup lang="ts">
// 图示审查（成员C，分工.md §四.6）。七类逐条给结论：
//   遗漏关系、错误方向、J/M 混用、非法嵌套、孤立命题、图元错误、多个论证结构识别。
//
// 判定方如实区分（理由见 shared/graphAudit.ts）：
//   后端 —— GR01 的 issues 已含关系级校验（方向/角色/J-M 混用/非法嵌套/环，共用同一个
//           RelationValidator）；草稿再走一次 GR02 得到「完整性」与「孤立命题」。
//           前端不重算规则，避免两处各写一份。
//   前端 —— 「图元错误」后端判不了（最终画成什么样由渲染层决定），按 GR01 的
//           nodes/edges 自查画法契约。
//   信息 —— 「多个论证结构识别」取自 componentCount，是提示不是缺陷。
import { computed, onMounted, ref, watch } from 'vue'
import ArgumentGraph from './ArgumentGraph.vue'
import GraphExportButtons from './GraphExportButtons.vue'
import Icon from '@/shared/Icon.vue'
import { toast } from '@/shared/toast'
import { errorMessageOf } from '@/shared/apiError'
import { componentsOf } from '@/shared/graphGeometry'
import { REL_ANNOTATION_ORDER, REL_META } from '@/shared/relationMeta'
import {
  AUDIT_DIMENSIONS,
  OTHER_DIMENSION,
  categoryOf,
  checkGlyphs,
  groupIssues,
  type AuditDecision,
  type AuditDimension
} from '@/shared/graphAudit'
import { getGraph, validateGraph } from '@/api/graph'
import type { GraphValidateResult, GraphView } from '@/types/graph'
import type { RelationIssue } from '@/types/relation'

interface OwnerOption {
  key: string
  ownerType: 'DRAFT' | 'FINAL_VERSION'
  ownerId: string
  label: string
  note: string
}

/** 演示归属对象。草稿走 GR02 全量审查，定稿只有 GR01 的结构自查（GR02 是草稿接口）。 */
const OWNERS: OwnerOption[] = [
  {
    key: 'DRAFT/md-1',
    ownerType: 'DRAFT',
    ownerId: 'md-1',
    label: '标注草稿 md-1',
    note: 'GR02 全量审查 + GR01 取图。种子草稿 8 命题 7 关系，七类应全绿。'
  },
  {
    key: 'FINAL_VERSION/us-1',
    ownerType: 'FINAL_VERSION',
    ownerId: 'us-1',
    label: '统一定稿 us-1',
    // 如实说明：定稿的关系要等裁定结果写入（D 的 J07/J08），此刻必然是 8 处孤立命题。
    // 这正好当「孤立命题」与「多个论证结构」两类的活样本，不是页面坏了。
    note:
      'GR01 取图与结构自查。关系的写入依赖裁定结果（由 D 的 J07/J08 落库），' +
      '当前尚未就绪，故此处只有 8 条统一命题、0 条关系：8 处孤立命题、8 个独立论证图是如实结论。'
  }
]

const TAB_LABELS = ['一', '二', '三', '四', '五', '六', '七', '八']

/** 归一化后的审查结果：后端 problem 与前端自查共用一种形状，便于统一归组与定位。 */
interface AuditFinding {
  key: string
  level: 'ERROR' | 'WARNING' | 'INFO'
  message: string
  category: string
  /** 出问题的图元 id（关系或命题），点条目时定位到图上 */
  targetId: string | null
  origin: '后端' | '前端'
}

interface DimensionRow {
  dimension: AuditDimension
  count: number
  status: 'ok' | 'warn' | 'error' | 'info'
}

const ownerKey = ref(OWNERS[0].key)
const loading = ref(false)
const graph = ref<GraphView | null>(null)
const validation = ref<GraphValidateResult | null>(null)
const activeCategory = ref<string>('ALL')
const activeNodeId = ref<string | null>(null)
const tabIndex = ref(0)
const graphRef = ref<InstanceType<typeof ArgumentGraph> | null>(null)

const owner = computed(() => OWNERS.find((item) => item.key === ownerKey.value) ?? OWNERS[0])
const nodes = computed(() => graph.value?.nodes ?? [])
const edges = computed(() => graph.value?.edges ?? [])
const componentCount = computed(
  () => validation.value?.componentCount ?? graph.value?.componentCount ?? 0
)
const isolatedIds = computed(
  () => validation.value?.isolatedNodeIds ?? graph.value?.isolatedNodeIds ?? []
)

const backendIssues = computed<RelationIssue[]>(
  () => validation.value?.issues ?? graph.value?.issues ?? []
)

const findings = computed<AuditFinding[]>(() => {
  const list: AuditFinding[] = []

  backendIssues.value.forEach((issue, index) => {
    list.push({
      key: `issue-${index}`,
      level: issue.level,
      message: issue.message,
      category: categoryOf(issue),
      targetId: issue.relationId ?? null,
      origin: '后端'
    })
  })

  // 孤立命题：GR02 会作为 WARNING 下发，非草稿归属只有 GR01 的 isolatedNodeIds 字段。
  const reported = new Set(
    backendIssues.value
      .filter((issue) => issue.code === 'GRAPH_ISOLATED_NODE')
      .map((issue) => issue.message)
  )
  isolatedIds.value.forEach((id) => {
    const message = `孤立命题未参与任何关系：${id}`
    if (reported.has(message)) return
    list.push({
      key: `isolated-${id}`,
      level: 'WARNING',
      message,
      category: 'ISOLATED_PROPOSITION',
      targetId: id,
      origin: '后端'
    })
  })

  checkGlyphs(nodes.value, edges.value).forEach((finding, index) => {
    list.push({
      key: `glyph-${index}`,
      level: 'ERROR',
      message: finding.message,
      category: 'GLYPH_ERROR',
      targetId: finding.targetId,
      origin: '前端'
    })
  })

  if (componentCount.value > 1) {
    list.push({
      key: 'multiple-structures',
      level: 'INFO',
      message: `识别出 ${componentCount.value} 个独立论证图，应分别出图`,
      category: 'MULTIPLE_STRUCTURES',
      targetId: null,
      origin: '后端'
    })
  }

  return list
})

const groups = computed(() => groupIssues(findings.value))

const dimensionRows = computed<DimensionRow[]>(() =>
  [...AUDIT_DIMENSIONS, OTHER_DIMENSION].map((dimension) => {
    const items = groups.value.get(dimension.id) ?? []
    const status: DimensionRow['status'] =
      dimension.decision === 'INFO'
        ? items.length > 0
          ? 'info'
          : 'ok'
        : items.some((item) => item.level === 'ERROR')
          ? 'error'
          : items.length > 0
            ? 'warn'
            : 'ok'
    return { dimension, count: items.length, status }
  })
)

const errorCount = computed(() => findings.value.filter((item) => item.level === 'ERROR').length)
const warnCount = computed(() => findings.value.filter((item) => item.level === 'WARNING').length)

const visibleFindings = computed<AuditFinding[]>(() =>
  activeCategory.value === 'ALL'
    ? findings.value
    : (groups.value.get(activeCategory.value) ?? [])
)

const activeDimension = computed<AuditDimension | null>(() => {
  if (activeCategory.value === 'ALL') return null
  return dimensionRows.value.find((row) => row.dimension.id === activeCategory.value)?.dimension ?? null
})

// ── 右栏：图示（与标注工作台同一套画法）──────────────────────────────

const components = computed(() =>
  componentsOf(
    nodes.value.map((node) => ({
      id: node.id,
      label: node.displayNo ?? node.relationCode ?? node.id
    })),
    edges.value.map((edge) => ({ a: edge.source, b: edge.target }))
  )
)

const tabs = computed(() => [
  '全部',
  ...components.value.map((_, index) => `图示${TAB_LABELS[index] ?? index + 1}`)
])

const visibleNodeIds = computed<Set<string> | null>(() => {
  if (tabIndex.value === 0) return null
  const group = components.value[tabIndex.value - 1]
  return group ? new Set(group) : null
})

const visibleNodes = computed(() => {
  const ids = visibleNodeIds.value
  return ids ? nodes.value.filter((node) => ids.has(node.id)) : nodes.value
})

const visibleEdges = computed(() => {
  const ids = visibleNodeIds.value
  return ids
    ? edges.value.filter((edge) => ids.has(edge.source) && ids.has(edge.target))
    : edges.value
})

const legendItems = computed(() =>
  REL_ANNOTATION_ORDER.map((code) => ({ code, meta: REL_META[code] }))
)

// ── 行为 ──────────────────────────────────────────────────────────────

const DECISION_LABELS: Record<AuditDecision, string> = {
  BACKEND: '后端判定',
  FRONTEND: '前端自查',
  INFO: '信息'
}

const DECISION_BADGE: Record<AuditDecision, string> = {
  BACKEND: 'default',
  FRONTEND: 'accent',
  INFO: 'outline'
}

function statusLabel(row: DimensionRow): string {
  if (row.status === 'error') return `${row.count} 处错误`
  if (row.status === 'warn') return `${row.count} 处提示`
  if (row.status === 'info') return `${row.count} 个独立图`
  return row.dimension.decision === 'INFO' ? '单一结构' : '未发现'
}

function levelColor(level: AuditFinding['level']): string {
  if (level === 'ERROR') return 'var(--danger)'
  if (level === 'WARNING') return 'var(--warn)'
  return 'var(--info)'
}

function levelIcon(level: AuditFinding['level']): string {
  return level === 'ERROR' ? 'alert' : 'info'
}

/** 点条目定位到图上的对应图元（关系节点或命题节点）。 */
function locate(finding: AuditFinding): void {
  if (!finding.targetId) return
  activeNodeId.value = finding.targetId
  graphRef.value?.fit()
}

function zoom(factor: number): void {
  graphRef.value?.zoomBy(factor)
}

function refit(): void {
  graphRef.value?.fit()
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const current = owner.value
    const view = await getGraph(current.ownerType, current.ownerId)
    graph.value = view
    validation.value =
      current.ownerType === 'DRAFT'
        ? await validateGraph(current.ownerId, {
            elementIds: view.nodes.filter((node) => node.kind === 'ELEMENT').map((node) => node.id),
            relationIds: view.nodes
              .filter((node) => node.kind === 'RELATION')
              .map((node) => node.id)
          })
        : null
    tabIndex.value = 0
    activeNodeId.value = null
  } catch (error) {
    graph.value = null
    validation.value = null
    toast(errorMessageOf(error), 'err')
  } finally {
    loading.value = false
  }
}

watch(ownerKey, load)
watch(activeCategory, () => {
  activeNodeId.value = null
})
onMounted(load)
</script>

<template>
  <div class="wb audit">
    <!-- ── 左：审查类别清单 ─────────────────────────────────────── -->
    <section class="wb-col wb-left">
      <div class="wb-col-head">
        <h3>图示审查</h3>
        <span style="flex: 1"></span>
        <span class="badge" :class="errorCount ? 'danger' : warnCount ? 'warn' : 'success'">
          {{ errorCount ? `${errorCount} 错误` : warnCount ? `${warnCount} 提示` : '全部通过' }}
        </span>
      </div>
      <div class="wb-col-body">
        <div class="op-sec">
          <h4>审查对象</h4>
          <div class="seg" style="width: 100%">
            <button
              v-for="option in OWNERS"
              :key="option.key"
              style="flex: 1 1 0"
              :class="{ 'is-active': ownerKey === option.key }"
              @click="ownerKey = option.key"
            >
              {{ option.label }}
            </button>
          </div>
          <p class="t-xs muted" style="margin-top: 6px">{{ owner.note }}</p>
          <p class="t-xs muted">
            图示状态
            <b :style="{ color: graph?.status === 'VALID' ? 'var(--success)' : 'var(--danger)' }">
              {{ graph?.status ?? '—' }}
            </b>
            · 布局 v{{ graph?.layoutVersion ?? 0 }}
            · {{ nodes.length }} 图元 / {{ edges.length }} 连线
          </p>
        </div>

        <div class="op-sec">
          <h4>审查类别</h4>
          <p class="t-xs muted">
            分工.md §四.6 的七类逐条给结论。判定方如实标注：后端判定取自关系校验器与构图逻辑，
            前端自查只负责「图元错误」这一类。
          </p>
          <button
            class="audit-row is-all"
            :class="{ 'is-active': activeCategory === 'ALL' }"
            @click="activeCategory = 'ALL'"
          >
            <span
              class="audit-dot"
              :style="{
                background: errorCount ? 'var(--danger)' : warnCount ? 'var(--warn)' : 'var(--success)'
              }"
            />
            <span class="audit-name">全部发现</span>
            <span class="t-xs muted">{{ errorCount }} 错误 · {{ warnCount }} 提示</span>
          </button>
          <button
            v-for="row in dimensionRows"
            :key="row.dimension.id"
            class="audit-row"
            :class="{ 'is-active': activeCategory === row.dimension.id }"
            @click="activeCategory = row.dimension.id"
          >
            <span
              class="audit-dot"
              :style="{
                background:
                  row.status === 'error'
                    ? 'var(--danger)'
                    : row.status === 'warn'
                      ? 'var(--warn)'
                      : row.status === 'info'
                        ? 'var(--info)'
                        : 'var(--success)'
              }"
            />
            <span class="audit-name">{{ row.dimension.label }}</span>
            <span class="t-xs muted">{{ statusLabel(row) }}</span>
          </button>
        </div>

        <div class="op-sec" style="border-bottom: 0">
          <p class="t-xs muted">
            判定方：
            <span class="badge default">{{ DECISION_LABELS.BACKEND }}</span>
            <span class="badge accent">{{ DECISION_LABELS.FRONTEND }}</span>
            <span class="badge outline">{{ DECISION_LABELS.INFO }}</span>
          </p>
        </div>
      </div>
    </section>

    <div class="splitter" />

    <!-- ── 中：发现明细 ─────────────────────────────────────────── -->
    <section class="wb-col wb-center">
      <div class="wb-col-head">
        <h3>{{ activeDimension ? activeDimension.label : '全部发现' }}</h3>
        <span style="flex: 1"></span>
        <span v-if="activeDimension" class="badge" :class="DECISION_BADGE[activeDimension.decision]">
          {{ DECISION_LABELS[activeDimension.decision] }}
        </span>
        <span class="badge" :class="graph?.status === 'VALID' ? 'success' : 'danger'">
          {{ graph?.status ?? '—' }}
        </span>
      </div>

      <div class="wb-col-body audit-body">
        <p v-if="activeDimension" class="note info">
          <Icon name="info" />
          <span class="note-body">{{ activeDimension.hint }}</span>
        </p>
        <p
          v-else
          class="note info"
        >
          <Icon name="info" />
          <span class="note-body">
            七类合计 {{ findings.length }} 项发现。点击任意条目可在右栏图上定位到对应图元。
          </span>
        </p>

        <div v-if="loading" class="empty">
          <div class="empty-ico"><Icon name="refresh" /></div>
          <h4>正在读取图示…</h4>
        </div>

        <div v-else-if="!visibleFindings.length" class="empty">
          <div class="empty-ico"><Icon name="checkc" /></div>
          <h4>{{ activeDimension ? `未发现${activeDimension.label}问题` : '未发现任何问题' }}</h4>
          <p class="t-xs muted" style="margin-top: 6px">
            {{ owner.label }} 的图示通过了{{ activeDimension ? '这一类审查' : '全部审查' }}。
          </p>
        </div>

        <div v-else class="audit-list">
          <button
            v-for="finding in visibleFindings"
            :key="finding.key"
            class="audit-item"
            :class="{ 'is-active': finding.targetId !== null && finding.targetId === activeNodeId }"
            @click="locate(finding)"
          >
            <span class="audit-line" :style="{ color: levelColor(finding.level) }">
              <Icon :name="levelIcon(finding.level)" />
              <b class="t-xs">{{ finding.level }}</b>
            </span>
            <span class="audit-text">{{ finding.message }}</span>
            <span class="audit-tags">
              <span class="badge" :class="DECISION_BADGE[finding.origin === '前端' ? 'FRONTEND' : 'BACKEND']">
                {{ finding.origin }}
              </span>
              <span v-if="finding.targetId" class="t-xs muted">{{ finding.targetId }}</span>
            </span>
          </button>
        </div>

        <p class="note warn" style="margin-top: 10px">
          <Icon name="info" />
          <span class="note-body">
            「多个论证结构识别」不是缺陷：连通分量多于一个说明本案存在多个独立论证图，
            出图与评审时应分别处理（右栏可用「图示一 / 图示二」分别查看）。
          </span>
        </p>
      </div>
    </section>

    <div class="splitter" />

    <!-- ── 右：论证图 ───────────────────────────────────────────── -->
    <section class="wb-col wb-right">
      <div class="graph-toolbar">
        <div class="seg">
          <button
            v-for="(tab, index) in tabs"
            :key="tab"
            :class="{ 'is-active': tabIndex === index }"
            @click="tabIndex = index"
          >
            {{ tab }}
          </button>
        </div>
        <span style="flex: 1"></span>
        <span class="t-xs muted">独立论证图 {{ componentCount }} 个</span>
        <button class="btn btn-icon btn-ghost" title="适应窗口" @click="refit">
          <Icon name="fit" />
        </button>
        <button class="btn btn-icon btn-ghost" title="放大" @click="zoom(1.15)">
          <Icon name="zin" />
        </button>
        <button class="btn btn-icon btn-ghost" title="缩小" @click="zoom(0.87)">
          <Icon name="zout" />
        </button>
        <GraphExportButtons :target="graphRef" :name="`图示审查-${owner.ownerId}`" />
      </div>

      <ArgumentGraph
        v-if="nodes.length"
        ref="graphRef"
        style="flex: 1 1 auto; min-height: 0"
        :nodes="visibleNodes"
        :edges="visibleEdges"
        :positions="graph?.positions ?? []"
        :active-id="activeNodeId"
        @update:active-id="activeNodeId = $event"
      />
      <div v-else class="empty" style="flex: 1 1 auto">
        <div class="empty-ico"><Icon name="grid" /></div>
        <h4>没有可审查的图示</h4>
        <p class="t-xs muted">{{ owner.label }} 尚无命题与关系。</p>
      </div>

      <div class="graph-legend">
        <span v-for="item in legendItems" :key="item.code" class="lg">
          <span :style="{ color: item.meta.color, fontFamily: 'var(--font-mono)' }">
            {{ item.meta.glyph }}
          </span>
          {{ item.code }} {{ item.meta.name }}
          <span class="muted">（{{ item.meta.directed ? '有向' : '无向' }}）</span>
        </span>
        <span class="lg muted">图元画法依据 relation-definitions.json 的 diagramRule</span>
      </div>
    </section>
  </div>
</template>

<style scoped>
/* 复用 .wb / .seg / .badge / .note / .empty / .graph-*，只补审查清单与条目这两处新形状。 */
.wb {
  margin: calc(var(--sp-6) * -1) calc(var(--sp-6) * -1) calc(var(--sp-9) * -1);
}
@media (max-width: 900px) {
  .wb {
    margin: calc(var(--sp-4) * -1) calc(var(--sp-4) * -1) calc(var(--sp-8) * -1);
  }
}

/* 中栏没有 .op-sec 包裹，自己补内边距（左栏的 .op-sec 已自带）。 */
.audit-body {
  padding: 14px;
}

.audit-row {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 7px 9px;
  margin-top: 4px;
  border: 1px solid transparent;
  border-radius: var(--r-sm, 8px);
  background: transparent;
  color: var(--fg-2);
  font-size: 12.5px;
  text-align: left;
  cursor: pointer;
}
.audit-row:hover {
  background: var(--surface-3);
}
.audit-row.is-active {
  background: var(--surface-3);
  border-color: var(--accent-line);
  color: var(--fg);
}
.audit-row.is-all {
  margin-bottom: 8px;
  border-bottom: 1px solid var(--border);
  border-radius: var(--r-sm, 8px) var(--r-sm, 8px) 0 0;
}
.audit-name {
  flex: 1 1 auto;
}
.audit-dot {
  width: 8px;
  height: 8px;
  flex: 0 0 8px;
  border-radius: 50%;
}

.audit-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 10px;
}
.audit-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  width: 100%;
  padding: 9px 11px;
  border: 1px solid var(--border);
  border-radius: var(--r-sm, 8px);
  background: var(--surface-2);
  color: var(--fg-2);
  text-align: left;
  cursor: pointer;
}
.audit-item:hover {
  border-color: var(--accent-line);
}
.audit-item.is-active {
  border-color: var(--accent);
  background: var(--accent-soft);
}
.audit-line {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: 0 0 auto;
}
.audit-line svg {
  width: 14px;
  height: 14px;
  stroke: currentColor;
  fill: none;
  stroke-width: 1.8;
}
.audit-text {
  flex: 1 1 auto;
  font-size: 12.5px;
  line-height: 1.55;
}
.audit-tags {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 0 0 auto;
}
</style>
