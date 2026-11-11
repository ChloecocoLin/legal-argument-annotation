<script setup lang="ts">
// 标注工作台（成员C）。第三个核心业务链的前半段：
// 在同一工作台加载当前标注员的命题 → 按 S → J → M → A → I 建立关系 → 嵌套检查 → 生成论证图示。
// 契约：R01–R04（关系实例）、GR01–GR03（论证图）；命题数据本轮走【临时】模拟命题接口。
// 三栏样式全部复用 app.css §10/§11（.wb / .doc-source / .rel-pick / .rel-list / .graph-stage …）。
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import ArgumentGraph from './ArgumentGraph.vue'
import GraphExportButtons from './GraphExportButtons.vue'
import BaseModal from '@/shared/BaseModal.vue'
import Chip from '@/shared/Chip.vue'
import ForbiddenNotice from '@/shared/ForbiddenNotice.vue'
import Icon from '@/shared/Icon.vue'
import { toast } from '@/shared/toast'
import { errorMessageOf, isForbidden } from '@/shared/apiError'
import { componentsOf } from '@/shared/graphGeometry'
import { typeGroup } from '@/shared/elementMeta'
import { REL_ANNOTATION_ORDER, REL_META } from '@/shared/relationMeta'
import {
  checkParticipant,
  orderedInputs,
  parseDisplayNo,
  representativeOf,
  type CandidateCheck,
  type CandidateInfo
} from '@/shared/relationRules'
import {
  createRelation,
  deleteRelation,
  listMockPropositions,
  listRelations,
  replaceParticipants
} from '@/api/relation'
import {
  getGraph,
  resetGraphLayout,
  saveGraphLayout,
  validateGraph
} from '@/api/graph'
import type { RelationType } from '@/types/common'
import type {
  MockProposition,
  Relation,
  RelationIssue,
  RelationParticipant
} from '@/types/relation'
import type {
  GraphNode,
  GraphNodePosition,
  GraphValidateResult,
  GraphView
} from '@/types/graph'

/** 【临时】模拟标注草稿（data.sql 种子）。正式接入后由「我的标注任务」带 ?draft=xxx 进入。 */
const DEMO_DRAFT_ID = 'md-1'
/** 标注阶段关系归属草稿；裁定阶段为 ARBITER_CUSTOM，定稿为 FINAL_VERSION。 */
const OWNER_TYPE = 'DRAFT'
/**
 * 保存布局时的画布留白。画布尺寸不再写死 1200×800：单命题的图也只存实际大小，
 * 否则取景时按固定画布缩放，那个 P1 会被缩到角落里。
 */
const CANVAS_PAD = 80
const TAB_LABELS = ['一', '二', '三', '四', '五', '六', '七', '八']

// 候选行：命题或已有关系，既要能画界面，也要能喂给关系矩阵判断
interface CandidateRow {
  info: CandidateInfo
  label: string
  text: string
  labelCode: string | null
}

const route = useRoute()
const draftId = computed(() => (route.query.draft as string) || DEMO_DRAFT_ID)

const loading = ref(false)
const saving = ref(false)
/** 403 时保留后端原文；有值时整页换成「无权限」空状态，而不是空三栏。 */
const forbidden = ref<string | null>(null)
const propositions = ref<MockProposition[]>([])
const relations = ref<Relation[]>([])
const graph = ref<GraphView | null>(null)
const validation = ref<GraphValidateResult | null>(null)
const graphRef = ref<InstanceType<typeof ArgumentGraph> | null>(null)

const activeNodeId = ref<string | null>(null)
const activePropositionId = ref<string | null>(null)
const tabIndex = ref(0)
/**
 * 尚未保存的坐标（拖动、重新布局产生）。
 *
 * 后端保存的 layout 只在 GR03 之后才更新；如果直接拿 graph.positions 当唯一来源，
 * 切一次「图示一/图示二」就会重建、把刚拖好的位置又按旧布局摆回去。
 */
const draftPositions = ref<GraphNodePosition[] | null>(null)
const resetLayoutOpen = ref(false)

const editor = reactive({
  relationId: null as string | null,
  code: null as RelationType | null,
  picked: [] as CandidateInfo[],
  error: ''
})

const deleteState = reactive({
  open: false,
  relationId: '',
  formula: '',
  reason: ''
})

// ── 数据加载 ──────────────────────────────────────────────────────────

onMounted(loadAll)

async function loadAll(): Promise<void> {
  loading.value = true
  forbidden.value = null
  try {
    const [elementList, relationPage, graphView] = await Promise.all([
      listMockPropositions(draftId.value),
      listRelations(draftId.value, { size: 100 }),
      getGraph(OWNER_TYPE, draftId.value)
    ])
    propositions.value = elementList
    relations.value = relationPage.items
    graph.value = graphView
    await runValidation()
  } catch (error) {
    // 拦截器已按后端原文弹过提示；这里是页内兜底：403 显示权限说明，其它错误也保留原话。
    if (isForbidden(error)) forbidden.value = errorMessageOf(error)
    else toast(errorMessageOf(error), 'err')
  } finally {
    loading.value = false
  }
}

/** 关系写操作后统一重取关系与图示（坐标由 GR01 或自动布局给出）。 */
async function refresh(): Promise<void> {
  const [relationPage, graphView] = await Promise.all([
    listRelations(draftId.value, { size: 100 }),
    getGraph(OWNER_TYPE, draftId.value)
  ])
  relations.value = relationPage.items
  graph.value = graphView
  await runValidation()
}

/**
 * GR02：把当前图示的全集提交校验，问题用于右栏「嵌套与完整性检查」。
 * 这是"图示审查"的概览口径；正式审查页在后续切片里做逐项定位。
 */
async function runValidation(): Promise<void> {
  const view = graph.value
  if (!view) return
  try {
    validation.value = await validateGraph(draftId.value, {
      elementIds: view.nodes.filter((n) => n.kind === 'ELEMENT').map((n) => n.id),
      relationIds: view.nodes.filter((n) => n.kind === 'RELATION').map((n) => n.id)
    })
  } catch (error) {
    validation.value = null
    toast(errorMessageOf(error), 'err')
  }
}

// ── 校验结论（放在图上方，先于细节看）──────────────────────────────────
// 之前只有第六节里一行 `{{ validation?.status }}`，校验完仍是「待校验」的错觉；
// 这里把「这稿到底过没过、差几条」做成图上方的一条常驻横幅。
const issues = computed(() => validation.value?.issues ?? [])
const errorIssues = computed(() => issues.value.filter((i) => i.level === 'ERROR'))
const warnIssues = computed(() => issues.value.filter((i) => i.level === 'WARNING'))
const passed = computed(
  () => validation.value?.status === 'VALID' && errorIssues.value.length === 0
)
const validationTone = computed<'idle' | 'ok' | 'bad'>(() => {
  if (!validation.value) return 'idle'
  return passed.value ? 'ok' : 'bad'
})
const validationTitle = computed(() => {
  if (!validation.value) return '尚未校验'
  if (passed.value) return '校验通过，图可以保存和提交'
  const counts: string[] = []
  if (errorIssues.value.length) counts.push(`${errorIssues.value.length} 条错误`)
  if (warnIssues.value.length) counts.push(`${warnIssues.value.length} 条提醒`)
  return `还有 ${counts.length ? counts.join('、') : '结构问题'}，图还不能通过`
})
const validationDetail = computed(() => {
  const view = validation.value
  if (!view) return '点「重新校验」提交当前全部命题与关系。'
  const stats = `${view.componentCount} 个独立论证图 · ${view.isolatedNodeIds.length} 个孤立命题`
  if (passed.value) return stats
  const first = errorIssues.value[0] ?? warnIssues.value[0]
  return first ? `${first.message}（${stats}）` : stats
})

/** 点问题行时尽量在图上选中对应关系节点，让「哪一条」看得见。 */
function focusIssue(issue: RelationIssue): void {
  if (issue.relationId) activeNodeId.value = issue.relationId
}

// ── 左栏：裁判理由原文（模拟命题）──────────────────────────────────────

function selectProposition(prop: MockProposition): void {
  activePropositionId.value = prop.id
  // 同步高亮右栏图示里的对应节点（非命题不进图，忽略）
  if (prop.proposition && graph.value?.nodes.some((n) => n.id === prop.id)) {
    activeNodeId.value = prop.id
  }
}

function onGraphNodeClick(node: GraphNode): void {
  if (node.kind === 'ELEMENT') {
    activePropositionId.value = node.id
    return
  }
  activePropositionId.value = null
}

// ── 中栏：关系标注 ────────────────────────────────────────────────────

const orderedElements = computed(() =>
  [...propositions.value].sort((a, b) => {
    if (a.proposition !== b.proposition) return a.proposition ? -1 : 1
    return parseDisplayNo(a.displayNo) - parseDisplayNo(b.displayNo)
  })
)

const elementRows = computed<CandidateRow[]>(() =>
  orderedElements.value.map((prop) => ({
    info: {
      targetKind: 'ELEMENT',
      targetId: prop.id,
      code: prop.labelCode,
      displayNo: prop.displayNo,
      proposition: prop.proposition
    },
    label: prop.displayNo ?? '—',
    text: prop.text,
    labelCode: prop.labelCode
  }))
)

const relationRows = computed<CandidateRow[]>(() =>
  relations.value
    .filter((rel) => rel.id !== editor.relationId)
    .map((rel) => ({
      info: {
        targetKind: 'RELATION',
        targetId: rel.id,
        code: rel.relationCode,
        displayNo: null,
        proposition: true
      },
      label: rel.expression,
      text: rel.name ?? REL_META[rel.relationCode].name,
      labelCode: rel.relationCode
    }))
)

const relationsByCode = computed<Map<RelationType, Relation[]>>(() => {
  const grouped = new Map<RelationType, Relation[]>()
  REL_ANNOTATION_ORDER.forEach((code) => grouped.set(code, []))
  relations.value.forEach((rel) => {
    grouped.get(rel.relationCode)?.push(rel)
  })
  return grouped
})

const activeMeta = computed(() => (editor.code ? REL_META[editor.code] : null))
const directing = computed(() => activeMeta.value?.directed === true)
const slotLabels = computed(() =>
  activeMeta.value && directing.value
    ? [activeMeta.value.sourceLabel, activeMeta.value.targetLabel]
    : []
)
const activeRelation = computed(() =>
  relations.value.find((rel) => rel.id === editor.relationId) ?? null
)

/** 未标注的关系类型（"未完成关系提示"） */
const pendingHints = computed(() =>
  REL_ANNOTATION_ORDER.filter(
    (code) => (relationsByCode.value.get(code)?.length ?? 0) === 0
  ).map((code) => `${code} ${REL_META[code].name}尚未标注`)
)

const representative = computed(() =>
  editor.code === 'I' ? representativeOf(editor.picked) : null
)

function describe(info: CandidateInfo): string {
  if (info.targetKind === 'ELEMENT') return info.displayNo ?? info.targetId
  const rel = relations.value.find((item) => item.id === info.targetId)
  return rel ? rel.expression : info.targetId
}

function pickCode(code: RelationType): void {
  if (editor.code === code && !editor.relationId) {
    resetEditor()
    return
  }
  editor.relationId = null
  editor.code = code
  editor.picked = []
  editor.error = ''
}

function resetEditor(): void {
  editor.relationId = null
  editor.code = null
  editor.picked = []
  editor.error = ''
}

function editRelation(rel: Relation): void {
  editor.relationId = rel.id
  editor.code = rel.relationCode
  editor.error = ''
  editor.picked = [...rel.participants]
    .sort((a, b) => a.ordinal - b.ordinal)
    .map((participant) => candidateOfParticipant(participant))
  activeNodeId.value = rel.id
}

function candidateOfParticipant(participant: RelationParticipant): CandidateInfo {
  if (participant.targetKind === 'RELATION') {
    return {
      targetKind: 'RELATION',
      targetId: participant.targetId,
      code:
        relations.value.find((rel) => rel.id === participant.targetId)?.relationCode ??
        null,
      displayNo: null,
      proposition: true
    }
  }
  const prop = propositions.value.find((item) => item.id === participant.targetId)
  return {
    targetKind: 'ELEMENT',
    targetId: participant.targetId,
    code: participant.labelCode ?? prop?.labelCode ?? null,
    displayNo: participant.displayNo ?? prop?.displayNo ?? null,
    proposition: prop?.proposition ?? true
  }
}

function isPicked(info: CandidateInfo): boolean {
  return editor.picked.some(
    (item) => item.targetKind === info.targetKind && item.targetId === info.targetId
  )
}

function unpick(index: number): void {
  editor.picked.splice(index, 1)
}

/** 有向关系的槽位点击：已填则移除，未填则忽略（填坑统一走候选列表） */
function toggleSlot(index: number): void {
  if (editor.picked[index]) unpick(index)
}

/** 下一个待填槽位的角色，用于候选列表的合法性提示 */
function currentRole(): 'SOURCE' | 'TARGET' | 'MEMBER' {
  if (!directing.value) return 'MEMBER'
  return editor.picked.length === 0 ? 'SOURCE' : 'TARGET'
}

function rowCheck(row: CandidateRow): CandidateCheck {
  if (!editor.code) return { ok: false, reason: '请先选择关系类型' }
  if (editor.relationId && row.info.targetId === editor.relationId) {
    return { ok: false, reason: '关系不能指向自身' }
  }
  return checkParticipant(editor.code, currentRole(), row.info)
}

function toggleCandidate(row: CandidateRow): void {
  if (!editor.code) {
    toast('请先选择关系类型', 'err')
    return
  }
  const index = editor.picked.findIndex(
    (item) =>
      item.targetKind === row.info.targetKind && item.targetId === row.info.targetId
  )
  if (index >= 0) {
    editor.picked.splice(index, 1)
    return
  }
  const check = rowCheck(row)
  if (!check.ok) {
    editor.error = check.reason
    return
  }
  if (directing.value && editor.picked.length >= 2) {
    editor.error = '有向关系只有「来源」与「目标」两个参与项，请先移除一个'
    return
  }
  editor.error = ''
  editor.picked.push(row.info)
}

async function submit(): Promise<void> {
  const code = editor.code
  if (!code) {
    toast('请先选择关系类型', 'err')
    return
  }
  if (directing.value && editor.picked.length !== 2) {
    editor.error = `需要「${REL_META[code].sourceLabel}」与「${REL_META[code].targetLabel}」各一项`
    return
  }
  if (!directing.value && editor.picked.length < 2) {
    editor.error = `${code} 至少需要 2 个成员`
    return
  }

  const participants = orderedInputs(code, editor.picked)
  saving.value = true
  editor.error = ''
  try {
    if (editor.relationId) {
      await replaceParticipants(editor.relationId, { participants })
      toast(`${code} 关系已更新`)
    } else {
      await createRelation(draftId.value, { relationCode: code, participants })
      toast(`${code} 关系已建立`)
    }
    resetEditor()
    await refresh()
  } catch (error) {
    // 拦截器已弹通用提示；这里把后端校验文案留在操作区，便于对照参与项修改
    editor.error = errorMessageOf(error)
  } finally {
    saving.value = false
  }
}

function askDelete(rel: Relation): void {
  deleteState.open = true
  deleteState.relationId = rel.id
  deleteState.formula = rel.expression
  deleteState.reason = ''
}

async function confirmDelete(): Promise<void> {
  const reason = deleteState.reason.trim()
  if (!reason) {
    toast('请填写删除原因', 'err')
    return
  }
  try {
    await deleteRelation(deleteState.relationId, { reason })
    toast('关系已删除')
    deleteState.open = false
    if (editor.relationId === deleteState.relationId) resetEditor()
    await refresh()
  } catch (error) {
    // 被嵌套引用时会返回 NESTED_REFERENCE_EXISTS，保持弹窗让用户看到原因
    toast(errorMessageOf(error), 'err')
  }
}

// ── 右栏：论证图示 ────────────────────────────────────────────────────

const graphNodes = computed(() => graph.value?.nodes ?? [])
const graphEdges = computed(() => graph.value?.edges ?? [])

/** 连通分量识别 → 「图示一 / 图示二」体现多个独立论证图（单节点分量即孤立命题） */
const components = computed(() =>
  componentsOf(
    graphNodes.value.map((node) => ({
      id: node.id,
      label: node.displayNo ?? node.relationCode ?? node.id
    })),
    graphEdges.value.map((edge) => ({ a: edge.source, b: edge.target }))
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
  return ids ? graphNodes.value.filter((node) => ids.has(node.id)) : graphNodes.value
})

const visibleEdges = computed(() => {
  const ids = visibleNodeIds.value
  return ids
    ? graphEdges.value.filter((edge) => ids.has(edge.source) && ids.has(edge.target))
    : graphEdges.value
})

const legendItems = computed(() =>
  REL_ANNOTATION_ORDER.map((code) => ({ code, meta: REL_META[code] }))
)

function zoom(factor: number): void {
  graphRef.value?.zoomBy(factor)
}

function refit(): void {
  graphRef.value?.fit()
}

function resetView(): void {
  graphRef.value?.reset()
}

/** 传给 ArgumentGraph 的坐标：未保存的优先，其次是后端保存的布局。 */
const savedPositions = computed(() => draftPositions.value ?? graph.value?.positions ?? [])

function onPositionsChange(positions: GraphNodePosition[]): void {
  draftPositions.value = positions
}

/**
 * 重新布局。只改屏幕坐标，不改关系；要按「保存布局」才落到 GR03。
 * 如果当前停在某个子图，先切回「全部」，否则只会重排当前子图的节点。
 */
async function relayout(): Promise<void> {
  if (tabIndex.value !== 0) {
    tabIndex.value = 0
    await nextTick()
  }
  graphRef.value?.autoLayoutNow()
  draftPositions.value = graphRef.value?.currentPositions() ?? null
  toast('已按自动布局重排；点「保存布局」留存')
}

/** GR03：保存布局。切到「全部」再取坐标，避免只存下当前图示的子集。 */
async function saveLayout(): Promise<void> {
  const view = graph.value
  if (!view) return
  if (tabIndex.value !== 0) {
    tabIndex.value = 0
    await nextTick()
  }
  const nodePositions = graphRef.value?.currentPositions() ?? []
  if (nodePositions.length === 0) return
  // 画布尺寸按内容包围盒算，不再固定 1200×800；
  // 否则单命题的图在默认画布里只占一个角，导出的 PNG 也大半是空白。
  const bounds = graphRef.value?.contentBounds()
  const canvasWidth = bounds
    ? Math.max(640, Math.round(bounds.maxX - bounds.minX + CANVAS_PAD * 2))
    : 800
  const canvasHeight = bounds
    ? Math.max(480, Math.round(bounds.maxY - bounds.minY + CANVAS_PAD * 2))
    : 600
  saving.value = true
  try {
    const result = await saveGraphLayout(view.graphId, {
      canvasWidth,
      canvasHeight,
      nodePositions
    })
    draftPositions.value = null
    toast(`布局已保存（版本 ${result.layoutVersion}）`)
    await refresh()
  } catch (error) {
    toast(errorMessageOf(error), 'err')
  } finally {
    saving.value = false
  }
}

/**
 * 清除已保存布局。注意：清的是「坐标」，不是图里的节点。
 *
 * 图是从当前命题与关系**推导**出来的（GR01 每次都按现状重建），
 * 命题只要还属于这份草稿，节点就一定还在——「重置图示后节点还在」不是 bug。
 * 要让某个节点消失，下一步只有一个：在「五、已标注关系」里删掉相关关系
 * （命题本身归成员A的要素管理，本页不删命题）。
 */
async function confirmResetLayout(): Promise<void> {
  const view = graph.value
  if (!view) return
  saving.value = true
  try {
    await resetGraphLayout(view.graphId)
    draftPositions.value = null
    resetLayoutOpen.value = false
    toast('已清除保存的布局，图示按自动布局重新排列')
    await refresh()
  } catch (error) {
    toast(errorMessageOf(error), 'err')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <!-- 403：整页换成权限说明，后端文案原样显示 -->
  <ForbiddenNotice v-if="forbidden && !graph" :message="forbidden" @retry="loadAll" />
  <div v-else class="wb">
    <!-- ── 左：裁判理由原文 ─────────────────────────────────────── -->
    <section class="wb-col wb-left">
      <div class="wb-col-head">
        <h3>裁判理由原文</h3>
        <span class="t-xs muted">模拟命题 · 点击定位</span>
      </div>
      <div class="wb-col-body">
        <div class="doc-source">
          <p
            v-for="prop in propositions"
            :key="prop.id"
            class="clause"
            :class="{ 'is-sel': prop.id === activePropositionId }"
            :data-type="typeGroup(prop.labelCode)"
            :title="prop.proposition ? '命题，可参与论证关系' : '非命题要素，不能参与论证关系'"
            @click="selectProposition(prop)"
          >
            <span class="clause-n">{{ prop.displayNo ?? '非命题' }}</span>
            <span>{{ prop.text }}</span>
          </p>
          <p v-if="!loading && propositions.length === 0" class="t-sm muted">
            当前草稿没有可标注的要素。
          </p>
        </div>
      </div>
    </section>

    <div class="splitter" />

    <!-- ── 中：要素与关系 ───────────────────────────────────────── -->
    <section class="wb-col wb-center">
      <div class="wb-col-head">
        <h3>要素与关系</h3>
        <span class="t-xs muted">S → J → M → A → I</span>
        <span style="flex: 1"></span>
        <span class="t-xs muted">{{ relations.length }} 条</span>
      </div>

      <!-- 校验结论常驻：通过/不通过一眼可见，不必翻到第六节去找状态 -->
      <div class="wb-banner" :class="`is-${validationTone}`">
        <Icon
          :name="validationTone === 'ok' ? 'checkc' : validationTone === 'bad' ? 'alert' : 'info'"
          cls="banner-ico"
        />
        <div class="wb-banner-text">
          <b>{{ validationTitle }}</b>
          <span>{{ validationDetail }}</span>
        </div>
        <button class="btn btn-secondary btn-sm" :disabled="loading" @click="runValidation">
          <Icon name="refresh" />
          重新校验
        </button>
      </div>

      <div class="wb-col-body">
        <!-- 关系类型 -->
        <div class="op-sec">
          <h4>一、关系类型</h4>
          <div class="rel-pick">
            <button
              v-for="code in REL_ANNOTATION_ORDER"
              :key="code"
              :class="{ 'is-on': editor.code === code }"
              :style="{ color: REL_META[code].color }"
              @click="pickCode(code)"
            >
              <span>{{ REL_META[code].glyph }}</span>
              <span class="rp-name">{{ REL_META[code].name }}</span>
              <span class="cp-code">{{ code }}</span>
            </button>
          </div>
          <p class="t-xs muted" style="margin-top: 8px">
            关系类型在建立后不可修改；修改参与项请用「修改」。
          </p>
        </div>

        <!-- 参与项 -->
        <div class="op-sec">
          <h4>二、参与项</h4>
          <p v-if="!activeMeta" class="t-sm muted">先在上方选择要建立的关系类型。</p>
          <template v-else>
            <p class="t-xs muted" style="margin-bottom: 8px">
              {{ activeMeta.participants }} · {{ activeMeta.directionRule }}
            </p>

            <div v-if="directing" class="chip-grid">
              <button
                v-for="(slot, index) in slotLabels"
                :key="index"
                class="chip-pick"
                :class="{ 'is-on': !!editor.picked[index] }"
                :style="editor.picked[index] ? { color: activeMeta.color } : undefined"
                @click="toggleSlot(index)"
              >
                <span class="cp-code">{{ index === 0 ? '来源' : '目标' }}</span>
                <span>{{ editor.picked[index] ? describe(editor.picked[index]) : slot }}</span>
                <Icon v-if="editor.picked[index]" name="x" />
              </button>
            </div>

            <div v-else class="chip-grid">
              <button
                v-for="(info, index) in editor.picked"
                :key="`${info.targetKind}-${info.targetId}`"
                class="chip-pick is-on"
                :style="{ color: activeMeta.color }"
                @click="unpick(index)"
              >
                <span class="cp-code">{{ info.targetKind === 'ELEMENT' ? '命题' : '关系' }}</span>
                <span>{{ describe(info) }}</span>
                <Icon name="x" />
              </button>
              <span v-if="editor.picked.length === 0" class="t-xs muted">
                点击下方候选加入成员（至少 2 个）
              </span>
            </div>

            <p
              v-if="representative"
              class="t-xs"
              style="margin-top: 8px; color: var(--rel-i)"
            >
              I 的代表节点取组内最大展示编号命题：{{ describe(representative) }}
            </p>

            <div style="display: flex; gap: 8px; margin-top: 12px">
              <button class="btn btn-primary btn-sm" :disabled="saving" @click="submit">
                <Icon name="check" />
                {{ activeRelation ? '保存修改' : '建立关系' }}
              </button>
              <button class="btn btn-secondary btn-sm" @click="resetEditor">
                <Icon name="undo" />
                重置
              </button>
            </div>
            <p v-if="editor.error" class="t-xs" style="margin-top: 8px; color: var(--danger)">
              {{ editor.error }}
            </p>
          </template>
        </div>

        <!-- 候选：命题 -->
        <div class="op-sec">
          <h4>三、候选命题</h4>
          <div class="el-list" style="padding: 0">
            <div
              v-for="row in elementRows"
              :key="row.info.targetId"
              class="el-item"
              :class="{ 'is-active': isPicked(row.info) }"
              :style="{ opacity: rowCheck(row).ok || isPicked(row.info) ? 1 : 0.55 }"
              :title="rowCheck(row).reason"
              @click="toggleCandidate(row)"
            >
              <div class="el-top">
                <span class="el-no">{{ row.label }}</span>
                <Chip v-if="row.labelCode" :code="row.labelCode" long />
              </div>
              <div class="el-text">{{ row.text }}</div>
            </div>
          </div>
        </div>

        <!-- 候选：已有关系（嵌套） -->
        <div class="op-sec">
          <h4>四、候选关系（嵌套）</h4>
          <p class="t-xs muted" style="margin-bottom: 8px">
            嵌套按指南从内到外构造：先建 J/M，再让 S/A/M 以关系整体作参与项。
          </p>
          <div class="rel-list" style="padding: 0">
            <div
              v-for="row in relationRows"
              :key="row.info.targetId"
              class="rel-item"
              :class="{ 'is-active': isPicked(row.info) }"
              :style="{ opacity: rowCheck(row).ok || isPicked(row.info) ? 1 : 0.55 }"
              :title="rowCheck(row).reason"
              @click="toggleCandidate(row)"
            >
              <span :style="{ color: REL_META[row.info.code as RelationType].color }">
                {{ REL_META[row.info.code as RelationType].glyph }}
              </span>
              <span class="formula">{{ row.label }}</span>
            </div>
            <p v-if="relationRows.length === 0" class="t-xs muted">
              还没有可作为参与项的关系。
            </p>
          </div>
        </div>

        <!-- 已标注关系 -->
        <div class="op-sec">
          <h4>五、已标注关系</h4>
          <template v-for="code in REL_ANNOTATION_ORDER" :key="code">
            <div class="section-label" style="margin: 6px 0">
              {{ code }} {{ REL_META[code].name }}
              <span :style="{ color: REL_META[code].color }">{{ REL_META[code].glyph }}</span>
            </div>
            <div class="rel-list" style="padding: 0">
              <div
                v-for="rel in relationsByCode.get(code) || []"
                :key="rel.id"
                class="rel-item"
                :class="{ 'is-active': rel.id === activeNodeId }"
                @click="editRelation(rel)"
              >
                <span :style="{ color: REL_META[code].color }">{{ REL_META[code].glyph }}</span>
                <span class="formula">{{ rel.expression }}</span>
                <button class="btn btn-icon btn-ghost" title="修改参与项" @click.stop="editRelation(rel)">
                  <Icon name="edit" />
                </button>
                <button class="btn btn-icon btn-ghost" title="删除关系" @click.stop="askDelete(rel)">
                  <Icon name="trash" />
                </button>
              </div>
              <p
                v-if="(relationsByCode.get(code)?.length ?? 0) === 0"
                class="t-xs muted"
              >
                尚未标注
              </p>
            </div>
          </template>
        </div>

        <!-- 嵌套与完整性检查 -->
        <div class="op-sec" style="border-bottom: 0">
          <h4>六、嵌套与完整性检查</h4>
          <p class="t-xs muted">
            图示状态：
            <b :style="{ color: passed ? 'var(--success)' : 'var(--danger)' }">
              {{ validation ? (passed ? '通过' : '未通过') : '尚未校验' }}
            </b>
            · 独立论证图 {{ validation?.componentCount ?? graph?.componentCount ?? 0 }} 个
            · 孤立命题 {{ validation?.isolatedNodeIds.length ?? 0 }} 个
          </p>
          <div v-if="issues.length" style="margin-top: 8px">
            <p
              v-for="(issue, index) in issues"
              :key="`${issue.code}-${index}`"
              class="t-xs"
              :style="{
                color: issue.level === 'ERROR' ? 'var(--danger)' : 'var(--warn)',
                cursor: issue.relationId ? 'pointer' : 'default'
              }"
              @click="focusIssue(issue)"
            >
              <Icon :name="issue.level === 'ERROR' ? 'alert' : 'info'" />
              {{ issue.message }}
              <span v-if="issue.relationId" class="muted">（点击定位）</span>
            </p>
          </div>
          <p v-else-if="passed" class="t-xs muted" style="margin-top: 8px">
            未发现错误方向、非法嵌套、J/M 混用与孤立命题问题。
          </p>
          <div v-if="pendingHints.length" style="margin-top: 8px">
            <p v-for="hint in pendingHints" :key="hint" class="t-xs" style="color: var(--warn)">
              <Icon name="info" />
              {{ hint }}
            </p>
          </div>
        </div>
      </div>
    </section>

    <div class="splitter" />

    <!-- ── 右：论证图示 ─────────────────────────────────────────── -->
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
        <!-- 校验状态与布局版本分开显示：两件事，别混成一个「·」 -->
        <span
          class="t-xs"
          :style="{ color: validationTone === 'ok' ? 'var(--success)' : validationTone === 'bad' ? 'var(--danger)' : 'var(--muted)' }"
        >
          {{ validationTone === 'ok' ? '校验通过' : validationTone === 'bad' ? `未通过 ${errorIssues.length}` : '待校验' }}
        </span>
        <span class="t-xs muted">布局 v{{ graph?.layoutVersion ?? 0 }}</span>
        <button class="btn btn-icon btn-ghost" title="适应窗口" @click="refit">
          <Icon name="fit" />
        </button>
        <button class="btn btn-icon btn-ghost" title="放大" @click="zoom(1.15)">
          <Icon name="zin" />
        </button>
        <button class="btn btn-icon btn-ghost" title="缩小" @click="zoom(0.87)">
          <Icon name="zout" />
        </button>
        <button class="btn btn-icon btn-ghost" title="回到原点" @click="resetView">
          <Icon name="undo" />
        </button>
        <button
          class="btn btn-icon btn-ghost"
          title="重新布局（孤立命题放左上，其余按层排列）"
          @click="relayout"
        >
          <Icon name="layers" />
        </button>
        <button
          class="btn btn-icon btn-ghost"
          title="清除已保存的布局"
          @click="resetLayoutOpen = true"
        >
          <Icon name="refresh" />
        </button>
        <GraphExportButtons :target="graphRef" :name="`标注工作台-${draftId}`" />
        <button class="btn btn-secondary btn-sm" :disabled="saving" @click="saveLayout">
          <Icon name="save" />
          保存布局
        </button>
      </div>
      <ArgumentGraph
        ref="graphRef"
        style="flex: 1 1 auto; min-height: 0"
        :nodes="visibleNodes"
        :edges="visibleEdges"
        :positions="savedPositions"
        :active-id="activeNodeId"
        :editable="tabIndex === 0"
        @update:active-id="activeNodeId = $event"
        @node-click="onGraphNodeClick"
        @positions-change="onPositionsChange"
      />
      <div class="graph-legend">
        <span v-for="item in legendItems" :key="item.code" class="lg">
          <span :style="{ color: item.meta.color, fontFamily: 'var(--font-mono)' }">
            {{ item.meta.glyph }}
          </span>
          {{ item.code }} {{ item.meta.name }}
          <span class="muted">（{{ item.meta.directed ? '有向' : '无向' }}）</span>
        </span>
        <span class="lg muted">拖动节点改坐标；滚轮缩放；空白处拖动平移</span>
      </div>
    </section>
  </div>

  <BaseModal
    v-model:open="deleteState.open"
    title="删除关系"
    :sub="`${deleteState.formula} · 被其它关系嵌套引用时不能删除（不级联）`"
  >
    <div class="field">
      <label>删除原因<span class="req">*</span></label>
      <input
        v-model="deleteState.reason"
        class="input"
        placeholder="例如：与裁判理由不符，重新标注"
      />
      <span class="hint">删除原因随裁定记录留档（记录与版本框架归成员D）。</span>
    </div>
    <template #footer>
      <button class="btn btn-secondary" @click="deleteState.open = false">取消</button>
      <button class="btn btn-danger" @click="confirmDelete">确认删除</button>
    </template>
  </BaseModal>

  <BaseModal
    v-model:open="resetLayoutOpen"
    title="清除已保存的布局"
    sub="只清除坐标，不删除命题与关系"
  >
    <p class="t-sm" style="line-height: 1.7">
      清除后图示会按自动布局重新排列（孤立命题在左上，有关系的部分按层展开）。
      命题仍属于本图，所以节点不会消失——图示是从当前命题与关系推导出来的。
    </p>
    <p class="t-sm" style="line-height: 1.7; margin-top: 8px">
      如果想让某个节点从图上消失，下一步只有一个：在「五、已标注关系」里删除与它相关的关系；
      命题本身由成员A的要素管理维护，本页不删除命题。
    </p>
    <template #footer>
      <button class="btn btn-secondary" @click="resetLayoutOpen = false">取消</button>
      <button class="btn btn-danger" :disabled="saving" @click="confirmResetLayout">
        确认清除
      </button>
    </template>
  </BaseModal>
</template>

<style scoped>
/* 工作台占满内容区：抵消 .content 的内边距，使 .wb 的 100vh 高度成立 */
.wb {
  margin: calc(var(--sp-6) * -1) calc(var(--sp-6) * -1) calc(var(--sp-9) * -1);
}
@media (max-width: 900px) {
  .wb {
    margin: calc(var(--sp-4) * -1) calc(var(--sp-4) * -1) calc(var(--sp-8) * -1);
  }
}
</style>
