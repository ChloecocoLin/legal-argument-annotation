<script setup lang="ts">
// 教学练习（成员C，分工.md §四-7）。五类练习共用一页：
//   S 专项 / J-M 区分 / A-I 区分 / 嵌套 / 学生图与标准图比较。
//
// 左栏选题，中栏作答（建关系，规则与标注工作台同一套 shared/relationRules），
// 右栏是提交后的评分：分数、逐维度达成度、逐条中文反馈、标准答案与讲评要点。
// GRAPH_COMPARE 会在中栏底部并排画出「标准图 / 学生图」——两边的节点与连线都由后端
// GraphAssembler 生成，前端只负责画，不重算「哪条边连哪两个节点」。
//
// 三个刻意的界面约束：
//   1. 提交前**不显示任何标准答案**（后端详情接口也不返回），否则练习就失效了；
//   2. 任何一次修改作答都会清掉上一次的评分结果——避免右栏显示的是「上一次的分数」；
//   3. 评分是只读试算，可以反复提交，页面上不出现"保存成绩"之类的按钮（成绩归 D）。
import { computed, onMounted, reactive, ref } from 'vue'
import ArgumentGraph from '@/features/annotation/ArgumentGraph.vue'
import Chip from '@/shared/Chip.vue'
import Icon from '@/shared/Icon.vue'
import { toast } from '@/shared/toast'
import { errorMessageOf } from '@/shared/apiError'
import { REL_ANNOTATION_ORDER, REL_META } from '@/shared/relationMeta'
import {
  checkParticipant,
  parseDisplayNo,
  type CandidateCheck,
  type CandidateInfo
} from '@/shared/relationRules'
import { getExercise, listExercises, scoreExercise } from '@/api/teaching'
import type { RelationType } from '@/types/common'
import type { ParticipantRole } from '@/types/relation'
import type {
  ExerciseDetail,
  ExerciseSummary,
  RelationTemplate,
  TeachingScoreResult,
  TemplateParticipant
} from '@/types/teaching'

/** 一条关系至少要有 2 个参与项（与后端 RelationRules 的 minParticipants 一致） */
const MIN_PARTICIPANTS = 2
/** 嵌套模板的最大深度，与后端 RelationCanonicalizer.MAX_DEPTH 一致；仅用于界面防死循环 */
const MAX_DEPTH = 12

interface CandidateRow {
  info: CandidateInfo
  label: string
  text: string
  labelCode: string | null
  proposition: boolean
}

const exercises = ref<ExerciseSummary[]>([])
const activeId = ref<string | null>(null)
const detail = ref<ExerciseDetail | null>(null)
const result = ref<TeachingScoreResult | null>(null)
const loading = ref(false)
const scoring = ref(false)

const editor = reactive({
  code: null as RelationType | null,
  picked: [] as CandidateInfo[],
  error: ''
})
/** 学生作答：内存里的关系模板，**不落库**（学生作答版本与成绩归 D） */
const answers = ref<RelationTemplate[]>([])
/** ref 只增不减，删掉中间的关系后新关系也不会撞名 */
let refSeq = 0

const activeMeta = computed(() => (editor.code ? REL_META[editor.code] : null))
const directed = computed(() => !!activeMeta.value?.directed)
/** 有向关系恰好 2 个槽位；无向关系（J/I）成员数不限 */
const maxSlots = computed(() => (directed.value ? MIN_PARTICIPANTS : Number.POSITIVE_INFINITY))

const slotLabels = computed(() =>
  directed.value
    ? [activeMeta.value?.sourceLabel || '来源', activeMeta.value?.targetLabel || '目标']
    : []
)

/** 当前要填的槽位对应的角色：有向按第 1/2 槽，无向一律 MEMBER */
function roleAt(index: number): ParticipantRole {
  if (!directed.value) return 'MEMBER'
  return index === 0 ? 'SOURCE' : 'TARGET'
}

// ── 选题 ──────────────────────────────────────────────────────────────

async function loadExercises() {
  try {
    exercises.value = await listExercises()
    if (exercises.value.length > 0) {
      await selectExercise(exercises.value[0].id)
    }
  } catch (error) {
    toast(errorMessageOf(error), 'err')
  }
}

async function selectExercise(exerciseId: string) {
  activeId.value = exerciseId
  loading.value = true
  resetAll()
  try {
    detail.value = await getExercise(exerciseId)
  } catch (error) {
    detail.value = null
    toast(errorMessageOf(error), 'err')
  } finally {
    loading.value = false
  }
}

// ── 作答：候选、选择、建立关系 ─────────────────────────────────────────

const elementRows = computed<CandidateRow[]>(() =>
  (detail.value?.elements ?? []).map((el) => ({
    info: {
      targetKind: 'ELEMENT',
      targetId: el.id,
      code: el.labelCode,
      displayNo: el.displayNo,
      proposition: el.proposition
    },
    label: el.displayNo ?? '非命题',
    text: el.text,
    labelCode: el.labelCode,
    proposition: el.proposition
  }))
)

const relationRows = computed<CandidateRow[]>(() => {
  const byRef = indexOf(answers.value)
  return answers.value.map((tpl) => ({
    info: {
      targetKind: 'RELATION',
      targetId: tpl.ref,
      code: tpl.code,
      displayNo: null,
      proposition: true
    },
    label: templateText(tpl, byRef),
    text: '',
    labelCode: null,
    proposition: true
  }))
})

function isPicked(info: CandidateInfo): boolean {
  return editor.picked.some(
    (p) => p.targetKind === info.targetKind && p.targetId === info.targetId
  )
}

/** 候选能不能进当前槽位：先看规则矩阵，再看槽位是否已满 */
function rowCheck(row: CandidateRow): CandidateCheck {
  const code = editor.code
  if (!code) return { ok: false, reason: '请先选择关系类型' }
  if (isPicked(row.info)) return { ok: true, reason: '已选为参与项' }
  if (editor.picked.length >= maxSlots.value) {
    return { ok: false, reason: `${REL_META[code].name}只能有 ${maxSlots.value} 个参与项` }
  }
  return checkParticipant(code, roleAt(editor.picked.length), row.info)
}

function toggleCandidate(row: CandidateRow) {
  const index = editor.picked.findIndex(
    (p) => p.targetKind === row.info.targetKind && p.targetId === row.info.targetId
  )
  if (index > -1) {
    editor.picked.splice(index, 1)
    editor.error = ''
    return
  }
  const check = rowCheck(row)
  if (!check.ok) {
    editor.error = check.reason
    return
  }
  editor.picked.push(row.info)
  editor.error = ''
}

function pickCode(code: RelationType) {
  editor.code = code
  editor.picked = []
  editor.error = ''
}

function resetEditor() {
  editor.code = null
  editor.picked = []
  editor.error = ''
}

function addRelation() {
  const code = editor.code
  if (!code) {
    editor.error = '请先选择关系类型'
    return
  }
  if (editor.picked.length < MIN_PARTICIPANTS) {
    editor.error = `${REL_META[code].name}至少需要 ${MIN_PARTICIPANTS} 个参与项`
    return
  }
  const ordered = directed.value
    ? editor.picked.slice(0, MIN_PARTICIPANTS)
    : [...editor.picked].sort((a, b) => sortKey(a) - sortKey(b))

  refSeq += 1
  answers.value.push({
    ref: `r${refSeq}`,
    code,
    participants: ordered.map((info, index) => participantOf(info, index)),
    note: null
  })
  resetEditor()
  invalidate()
}

function participantOf(info: CandidateInfo, index: number): TemplateParticipant {
  const nested = info.targetKind === 'RELATION'
  return {
    ordinal: index + 1,
    role: roleAt(index),
    targetKind: info.targetKind,
    // 关系端点必须走 targetRef：把引用名写进 targetId 会被后端当成「引用了不存在的 ref」
    targetId: nested ? null : info.targetId,
    targetRef: nested ? info.targetId : null
  }
}

/** 无向关系的成员按展示编号排序，与后端「J 成员按展示编号输出」一致；关系端点排在最前 */
function sortKey(info: CandidateInfo): number {
  if (info.targetKind === 'RELATION') return -1
  return parseDisplayNo(info.displayNo)
}

function removeRelation(tpl: RelationTemplate) {
  const byRef = indexOf(answers.value)
  const referencing = answers.value.filter((t) =>
    t.participants.some((p) => p.targetRef === tpl.ref)
  )
  if (referencing.length > 0) {
    // 与后端 NESTED_REFERENCE_EXISTS 同一道理：不级联删除，先删外层
    const names = referencing.map((t) => templateText(t, byRef)).join('、')
    toast(`${names} 以这条关系为参与项，请先删除外层关系`, 'err')
    return
  }
  answers.value = answers.value.filter((t) => t.ref !== tpl.ref)
  invalidate()
}

function answersOf(code: RelationType): RelationTemplate[] {
  return answers.value.filter((t) => t.code === code)
}

/** 任何一次修改都清掉旧评分：右栏绝不能停在上一次作答的分数上 */
function invalidate() {
  result.value = null
}

// ── 表达式渲染（仅显示；权威表达式由后端生成） ────────────────────────

function indexOf(list: RelationTemplate[]): Map<string, RelationTemplate> {
  const map = new Map<string, RelationTemplate>()
  list.forEach((tpl) => map.set(tpl.ref, tpl))
  return map
}

function elementName(id: string | null): string {
  if (!id) return '?'
  const found = (detail.value?.elements ?? []).find((el) => el.id === id)
  return found?.displayNo ?? found?.labelCode ?? id
}

function templateText(
  tpl: RelationTemplate,
  byRef: Map<string, RelationTemplate>,
  depth = 0
): string {
  if (depth > MAX_DEPTH) return '…'
  const inner = tpl.participants
    .map((p) => {
      if (p.targetKind !== 'RELATION') return elementName(p.targetId)
      const target = p.targetRef ? byRef.get(p.targetRef) : undefined
      return target ? templateText(target, byRef, depth + 1) : (p.targetRef ?? '?')
    })
    .join(',')
  return `${tpl.code}(${inner})`
}

// ── 提交评分 ──────────────────────────────────────────────────────────

async function submit() {
  if (!detail.value) return
  scoring.value = true
  try {
    result.value = await scoreExercise(detail.value.id, answers.value)
    toast(`本题得分 ${result.value.score} 分`, result.value.score >= 60 ? 'ok' : 'err')
  } catch (error) {
    toast(errorMessageOf(error), 'err')
  } finally {
    scoring.value = false
  }
}

function resetAnswer() {
  answers.value = []
  refSeq = 0
  resetEditor()
  invalidate()
}

function resetAll() {
  answers.value = []
  refSeq = 0
  resetEditor()
  result.value = null
}

function percentOf(value: number): number {
  return Math.round(value * 100)
}

function scoreColor(score: number): string {
  if (score >= 85) return 'var(--success)'
  if (score >= 60) return 'var(--warn)'
  return 'var(--danger)'
}

function feedbackColor(level: string): string {
  return level === 'ERROR' ? 'var(--danger)' : 'var(--warn)'
}

function describe(info: CandidateInfo | null): string {
  if (!info) return '—'
  if (info.targetKind === 'RELATION') {
    const tpl = answers.value.find((t) => t.ref === info.targetId)
    return tpl ? templateText(tpl, indexOf(answers.value)) : info.targetId
  }
  return elementName(info.targetId)
}

onMounted(loadExercises)
</script>

<template>
  <div class="wb">
    <!-- ── 左：题库 ─────────────────────────────────────────────── -->
    <section class="wb-col wb-left">
      <div class="wb-col-head">
        <h3>关系练习</h3>
        <span class="t-xs muted">{{ exercises.length }} 题</span>
      </div>
      <div class="wb-col-body">
        <div class="el-list">
          <div
            v-for="ex in exercises"
            :key="ex.id"
            class="el-item"
            :class="{ 'is-active': ex.id === activeId }"
            @click="selectExercise(ex.id)"
          >
            <div class="el-top">
              <span class="el-no">{{ ex.exerciseTypeLabel }}</span>
              <span class="t-xs muted">{{ ex.exerciseCode }}</span>
            </div>
            <div class="el-text">{{ ex.title }}</div>
          </div>
          <p v-if="exercises.length === 0" class="t-sm muted">题库为空。</p>
        </div>

        <div v-if="detail" class="op-sec" style="border-bottom: 0">
          <h4>评分维度</h4>
          <p
            v-for="dim in detail.dimensions"
            :key="dim.key"
            class="t-xs muted between"
            style="margin-bottom: 6px"
          >
            <span>{{ dim.label }}</span>
            <span class="mono">{{ dim.weight }} 分</span>
          </p>
          <p class="t-xs faint" style="margin-top: 8px">
            没有可判对象（如本题不含方向）的维度会被整块剔除，权重留给其余维度重新归一。
          </p>
        </div>
      </div>
    </section>

    <div class="splitter" />

    <!-- ── 中：作答 ─────────────────────────────────────────────── -->
    <section class="wb-col wb-center">
      <div class="wb-col-head">
        <h3>{{ detail?.title ?? '作答' }}</h3>
        <span style="flex: 1"></span>
        <span class="t-xs muted">{{ answers.length }} 条关系</span>
      </div>
      <div class="wb-col-body">
        <p v-if="loading" class="t-sm muted" style="padding: 14px">加载中…</p>

        <template v-else-if="detail">
          <!-- 题干与命题清单 -->
          <div class="op-sec">
            <h4>一、题干</h4>
            <p class="t-sm" style="line-height: 1.7">{{ detail.prompt }}</p>
          </div>

          <div class="op-sec">
            <h4>二、本题命题</h4>
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
                  <span v-if="!row.proposition" class="badge warn">非命题</span>
                </div>
                <div class="el-text">{{ row.text }}</div>
              </div>
            </div>
          </div>

          <!-- 关系类型 -->
          <div class="op-sec">
            <h4>三、关系类型</h4>
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
          </div>

          <!-- 参与项 -->
          <div class="op-sec">
            <h4>四、参与项</h4>
            <p v-if="!activeMeta" class="t-sm muted">先在上方选择要建立的关系类型。</p>
            <template v-else>
              <p class="t-xs muted" style="margin-bottom: 8px">
                {{ activeMeta.participants }} · {{ activeMeta.directionRule }}
              </p>

              <div v-if="directed" class="chip-grid">
                <button
                  v-for="(slot, index) in slotLabels"
                  :key="index"
                  class="chip-pick"
                  :class="{ 'is-on': !!editor.picked[index] }"
                  :style="editor.picked[index] ? { color: activeMeta.color } : undefined"
                  @click="editor.picked.splice(index, 1)"
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
                  @click="editor.picked.splice(index, 1)"
                >
                  <span class="cp-code">{{ info.targetKind === 'ELEMENT' ? '命题' : '关系' }}</span>
                  <span>{{ describe(info) }}</span>
                  <Icon name="x" />
                </button>
                <span v-if="editor.picked.length === 0" class="t-xs muted">
                  点击下方候选加入成员（至少 {{ MIN_PARTICIPANTS }} 个）
                </span>
              </div>

              <div style="display: flex; gap: 8px; margin-top: 12px">
                <button class="btn btn-primary btn-sm" @click="addRelation">
                  <Icon name="check" />
                  加入作答
                </button>
                <button class="btn btn-secondary btn-sm" @click="resetEditor">
                  <Icon name="undo" />
                  重置
                </button>
              </div>
              <p
                v-if="editor.error"
                class="t-xs"
                style="margin-top: 8px; color: var(--danger)"
              >
                {{ editor.error }}
              </p>
            </template>
          </div>

          <!-- 候选关系（嵌套） -->
          <div class="op-sec">
            <h4>五、候选关系（嵌套）</h4>
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

          <!-- 我的作答 -->
          <div class="op-sec">
            <h4>六、我的作答</h4>
            <template v-for="code in REL_ANNOTATION_ORDER" :key="code">
              <template v-if="answersOf(code).length > 0">
                <div class="section-label" style="margin: 6px 0">
                  {{ code }} {{ REL_META[code].name }}
                  <span :style="{ color: REL_META[code].color }">{{ REL_META[code].glyph }}</span>
                </div>
                <div class="rel-list" style="padding: 0">
                  <div
                    v-for="tpl in answersOf(code)"
                    :key="tpl.ref"
                    class="rel-item"
                  >
                    <span :style="{ color: REL_META[code].color }">{{ REL_META[code].glyph }}</span>
                    <span class="formula">{{ templateText(tpl, indexOf(answers)) }}</span>
                    <button
                      class="btn btn-icon btn-ghost"
                      title="删除这条关系"
                      @click.stop="removeRelation(tpl)"
                    >
                      <Icon name="trash" />
                    </button>
                  </div>
                </div>
              </template>
            </template>
            <p v-if="answers.length === 0" class="t-xs muted">尚未建立任何关系。</p>
          </div>

          <!-- 提交 -->
          <div class="op-sec" style="border-bottom: 0">
            <div style="display: flex; gap: 8px">
              <button class="btn btn-primary btn-sm" :disabled="scoring" @click="submit">
                <Icon name="check" />
                {{ scoring ? '评分中…' : '提交评分' }}
              </button>
              <button class="btn btn-secondary btn-sm" :disabled="scoring" @click="resetAnswer">
                <Icon name="undo" />
                清空作答
              </button>
            </div>
            <p class="t-xs muted" style="margin-top: 8px">
              评分是只读试算：不保存作答、不记成绩，可以改完再提交。成绩与班级统计由 D 汇总。
            </p>
          </div>

          <!-- 学生图与标准图对照 -->
          <div v-if="result?.graph" class="op-sec" style="border-bottom: 0">
            <h4>七、学生图与标准图比较</h4>
            <div class="grid-2">
              <div
                class="panel"
                style="height: 300px; display: flex; flex-direction: column; overflow: hidden"
              >
                <div class="card-head tight t-sm">
                  <span class="badge info">标准图</span>
                  <span class="t-xs muted">
                    {{ result.statistics.standardStructures }} 个独立论证图
                  </span>
                </div>
                <ArgumentGraph
                  style="flex: 1 1 auto; min-height: 0"
                  :nodes="result.graph.standardNodes"
                  :edges="result.graph.standardEdges"
                  :positions="[]"
                  :editable="false"
                />
              </div>
              <div
                class="panel"
                style="height: 300px; display: flex; flex-direction: column; overflow: hidden"
              >
                <div class="card-head tight t-sm">
                  <span class="badge accent">学生图</span>
                  <span class="t-xs muted">
                    {{ result.statistics.studentStructures }} 个独立论证图
                  </span>
                </div>
                <ArgumentGraph
                  style="flex: 1 1 auto; min-height: 0"
                  :nodes="result.graph.studentNodes"
                  :edges="result.graph.studentEdges"
                  :positions="[]"
                  :editable="false"
                />
              </div>
            </div>
          </div>
        </template>

        <p v-else class="t-sm muted" style="padding: 14px">请先在左栏选择一道练习。</p>
      </div>
    </section>

    <div class="splitter" />

    <!-- ── 右：评分结果 ─────────────────────────────────────────── -->
    <section class="wb-col wb-right">
      <div class="wb-col-head">
        <h3>评分结果</h3>
        <span style="flex: 1"></span>
        <span v-if="result" class="t-xs muted">
          {{ result.exerciseTypeLabel }} · 只读试算
        </span>
      </div>
      <div class="wb-col-body">
        <p v-if="!result" class="t-sm muted" style="padding: 14px">
          提交评分后在这里显示：总分、逐维度达成度、每条判断错在哪，以及标准答案与讲评要点。
        </p>

        <template v-else>
          <!-- 总分 -->
          <div class="card" style="margin: 14px">
            <div class="stat-card">
              <div class="stat-k">本题得分</div>
              <div class="stat-num" :style="{ color: scoreColor(result.score) }">
                {{ result.score }}<span class="t-sm muted"> / 100</span>
              </div>
              <div class="stat-sub">
                标准答案 {{ result.statistics.standardRelations }} 条 ·
                你的作答 {{ result.statistics.studentRelations }} 条 ·
                完全一致 {{ result.statistics.matched }} 条
              </div>
            </div>
          </div>

          <!-- 维度 -->
          <div class="op-sec">
            <h4>逐维度达成度</h4>
            <div v-for="dim in result.dimensions" :key="dim.key" style="margin-bottom: 12px">
              <div class="between">
                <span class="t-sm">
                  {{ dim.label }}
                  <span class="t-xs muted">· 权重 {{ dim.weight }}</span>
                </span>
                <span
                  class="mono t-sm"
                  :style="{ color: dim.applicable ? scoreColor(percentOf(dim.value)) : 'var(--faint)' }"
                >
                  {{ dim.applicable ? `${percentOf(dim.value)}%` : '不适用' }}
                </span>
              </div>
              <div class="progress" :class="{ warm: dim.applicable && dim.value < 0.6 }">
                <i :style="{ width: `${dim.applicable ? percentOf(dim.value) : 0}%` }" />
              </div>
              <p class="t-xs muted" style="margin-top: 4px">{{ dim.detail }}</p>
            </div>
          </div>

          <!-- 反馈 -->
          <div class="op-sec">
            <h4>逐条反馈</h4>
            <p v-if="result.feedback.length === 0" class="t-sm" style="color: var(--success)">
              <Icon name="check" />
              全部判断与标准答案一致。
            </p>
            <div
              v-for="(fb, index) in result.feedback"
              :key="`${fb.code}-${index}`"
              style="margin-bottom: 10px"
            >
              <p class="t-sm" :style="{ color: feedbackColor(fb.level) }">
                <Icon :name="fb.level === 'ERROR' ? 'alert' : 'info'" />
                {{ fb.message }}
              </p>
              <p
                v-if="fb.studentExpression || fb.standardExpression"
                class="t-xs mono muted"
                style="margin-top: 3px"
              >
                你的：{{ fb.studentExpression ?? '—' }}
                <span v-if="fb.standardExpression"> · 标准：{{ fb.standardExpression }}</span>
              </p>
            </div>
          </div>

          <!-- 作答被校验器拦下的问题 -->
          <div v-if="result.validationIssues.length > 0" class="op-sec">
            <h4>结构问题</h4>
            <p
              v-for="(issue, index) in result.validationIssues"
              :key="`${issue.code}-${index}`"
              class="t-sm"
              :style="{ color: issue.level === 'ERROR' ? 'var(--danger)' : 'var(--warn)' }"
            >
              <Icon :name="issue.level === 'ERROR' ? 'alert' : 'info'" />
              {{ issue.message }}
            </p>
          </div>

          <!-- 计数 -->
          <div class="op-sec">
            <h4>计数</h4>
            <div class="grid-3">
              <div class="panel stat-card">
                <div class="stat-k">遗漏</div>
                <div class="stat-num">{{ result.statistics.missing }}</div>
              </div>
              <div class="panel stat-card">
                <div class="stat-k">多标</div>
                <div class="stat-num">{{ result.statistics.extra }}</div>
              </div>
              <div class="panel stat-card">
                <div class="stat-k">方向错</div>
                <div class="stat-num">{{ result.statistics.wrongDirection }}</div>
              </div>
              <div class="panel stat-card">
                <div class="stat-k">类型错</div>
                <div class="stat-num">{{ result.statistics.wrongType }}</div>
              </div>
              <div class="panel stat-card">
                <div class="stat-k">参与项错</div>
                <div class="stat-num">{{ result.statistics.wrongParticipants }}</div>
              </div>
              <div class="panel stat-card">
                <div class="stat-k">嵌套错</div>
                <div class="stat-num">{{ result.statistics.wrongNesting }}</div>
              </div>
            </div>
          </div>

          <!-- 标准答案 -->
          <div class="op-sec">
            <h4>标准答案</h4>
            <div class="rel-list" style="padding: 0">
              <div
                v-for="rel in result.standardAnswer.relations"
                :key="rel.ref"
                class="rel-item"
              >
                <span :style="{ color: REL_META[rel.code]?.color ?? 'var(--fg)' }">
                  {{ REL_META[rel.code]?.glyph ?? rel.code }}
                </span>
                <span class="formula">{{ rel.expression }}</span>
              </div>
            </div>
            <p class="t-xs muted" style="margin-top: 8px">
              独立论证图 {{ result.standardAnswer.structureCount }} 个 ·
              孤立命题
              {{
                result.standardAnswer.isolatedDisplayNos.length > 0
                  ? result.standardAnswer.isolatedDisplayNos.join('、')
                  : '无'
              }}
            </p>
          </div>

          <!-- 讲评要点 -->
          <div class="op-sec" style="border-bottom: 0">
            <h4>讲评要点</h4>
            <p class="t-sm" style="line-height: 1.75">{{ result.explanation }}</p>
          </div>
        </template>
      </div>
    </section>
  </div>
</template>
