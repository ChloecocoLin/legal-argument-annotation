<script setup lang="ts">
// 关系裁定页（成员C）：分工.md §四-4 的界面部分 —— 冲突解决的第二阶段（命题裁定之后）。
//
// 链路：J06 读出关系差异与一致项 → 逐项决定（采用某版 / 自定义 / 删除 / 提交专家 / 退回 / 确认一致）
//      → 「生成关系裁定结果」试算 → 正式论证图（分工.md §四-8 的交付物）。
//
// 边界：**决定不落库**。裁定记录、版本与流程框架（J07/J08）归成员D，本页不伪造保存成功：
// 它是 D 的写接口就绪前的试算，也是「一次完整裁定」的可视化验收面。
// 三栏样式复用 app.css §10/§11（.wb / .diff-* / .graph-* / .stat-card / .note / .seg）。
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import ArgumentGraph from '../annotation/ArgumentGraph.vue'
import BaseModal from '@/shared/BaseModal.vue'
import Chip from '@/shared/Chip.vue'
import Icon from '@/shared/Icon.vue'
import { toast } from '@/shared/toast'
import { errorCodeOf, errorMessageOf } from '@/shared/apiError'
import { REL_ANNOTATION_ORDER, REL_META } from '@/shared/relationMeta'
import {
  checkParticipant,
  orderedInputs,
  parseDisplayNo,
  type CandidateCheck,
  type CandidateInfo
} from '@/shared/relationRules'
import { listMockPropositions } from '@/api/relation'
import {
  listRelationItems,
  resolveRelationResult
} from '@/api/relationAdjudication'
import type { RelationType } from '@/types/common'
import type { MockProposition, ParticipantInput, ParticipantRole } from '@/types/relation'
import type {
  RelationAdjudicationItem,
  RelationAdjudicationResult,
  RelationCandidate,
  RelationConflictType,
  RelationDecisionAction,
  RelationDecisionInput
} from '@/types/relationAdjudication'

/** 【临时】演示会话：ses-1 命题已冻结、8 条裁定项覆盖全部五类；ses-3 演示悬空端点。 */
const DEMO_SESSION_ID = 'ses-1'
/** 【临时】统一命题集合。正式来源是 D 的 J05（命题阶段冻结产出），接口就绪后由会话带出。 */
const UNIFIED_SNAPSHOT_ID = 'us-1'
/** J06 一次取满：过滤在本地做，否则翻页会把「本页是否已裁」截断成错的进度。 */
const PAGE_SIZE = 100

const SCOPES = [
  { value: 'ALL', label: '全部' },
  { value: 'PENDING', label: '待裁' },
  { value: 'DECIDED', label: '已裁' }
] as const

const CONFLICT_TYPES: RelationConflictType[] = [
  'EXISTENCE',
  'TYPE',
  'DIRECTION',
  'PARTICIPANTS',
  'AGREEMENT'
]

/** conflictType → 界面文案与徽章色。dimension 更细一档，两者同时显示。 */
const CONFLICT_META: Record<RelationConflictType, { label: string; badge: string }> = {
  EXISTENCE: { label: '存在性', badge: 'warn' },
  TYPE: { label: '关系类型', badge: 'info' },
  DIRECTION: { label: '方向', badge: 'accent' },
  PARTICIPANTS: { label: '参与项', badge: 'danger' },
  AGREEMENT: { label: '一致', badge: 'success' }
}

const ROLE_LABELS: Record<ParticipantRole, string> = {
  SOURCE: '来源',
  TARGET: '目标',
  MEMBER: '成员'
}

/** 不消费裁定项的两个动作：项留在待办里（contract §5）。 */
const DEFERRED_ACTIONS: RelationDecisionAction[] = ['RETURN', 'ESCALATE_EXPERT']

const ACTION_LABELS: Record<RelationDecisionAction, string> = {
  ADOPT_VERSION: '采用某版本',
  CUSTOM: '自定义关系',
  DELETE: '删除关系',
  RETURN: '退回重标',
  ESCALATE_EXPERT: '提交专家',
  CONFIRM_AGREEMENT: '确认一致'
}

/** 自定义关系弹窗里的候选行：命题来自统一命题集合，关系来自本次已保留的内层关系。 */
interface CustomRow {
  info: CandidateInfo
  label: string
  text: string
  labelCode: string | null
}

const route = useRoute()
const sessionId = computed(() => (route.query.session as string) || DEMO_SESSION_ID)

const loading = ref(false)
const generating = ref(false)
const loadError = ref('')
const items = ref<RelationAdjudicationItem[]>([])
const unifiedProps = ref<MockProposition[]>([])
const result = ref<RelationAdjudicationResult | null>(null)
const graphRef = ref<InstanceType<typeof ArgumentGraph> | null>(null)

/** 本页的裁定草稿：itemId → 决定。**不落库**（D 的 J07 就绪后由后端持久化）。 */
const decisions = reactive<Record<string, RelationDecisionInput | undefined>>({})
/** 理由单独存：决定在点按钮时落，理由可能之后才写完，出参时才拼进去。 */
const reasons = reactive<Record<string, string>>({})

const activeItemId = ref<string | null>(null)
const scopeFilter = ref<(typeof SCOPES)[number]['value']>('ALL')
const typeFilter = ref<RelationConflictType | 'ALL'>('ALL')

const custom = reactive({
  open: false,
  itemId: '',
  code: null as RelationType | null,
  picked: [] as CandidateInfo[],
  error: ''
})

// ── 加载 ──────────────────────────────────────────────────────────────

onMounted(load)
watch(sessionId, load)

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  result.value = null
  items.value = []
  activeItemId.value = null
  Object.keys(decisions).forEach((key) => delete decisions[key])
  Object.keys(reasons).forEach((key) => delete reasons[key])
  try {
    const [page, propositions] = await Promise.all([
      listRelationItems(sessionId.value, { page: 1, size: PAGE_SIZE }),
      listMockPropositions(UNIFIED_SNAPSHOT_ID)
    ])
    items.value = page.items
    unifiedProps.value = propositions
    items.value.forEach((item) => {
      reasons[item.id] = ''
    })
    activeItemId.value = items.value[0]?.id ?? null
  } catch (error) {
    loadError.value = `${errorCodeOf(error)} · ${errorMessageOf(error)}`
  } finally {
    loading.value = false
  }
}

/** 门禁文案：命题阶段没冻死就不许开关系裁定（硬顺序）。 */
const gateHint = computed(() => {
  if (loadError.value.includes('PROPOSITIONS_NOT_COMPLETE')) {
    return '命题阶段尚未冻结，不能开始关系裁定。硬顺序：先完成命题裁定（J05），关系端点才有可比对的统一命题集合。'
  }
  if (loadError.value.includes('INSUFFICIENT_VERSIONS')) {
    return '会话的源版本少于两个，没有可比对的双方。'
  }
  if (loadError.value.includes('NOT_FOUND')) {
    return '裁定会话不存在。可用 ?session=ses-1（主演示）或 ?session=ses-3（悬空端点）试。'
  }
  return loadError.value
})

// ── 列表与进度 ────────────────────────────────────────────────────────

const activeItem = computed(
  () => items.value.find((item) => item.id === activeItemId.value) ?? null
)

function isDecided(item: RelationAdjudicationItem): boolean {
  const decision = decisions[item.id]
  return !!decision && !DEFERRED_ACTIONS.includes(decision.action)
}

function versionLabelOf(item: RelationAdjudicationItem, relationId?: string | null): string {
  if (!relationId) return ''
  return item.candidates.find((c) => c.relationId === relationId)?.versionLabel ?? ''
}

/** 列表行的一行摘要：两侧表达式并排。 */
function summaryOf(item: RelationAdjudicationItem): string {
  if (item.candidates.length === 0) return '（无候选）'
  return item.candidates
    .map((c) => `${c.versionLabel} ${c.expression}`)
    .join('   ≠   ')
}

function stateLabel(item: RelationAdjudicationItem): string {
  const decision = decisions[item.id]
  if (!decision) return '未裁'
  if (decision.action === 'RETURN') return '已退回'
  if (decision.action === 'ESCALATE_EXPERT') return '待专家'
  if (decision.action === 'ADOPT_VERSION') {
    return `已裁 · 采用 ${versionLabelOf(item, decision.sourceObjectId)}`
  }
  if (decision.action === 'CONFIRM_AGREEMENT') return '已裁 · 确认一致'
  return ACTION_LABELS[decision.action].replace('关系', '')
}

function indexOfItem(itemId: string): number {
  return items.value.findIndex((item) => item.id === itemId) + 1
}

const visibleItems = computed(() =>
  items.value.filter((item) => {
    if (typeFilter.value !== 'ALL' && item.conflictType !== typeFilter.value) return false
    if (scopeFilter.value === 'PENDING') return !isDecided(item)
    if (scopeFilter.value === 'DECIDED') return isDecided(item)
    return true
  })
)

const conflictCount = computed(
  () => items.value.filter((item) => item.conflictType !== 'AGREEMENT').length
)
const agreementCount = computed(() => items.value.length - conflictCount.value)
const decidedCount = computed(() => items.value.filter(isDecided).length)
const progressPercent = computed(() =>
  items.value.length === 0 ? 0 : Math.round((decidedCount.value / items.value.length) * 100)
)

function countOfType(type: RelationConflictType): number {
  return items.value.filter((item) => item.conflictType === type).length
}

// ── 裁定动作 ──────────────────────────────────────────────────────────

/** 采用某个候选。**含悬空端点的候选一律拦住**：宁可不让裁定，也不能对着不可比的候选做决定。 */
function adopt(item: RelationAdjudicationItem, candidate: RelationCandidate): void {
  if (candidate.unmapped) {
    toast('该候选含无法映射到统一命题集合的端点，请先完成该命题的裁定', 'err')
    return
  }
  decisions[item.id] = {
    itemId: item.id,
    action: 'ADOPT_VERSION',
    sourceVersionId: candidate.versionId,
    sourceObjectId: candidate.relationId
  }
}

function confirmAgreement(item: RelationAdjudicationItem): void {
  const candidate = item.candidates.find((c) => !c.unmapped) ?? item.candidates[0]
  if (!candidate) {
    toast('该项没有可确认的候选关系', 'err')
    return
  }
  decisions[item.id] = {
    itemId: item.id,
    action: 'CONFIRM_AGREEMENT',
    sourceVersionId: candidate.versionId,
    sourceObjectId: candidate.relationId
  }
}

function markDelete(item: RelationAdjudicationItem): void {
  decisions[item.id] = { itemId: item.id, action: 'DELETE' }
}

/** RETURN / ESCALATE_EXPERT：不消费裁定项，理由必填（与契约 J07/J08 一致）。 */
function defer(item: RelationAdjudicationItem, action: 'RETURN' | 'ESCALATE_EXPERT'): void {
  if (!(reasons[item.id] ?? '').trim()) {
    toast(action === 'RETURN' ? '退回必须填写补正理由' : '提交专家必须写明疑难问题', 'err')
    return
  }
  decisions[item.id] = { itemId: item.id, action }
}

function clearDecision(item: RelationAdjudicationItem): void {
  delete decisions[item.id]
}

// ── 自定义关系弹窗 ────────────────────────────────────────────────────

/** 候选命题：统一命题集合上的命题（不是源版本命题）——裁定结果建立在它之上。 */
const unifiedRows = computed<CustomRow[]>(() =>
  unifiedProps.value
    .filter((prop) => prop.proposition)
    .sort((a, b) => parseDisplayNo(a.displayNo) - parseDisplayNo(b.displayNo))
    .map((prop) => ({
      info: {
        targetKind: 'ELEMENT' as const,
        targetId: prop.id,
        code: prop.labelCode,
        displayNo: prop.displayNo,
        proposition: true
      },
      label: prop.displayNo ?? '—',
      text: prop.text,
      labelCode: prop.labelCode
    }))
)

/**
 * 候选关系：**本次裁定已保留**的内层关系（「修改嵌套」就靠它）。
 * 只列已裁项选中的候选——引用它们只是复用裁定者刚做的决定，不会替他把别的关系也采下来。
 */
const retainedRelationRows = computed<CustomRow[]>(() => {
  const rows: CustomRow[] = []
  items.value.forEach((item) => {
    const decision = decisions[item.id]
    if (!decision) return
    if (decision.action !== 'ADOPT_VERSION' && decision.action !== 'CONFIRM_AGREEMENT') return
    const candidate = item.candidates.find((c) => c.relationId === decision.sourceObjectId)
    if (!candidate) return
    rows.push({
      info: {
        targetKind: 'RELATION',
        targetId: candidate.relationId,
        code: candidate.relationCode,
        displayNo: null,
        proposition: true
      },
      label: `${candidate.versionLabel} ${candidate.expression}`,
      text: REL_META[candidate.relationCode].name,
      labelCode: candidate.relationCode
    })
  })
  return rows
})

function openCustom(item: RelationAdjudicationItem): void {
  custom.open = true
  custom.itemId = item.id
  custom.code = null
  custom.picked = []
  custom.error = ''
}

function pickCustomCode(code: RelationType): void {
  custom.code = code
  custom.picked = []
  custom.error = ''
}

const customDirecting = computed(() => (custom.code ? REL_META[custom.code].directed : false))
const customSlots = computed(() => {
  const meta = custom.code ? REL_META[custom.code] : null
  return meta && meta.directed ? [meta.sourceLabel, meta.targetLabel] : []
})

function currentCustomRole(): ParticipantRole {
  if (!custom.code || !REL_META[custom.code].directed) return 'MEMBER'
  return custom.picked.length === 0 ? 'SOURCE' : 'TARGET'
}

function customRowCheck(row: CustomRow): CandidateCheck {
  if (!custom.code) return { ok: false, reason: '请先选择关系类型' }
  return checkParticipant(custom.code, currentCustomRole(), row.info)
}

function toggleCustomRow(row: CustomRow): void {
  if (!custom.code) {
    custom.error = '请先选择关系类型'
    return
  }
  const index = custom.picked.findIndex(
    (c) => c.targetKind === row.info.targetKind && c.targetId === row.info.targetId
  )
  if (index >= 0) {
    custom.picked.splice(index, 1)
    return
  }
  const check = customRowCheck(row)
  if (!check.ok) {
    custom.error = check.reason
    return
  }
  if (REL_META[custom.code].directed && custom.picked.length >= 2) {
    custom.error = '有向关系只有「来源」与「目标」两个参与项，请先移除一个'
    return
  }
  custom.error = ''
  custom.picked.push(row.info)
}

function submitCustom(): void {
  const code = custom.code
  if (!code) {
    custom.error = '请先选择关系类型'
    return
  }
  const meta = REL_META[code]
  if (meta.directed && custom.picked.length !== 2) {
    custom.error = `需要「${meta.sourceLabel}」与「${meta.targetLabel}」各一项`
    return
  }
  if (!meta.directed && custom.picked.length < 2) {
    custom.error = `${code} 至少需要 2 个成员`
    return
  }
  const participants: ParticipantInput[] = orderedInputs(code, custom.picked)
  decisions[custom.itemId] = {
    itemId: custom.itemId,
    action: 'CUSTOM',
    customRelation: { relationCode: code, participants }
  }
  custom.open = false
  toast(`已加入自定义关系 ${code}（提交前仍可改）`)
}

// ── 生成关系裁定结果 ──────────────────────────────────────────────────

function payloadOf(): RelationDecisionInput[] {
  return Object.values(decisions)
    .filter((decision): decision is RelationDecisionInput => !!decision)
    .map((decision) => ({
      ...decision,
      // 理由在出参时才拼：决定是点按钮落的，理由可能之后才写完
      reason: (reasons[decision.itemId] ?? '').trim() || undefined
    }))
}

async function generate(): Promise<void> {
  if (items.value.length === 0) return
  generating.value = true
  try {
    // 不传 unifiedSnapshotId：由后端取会话声明的统一命题集合，避免与 D 的 J05 抢事实来源
    const generated = await resolveRelationResult(sessionId.value, { decisions: payloadOf() })
    result.value = generated
    toast(
      `已生成：采纳 ${generated.decidedCount}/${items.value.length} 项，状态 ${generated.status}`,
      generated.status === 'INVALID' ? 'err' : 'ok'
    )
  } catch (error) {
    toast(errorMessageOf(error), 'err')
  } finally {
    generating.value = false
  }
}

const resultMeta = computed(() => {
  const status = result.value?.status
  if (status === 'COMPLETE') {
    return { badge: 'success', label: 'COMPLETE · 可进入最终审查' }
  }
  if (status === 'INCOMPLETE') {
    return { badge: 'warn', label: 'INCOMPLETE · 尚有未决或需重裁项' }
  }
  if (status === 'INVALID') {
    return { badge: 'danger', label: 'INVALID · 合并结果违反关系规则' }
  }
  return { badge: 'default', label: '尚未生成' }
})

const errorIssues = computed(
  () => result.value?.issues.filter((issue) => issue.level === 'ERROR') ?? []
)

const warnings = computed(
  () =>
    result.value?.issues.filter(
      (issue) => issue.level !== 'ERROR' && issue.code !== 'GRAPH_ISOLATED_NODE'
    ) ?? []
)

function itemNumbers(ids: string[] | undefined): string {
  return (ids ?? []).map(indexOfItem).join('、')
}

function fitGraph(): void {
  graphRef.value?.fit()
}
</script>

<template>
  <div class="wb">
    <!-- ── 左：关系裁定项 ───────────────────────────────────────── -->
    <section class="wb-col wb-left">
      <div class="wb-col-head">
        <h3>关系差异项</h3>
        <span class="t-xs muted">{{ sessionId }}</span>
        <span style="flex: 1"></span>
        <span class="t-xs muted">{{ decidedCount }}/{{ items.length }} 已裁</span>
      </div>
      <div class="wb-col-body">
        <div class="op-sec">
          <div class="progress"><i :style="{ width: `${progressPercent}%` }" /></div>
          <p class="t-xs muted" style="margin-top: 8px">
            分歧 {{ conflictCount }} 项 · 一致 {{ agreementCount }} 项。
            <b>一致项也须由裁定者确认</b>，不确认就进不了最终审查。
          </p>
          <div class="seg" style="margin-top: 10px">
            <button
              v-for="scope in SCOPES"
              :key="scope.value"
              :class="{ 'is-active': scopeFilter === scope.value }"
              @click="scopeFilter = scope.value"
            >
              {{ scope.label }}
            </button>
          </div>
          <div class="type-pick">
            <button :class="{ 'is-on': typeFilter === 'ALL' }" @click="typeFilter = 'ALL'">
              全部 <span class="cp-code">{{ items.length }}</span>
            </button>
            <button
              v-for="type in CONFLICT_TYPES"
              :key="type"
              :class="{ 'is-on': typeFilter === type }"
              @click="typeFilter = type"
            >
              {{ CONFLICT_META[type].label }}
              <span class="cp-code">{{ countOfType(type) }}</span>
            </button>
          </div>
        </div>

        <div class="adj-list">
          <div
            v-for="item in visibleItems"
            :key="item.id"
            class="adj-item"
            :class="{ 'is-active': item.id === activeItemId }"
            @click="activeItemId = item.id"
          >
            <div class="adj-top">
              <span class="adj-no">{{ indexOfItem(item.id) }}</span>
              <span class="badge" :class="CONFLICT_META[item.conflictType].badge">
                {{ item.dimension }}
              </span>
              <span v-if="item.nestedFirst" class="badge warn">先裁内层</span>
              <span style="flex: 1"></span>
              <span class="adj-state" :class="{ 'is-done': isDecided(item) }">
                {{ stateLabel(item) }}
              </span>
            </div>
            <div class="adj-expr">{{ summaryOf(item) }}</div>
          </div>

          <p v-if="!loading && visibleItems.length === 0" class="t-xs muted">
            当前筛选条件下没有裁定项。
          </p>
          <div v-if="loading" class="empty">
            <div class="empty-ico"><Icon name="refresh" /></div>
            <h4>正在读取关系裁定项…</h4>
          </div>
          <div v-else-if="loadError" class="empty">
            <div class="empty-ico"><Icon name="lock" /></div>
            <h4>无法开始关系裁定</h4>
            <p class="t-sm" style="margin-top: 6px">{{ gateHint }}</p>
            <p class="t-xs muted" style="margin-top: 6px">{{ loadError }}</p>
          </div>
        </div>
      </div>
    </section>

    <div class="splitter" />

    <!-- ── 中：当前项详情与裁定动作 ─────────────────────────────── -->
    <section class="wb-col wb-center">
      <div class="wb-col-head">
        <h3>裁定项详情</h3>
        <span class="t-xs muted">先裁内层，再裁外层</span>
        <span style="flex: 1"></span>
        <span v-if="activeItem" class="t-xs muted">
          第 {{ indexOfItem(activeItem.id) }} / {{ items.length }} 项
        </span>
      </div>
      <div v-if="activeItem" class="wb-col-body">
        <div class="op-sec">
          <div class="badge-row">
            <span class="badge" :class="CONFLICT_META[activeItem.conflictType].badge">
              {{ activeItem.conflictType }} · {{ activeItem.dimension }}
            </span>
            <span v-if="activeItem.nestedFirst" class="badge warn">
              <Icon name="alert" /> 含嵌套，先裁内层
            </span>
            <span class="badge" :class="isDecided(activeItem) ? 'success' : 'default'">
              {{ stateLabel(activeItem) }}
            </span>
          </div>
          <p class="t-sm reason-text">{{ activeItem.reason }}</p>
        </div>

        <!-- 两侧候选并排 -->
        <div class="op-sec">
          <h4>两侧候选</h4>
          <div class="cand-grid">
            <div
              v-for="candidate in activeItem.candidates"
              :key="candidate.relationId"
              class="diff-col"
              :class="{ 'is-unmapped': candidate.unmapped }"
            >
              <div class="diff-col-head">
                <span class="who">
                  <span :style="{ color: REL_META[candidate.relationCode].color }">
                    {{ REL_META[candidate.relationCode].glyph }}
                  </span>
                  {{ candidate.versionLabel }} · {{ REL_META[candidate.relationCode].name }}
                </span>
                <span style="flex: 1"></span>
                <button
                  class="btn btn-primary btn-sm"
                  :disabled="candidate.unmapped"
                  :title="candidate.unmapped ? '含无法映射的端点，暂不可采用' : ''"
                  @click="adopt(activeItem, candidate)"
                >
                  <Icon name="check" />
                  采用 {{ candidate.versionLabel }}
                </button>
              </div>
              <div class="diff-body">
                <div class="formula">{{ candidate.expression }}</div>
                <div
                  v-for="participant in candidate.participants"
                  :key="participant.id"
                  class="diff-row"
                  :class="{ conflict: candidate.unmapped }"
                >
                  <span class="dno">{{ participant.displayNo ?? participant.ordinal ?? '—' }}</span>
                  <span class="dtxt">
                    <b>{{ ROLE_LABELS[participant.role] }}</b>
                    {{ participant.text ?? participant.targetId }}
                  </span>
                </div>
                <p v-if="candidate.unmapped" class="t-xs unmapped-hint">
                  <Icon name="alert" />
                  含无法映射到统一命题集合的端点 ——
                  <b>请先完成该命题的裁定</b>，本候选暂不可采用。
                </p>
              </div>
            </div>
          </div>
          <p v-if="activeItem.candidates.length === 0" class="t-xs muted">
            该项没有候选关系（一侧新增/遗漏时只有一条）。
          </p>
        </div>

        <!-- 理由 -->
        <div class="op-sec">
          <h4>裁定理由</h4>
          <div class="field">
            <textarea
              v-model="reasons[activeItem.id]"
              class="textarea"
              placeholder="例如：P3 才是被支持项 / 裁判理由原文第 3 段支持该方向"
            />
            <span class="hint">
              采用某版建议写明理由；「提交专家」与「退回重标」必填。
              理由随决定留档（裁定记录归成员D）。
            </span>
          </div>
        </div>

        <!-- 动作 -->
        <div class="op-sec" style="border-bottom: 0">
          <h4>裁定动作</h4>
          <div class="action-row">
            <button
              v-if="activeItem.conflictType === 'AGREEMENT'"
              class="btn btn-primary btn-sm"
              @click="confirmAgreement(activeItem)"
            >
              <Icon name="checkc" />
              确认一致
            </button>
            <button class="btn btn-secondary btn-sm" @click="openCustom(activeItem)">
              <Icon name="edit" />
              自定义关系
            </button>
            <button class="btn btn-danger btn-sm" @click="markDelete(activeItem)">
              <Icon name="trash" />
              删除关系
            </button>
            <button class="btn btn-warm btn-sm" @click="defer(activeItem, 'ESCALATE_EXPERT')">
              <Icon name="scale" />
              提交专家
            </button>
            <button class="btn btn-secondary btn-sm" @click="defer(activeItem, 'RETURN')">
              <Icon name="undo" />
              退回重标
            </button>
            <button
              v-if="decisions[activeItem.id]"
              class="btn btn-ghost btn-sm"
              @click="clearDecision(activeItem)"
            >
              <Icon name="x" />
              清除本项决定
            </button>
          </div>
          <div v-if="decisions[activeItem.id]" class="note info" style="margin-top: 10px">
            <Icon name="info" />
            <span class="note-body">
              本项当前动作：<b>{{ ACTION_LABELS[decisions[activeItem.id]!.action] }}</b>。
              每次操作只改本页草稿，点右栏「生成关系裁定结果」才提交试算；
              D 的 J07/J08 就绪后才真正落库。
            </span>
          </div>
        </div>
      </div>
      <div v-else class="wb-col-body">
        <div class="empty">
          <div class="empty-ico"><Icon name="merge" /></div>
          <h4>没有可裁定的项</h4>
          <p class="t-sm">左栏选择一项，或换一个会话（?session=ses-1 / ses-3）。</p>
        </div>
      </div>
    </section>

    <div class="splitter" />

    <!-- ── 右：关系裁定结果与正式论证图 ─────────────────────────── -->
    <section class="wb-col wb-right">
      <div class="graph-toolbar">
        <h3>正式论证图</h3>
        <span class="badge" :class="resultMeta.badge">{{ resultMeta.label }}</span>
        <span style="flex: 1"></span>
        <span v-if="result" class="t-xs muted">
          布局 v{{ result.graph.layoutVersion }} · 独立图示 {{ result.graph.componentCount }} 个 ·
          孤立命题 {{ result.graph.isolatedNodeIds.length }} 个
        </span>
        <button class="btn btn-icon btn-ghost" title="适应窗口" @click="fitGraph">
          <Icon name="fit" />
        </button>
        <button
          class="btn btn-primary btn-sm"
          :disabled="generating || items.length === 0"
          @click="generate"
        >
          <Icon name="play" />
          生成关系裁定结果
        </button>
      </div>

      <div v-if="result" class="result-block">
        <div class="stat-row">
          <span><b>{{ result.decidedCount }}</b> <span class="t-xs muted">/ {{ items.length }} 已裁</span></span>
          <span><b :style="{ color: 'var(--danger)' }">{{ result.conflictCount }}</b> <span class="t-xs muted">分歧</span></span>
          <span><b :style="{ color: 'var(--success)' }">{{ result.agreementCount }}</b> <span class="t-xs muted">一致</span></span>
          <span><b>{{ result.relations.length }}</b> <span class="t-xs muted">合并关系</span></span>
        </div>

        <div v-if="result.pendingItemIds.length" class="note warn">
          <Icon name="info" />
          <span class="note-body">
            未决 {{ result.pendingItemIds.length }} 项（第 {{ itemNumbers(result.pendingItemIds) }} 项）：
            未给决定，或决定是「提交专家 / 退回重标」。<b>不消费裁定项</b>，仍留在待办里。
          </span>
        </div>

        <div v-if="result.reAdjudicationItemIds.length" class="note is-err">
          <Icon name="alert" />
          <span class="note-body">
            需重新裁定 {{ result.reAdjudicationItemIds.length }} 项（第
            {{ itemNumbers(result.reAdjudicationItemIds) }} 项）：
            其选择引用了已被删除的内层关系，或自定义关系未通过校验。删除不级联，由裁定者显式处理。
          </span>
        </div>

        <div v-if="errorIssues.length" class="note is-err">
          <Icon name="alert" />
          <span class="note-body">
            <b>{{ errorIssues[0].code }}</b>：{{ errorIssues[0].message }}
            <template v-if="errorIssues.length > 1">（其余 {{ errorIssues.length - 1 }} 条同类问题已省略）</template>
          </span>
        </div>

        <div v-if="warnings.length" class="note warn">
          <Icon name="info" />
          <span class="note-body">{{ warnings[0].message }}</span>
        </div>

        <div>
          <div class="section-label" style="margin-bottom: 6px">
            合并后的关系（{{ result.relations.length }}）
          </div>
          <div class="merged-list">
            <div v-for="relation in result.relations" :key="relation.id" class="merged-item">
              <span :style="{ color: REL_META[relation.relationCode].color }">
                {{ REL_META[relation.relationCode].glyph }}
              </span>
              <span class="formula">{{ relation.expression }}</span>
            </div>
            <p v-if="result.relations.length === 0" class="t-xs muted">
              没有任何关系被采用。
            </p>
          </div>
        </div>

        <p class="t-xs muted">
          结果建立在统一命题集合 {{ result.unifiedSnapshotId }} 之上
          （端点先映射，防悬空边）；一次裁定结果不落库，D 的 J07/J08 接管后语义不变。
        </p>
      </div>

      <div v-else class="result-block is-empty">
        <div class="empty">
          <div class="empty-ico"><Icon name="layers" /></div>
          <h4>尚未生成关系裁定结果</h4>
          <p class="t-sm">
            在左栏逐项裁定后点上方「生成关系裁定结果」，这里会显示合并后的关系、
            校验问题与正式论证图。
          </p>
          <p class="t-xs muted" style="margin-top: 6px">
            未裁项会落进 INCOMPLETE，不会凭空补全。
          </p>
        </div>
      </div>

      <ArgumentGraph
        v-if="result"
        ref="graphRef"
        style="flex: 1 1 auto; min-height: 0"
        :nodes="result.graph.nodes"
        :edges="result.graph.edges"
        :positions="result.graph.positions"
      />
      <div v-if="result" class="graph-legend">
        <span v-for="code in CONFLICT_TYPES" :key="code" class="lg">
          <span class="badge" :class="CONFLICT_META[code].badge">{{ CONFLICT_META[code].label }}</span>
          {{ countOfType(code) }}
        </span>
        <span class="lg muted">正式论证图：ownerType=FINAL_VERSION，端点全部落在统一命题集合上</span>
      </div>
    </section>
  </div>

  <!-- ── 自定义关系弹窗 ─────────────────────────────────────────── -->
  <BaseModal
    v-model:open="custom.open"
    title="自定义关系"
    :sub="`第 ${indexOfItem(custom.itemId)} 项 · 端点在统一命题集合 ${UNIFIED_SNAPSHOT_ID} 上，不是源版本命题`"
    wide
  >
    <div class="op-sec" style="padding: 0 0 14px">
      <h4>一、关系类型</h4>
      <div class="rel-pick">
        <button
          v-for="code in REL_ANNOTATION_ORDER"
          :key="code"
          :class="{ 'is-on': custom.code === code }"
          :style="{ color: REL_META[code].color }"
          @click="pickCustomCode(code)"
        >
          <span>{{ REL_META[code].glyph }}</span>
          <span class="rp-name">{{ REL_META[code].name }}</span>
          <span class="cp-code">{{ code }}</span>
        </button>
      </div>
    </div>

    <div class="op-sec" style="padding: 0 0 14px">
      <h4>二、参与项</h4>
      <p v-if="!custom.code" class="t-sm muted">先在上方选择关系类型。</p>
      <template v-else>
        <p class="t-xs muted" style="margin-bottom: 8px">
          {{ REL_META[custom.code].participants }} · {{ REL_META[custom.code].directionRule }}
        </p>
        <div v-if="customDirecting" class="chip-grid">
          <button
            v-for="(slot, index) in customSlots"
            :key="index"
            class="chip-pick"
            :class="{ 'is-on': !!custom.picked[index] }"
            :style="custom.picked[index] ? { color: REL_META[custom.code].color } : undefined"
            @click="custom.picked[index] && custom.picked.splice(index, 1)"
          >
            <span class="cp-code">{{ index === 0 ? '来源' : '目标' }}</span>
            <span>
              {{ custom.picked[index]
                ? (custom.picked[index].displayNo ?? custom.picked[index].targetId)
                : slot }}
            </span>
            <Icon v-if="custom.picked[index]" name="x" />
          </button>
        </div>
        <div v-else class="chip-grid">
          <button
            v-for="(info, index) in custom.picked"
            :key="`${info.targetKind}-${info.targetId}`"
            class="chip-pick is-on"
            :style="{ color: REL_META[custom.code].color }"
            @click="custom.picked.splice(index, 1)"
          >
            <span class="cp-code">{{ info.targetKind === 'ELEMENT' ? '命题' : '关系' }}</span>
            <span>{{ info.displayNo ?? info.targetId }}</span>
            <Icon name="x" />
          </button>
          <span v-if="custom.picked.length === 0" class="t-xs muted">
            点击下方候选加入成员（至少 2 个）
          </span>
        </div>
      </template>
    </div>

    <div class="op-sec" style="padding: 0 0 14px">
      <h4>三、候选命题（统一命题集合）</h4>
      <div class="el-list" style="padding: 0; max-height: 220px; overflow-y: auto">
        <div
          v-for="row in unifiedRows"
          :key="row.info.targetId"
          class="el-item"
          :class="{ 'is-active': custom.picked.some((c) => c.targetId === row.info.targetId) }"
          :style="{ opacity: customRowCheck(row).ok || custom.picked.some((c) => c.targetId === row.info.targetId) ? 1 : 0.55 }"
          :title="customRowCheck(row).reason"
          @click="toggleCustomRow(row)"
        >
          <div class="el-top">
            <span class="el-no">{{ row.label }}</span>
            <Chip v-if="row.labelCode" :code="row.labelCode" long />
          </div>
          <div class="el-text">{{ row.text }}</div>
        </div>
      </div>
    </div>

    <div class="op-sec" style="padding: 0">
      <h4>四、候选关系（本次裁定已保留的内层关系）</h4>
      <p class="t-xs muted" style="margin-bottom: 8px">
        嵌套按从内到外构造：先裁内层关系，这里才能引用它作为外层端点（「修改嵌套」用这一栏）。
      </p>
      <div class="rel-list" style="padding: 0">
        <div
          v-for="row in retainedRelationRows"
          :key="row.info.targetId"
          class="rel-item"
          :class="{ 'is-active': custom.picked.some((c) => c.targetId === row.info.targetId) }"
          :style="{ opacity: customRowCheck(row).ok || custom.picked.some((c) => c.targetId === row.info.targetId) ? 1 : 0.55 }"
          :title="customRowCheck(row).reason"
          @click="toggleCustomRow(row)"
        >
          <span :style="{ color: REL_META[row.info.code as RelationType].color }">
            {{ REL_META[row.info.code as RelationType].glyph }}
          </span>
          <span class="formula">{{ row.label }}</span>
        </div>
        <p v-if="retainedRelationRows.length === 0" class="t-xs muted">
          还没有已裁定的内层关系可引用。
        </p>
      </div>
    </div>

    <p v-if="custom.error" class="t-xs" style="margin-top: 10px; color: var(--danger)">
      {{ custom.error }}
    </p>

    <template #footer>
      <button class="btn btn-secondary" @click="custom.open = false">取消</button>
      <button class="btn btn-primary" @click="submitCustom">
        <Icon name="check" />
        加入本项自定义关系
      </button>
    </template>
  </BaseModal>
</template>

<style scoped>
/* 本页占满内容区：抵消 .content 的内边距，使 .wb 的 100vh 高度成立（同标注工作台） */
.wb {
  margin: calc(var(--sp-6) * -1) calc(var(--sp-6) * -1) calc(var(--sp-9) * -1);
}
@media (max-width: 900px) {
  .wb {
    margin: calc(var(--sp-4) * -1) calc(var(--sp-4) * -1) calc(var(--sp-8) * -1);
  }
}

/* 裁定项列表。形态与 .el-item / .rel-item 都不同（三行、非等宽、带徽章），故用局部类名 */
.adj-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px;
}
.adj-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 9px 11px;
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  background: var(--surface);
  cursor: pointer;
  transition: border-color 0.12s ease, box-shadow 0.12s ease;
}
.adj-item:hover {
  border-color: var(--accent-line);
}
.adj-item.is-active {
  border-color: var(--accent);
  box-shadow: 0 0 0 2px var(--accent-soft);
}
.adj-top {
  display: flex;
  align-items: center;
  gap: 6px;
}
.adj-no {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--faint);
  min-width: 14px;
}
.adj-expr {
  font-family: var(--font-mono);
  font-size: 11.5px;
  color: var(--fg-2);
  line-height: 1.5;
  word-break: break-all;
}
.adj-state {
  font-size: 11px;
  color: var(--faint);
  white-space: nowrap;
}
.adj-state.is-done {
  color: var(--success);
  font-weight: 600;
}

/* 差异类型筛选：.rel-pick 是两列，这里类型多一个，用三列 */
.type-pick {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
  margin-top: 8px;
}
.type-pick button {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  padding: 6px 8px;
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  background: var(--surface);
  font-size: 11.5px;
  color: var(--fg-2);
  transition: all 0.12s ease;
}
.type-pick button:hover {
  border-color: var(--accent-line);
}
.type-pick button.is-on {
  border-color: var(--accent);
  background: var(--accent-soft);
  color: var(--accent);
  font-weight: 600;
}

.badge-row {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.reason-text {
  margin-top: 10px;
  color: var(--fg-2);
  line-height: 1.7;
}

/* 两侧候选：一行放得下就并排，放不下自动换行 */
.cand-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  gap: var(--sp-4);
}
.diff-col.is-unmapped {
  border-color: color-mix(in oklch, var(--danger) 32%, transparent);
}
.formula {
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 600;
  color: var(--fg);
  word-break: break-all;
}
.unmapped-hint {
  color: var(--danger);
  line-height: 1.6;
  display: flex;
  gap: 6px;
}
.unmapped-hint svg {
  width: 14px;
  height: 14px;
  flex: 0 0 14px;
  margin-top: 2px;
}

.action-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

/* 结果面板：占右栏上部，超高自己滚，不压掉图示 */
.result-block {
  flex: 0 0 auto;
  max-height: 40vh;
  overflow-y: auto;
  padding: 12px 14px;
  border-bottom: 1px solid var(--border);
  background: var(--surface);
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.result-block.is-empty {
  max-height: none;
}
.stat-row {
  display: flex;
  gap: 18px;
  align-items: baseline;
  flex-wrap: wrap;
}
.stat-row b {
  font-family: var(--font-mono);
  font-size: 19px;
}
.merged-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.merged-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 9px;
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  background: var(--surface);
  font-size: 11.5px;
}
.merged-item .formula {
  font-size: 11.5px;
  font-weight: 500;
}

/* .note 只定义了 info/warn/accent，错误态补一个 */
.note.is-err {
  background: var(--danger-soft);
  border-color: color-mix(in oklch, var(--danger) 30%, transparent);
  color: var(--danger);
}
.note.is-err .note-body {
  color: var(--fg-2);
}
</style>
