<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getCustomContext, saveCustomAnnotation } from '@/api/adjudication'
import { listLabels } from '@/api/guide'
import BaseModal from '@/shared/BaseModal.vue'
import Chip from '@/shared/Chip.vue'
import ElementTypeSelector from '@/shared/ElementTypeSelector.vue'
import Icon from '@/shared/Icon.vue'
import { TYPE_META, typeGroup } from '@/shared/elementMeta'
import { toast } from '@/shared/toast'
import type {
  CustomAnnotationRequest,
  CustomContext,
  CustomItemKind
} from '@/types/adjudication'
import type { LabelDefinition } from '@/types/guide'

type LoadState = 'loading' | 'ready' | 'missing' | 'error'

interface LocalElement {
  clientKey: string
  displayNo: string
  labelCode: string | null
  labelDefinitionId: string | null
  startOffset: number
  endOffset: number
  text: string
  sourceElementIds: string[]
}

interface SelectionDraft {
  startOffset: number
  endOffset: number
  text: string
}

interface ParagraphRange {
  start: number
  end: number
  text: string
}

interface TextSegment {
  text: string
  elementKey: string | null
  group: string | null
}

const DEMO_PARAGRAPHS = [
  '本院认为，原告与被告于 2023 年 4 月签订《房屋买卖合同》，系双方真实意思表示，内容不违反法律、行政法规的强制性规定，应属合法有效。',
  '依法成立的合同，对当事人具有法律约束力。当事人应当按照约定全面履行自己的义务。被告未按约定期限支付剩余购房款，已构成违约。',
  '综上，原告要求被告支付剩余购房款并承担逾期付款责任的诉讼请求，于法有据，本院予以支持。'
]
const DEMO_DOCUMENT_TEXT = DEMO_PARAGRAPHS.join('\n')

const route = useRoute()
const loadState = ref<LoadState>('loading')
const loadError = ref('')
const loadNotice = ref('')
const demoMode = ref(false)
const context = ref<CustomContext | null>(null)
const labels = ref<LabelDefinition[]>([])
const labelsLoading = ref(false)
const labelsError = ref<string | null>(null)
const documentText = ref('')
const scopeStartOffset = ref(0)
const elements = ref<LocalElement[]>([])
const sourceRef = ref<HTMLElement | null>(null)
const activeKey = ref<string | null>(null)
const selectionDraft = ref<SelectionDraft | null>(null)
const submitOpen = ref(false)
const submitReason = ref('')
const submitting = ref(false)
const validationIssues = ref<string[]>([])
const lastSavedAnnotationId = ref<string | null>(null)
let localSequence = 0

const showWorkbench = computed(() => loadState.value === 'ready' || demoMode.value)
const activeElement = computed(() =>
  elements.value.find((element) => element.clientKey === activeKey.value) ?? null
)
const labeledCount = computed(() => elements.value.filter((element) => element.labelCode).length)
const taskSubtitle = computed(() => {
  if (demoMode.value) return '本地演示数据 · 不会发送到后端'
  if (!context.value) return ''
  return `${context.value.itemKind} · 会话 ${shortId(context.value.session.id)} · 文书 ${shortId(context.value.documentTextVersion.documentId)}`
})
const guideBadge = computed(() => {
  if (demoMode.value) return '本地演示'
  return context.value ? `指南 ${context.value.guideVersion.versionCode}` : '指南待加载'
})

const paragraphRanges = computed<ParagraphRange[]>(() => {
  const ranges: ParagraphRange[] = []
  const matcher = /[^\r\n]+/g
  let match: RegExpExecArray | null
  while ((match = matcher.exec(documentText.value))) {
    ranges.push({
      start: scopeStartOffset.value + match.index,
      end: scopeStartOffset.value + match.index + match[0].length,
      text: match[0]
    })
  }
  if (ranges.length === 0 && documentText.value) {
    ranges.push({
      start: scopeStartOffset.value,
      end: scopeStartOffset.value + documentText.value.length,
      text: documentText.value
    })
  }
  return ranges
})

const selectedLabelCode = computed<string | null>({
  get: () => activeElement.value?.labelCode ?? null,
  set: (value) => {
    if (!activeElement.value || !value) return
    const label = labels.value.find((item) => item.code === value)
    activeElement.value.labelCode = value
    activeElement.value.labelDefinitionId = label?.id ?? null
    validationIssues.value = []
    toast(`${activeElement.value.displayNo} 已标记为 ${value}`)
  }
})

const renderedParagraphs = computed<TextSegment[][]>(() =>
  paragraphRanges.value.map((paragraph) => {
    const intersecting = elements.value
      .filter((element) => element.startOffset < paragraph.end && element.endOffset > paragraph.start)
      .sort((left, right) => left.startOffset - right.startOffset)
    const segments: TextSegment[] = []
    let cursor = paragraph.start

    for (const element of intersecting) {
      const start = Math.max(element.startOffset, paragraph.start)
      const end = Math.min(element.endOffset, paragraph.end)
      if (start > cursor) {
        segments.push({ text: sliceDocument(cursor, start), elementKey: null, group: null })
      }
      if (end > cursor) {
        segments.push({
          text: sliceDocument(Math.max(start, cursor), end),
          elementKey: element.clientKey,
          group: typeGroup(element.labelCode ?? undefined)
        })
        cursor = end
      }
    }
    if (cursor < paragraph.end) {
      segments.push({ text: sliceDocument(cursor, paragraph.end), elementKey: null, group: null })
    }
    return segments
  })
)

function shortId(value: string): string {
  return value.length > 10 ? `${value.slice(0, 8)}…` : value
}

function readQueryValue(value: unknown): string {
  return Array.isArray(value) ? String(value[0] ?? '') : typeof value === 'string' ? value : ''
}

function parseRouteParams(): { sessionId: string; itemKind: CustomItemKind; itemId: string } | null {
  const sessionId = readQueryValue(route.query.sessionId).trim()
  const itemId = readQueryValue(route.query.itemId).trim()
  const rawKind = readQueryValue(route.query.itemKind).trim().toUpperCase()
  if (!sessionId || !itemId || (rawKind !== 'PROPOSITION' && rawKind !== 'RELATION')) return null
  return { sessionId, itemId, itemKind: rawKind }
}

function buildDemoLabels(): LabelDefinition[] {
  return Object.entries(TYPE_META).map(([code, meta], index) => {
    const group = typeGroup(code)
    const isProposition = group !== 'nl'
    return {
      id: `preview-${code}`,
      guideVersionId: 'preview-guide-version',
      code,
      name: meta.name,
      category: isProposition ? 'PROPOSITION' : 'NON_PROPOSITION',
      baseType: isProposition ? group.toUpperCase() : null,
      sortOrder: index + 1,
      definition: meta.name,
      recognitionRules: [],
      signalPhrases: [],
      examples: [],
      notes: '仅用于本地演示'
    }
  })
}

function createDemoElement(
  clientKey: string,
  phrase: string,
  labelCode: string | null
): LocalElement {
  const startOffset = DEMO_DOCUMENT_TEXT.indexOf(phrase)
  return {
    clientKey,
    displayNo: '',
    labelCode,
    labelDefinitionId: labelCode ? `preview-${labelCode}` : null,
    startOffset,
    endOffset: startOffset + phrase.length,
    text: phrase,
    sourceElementIds: []
  }
}

function activateDemo(): void {
  demoMode.value = true
  context.value = null
  labels.value = buildDemoLabels()
  labelsError.value = null
  documentText.value = DEMO_DOCUMENT_TEXT
  scopeStartOffset.value = 0
  elements.value = [
    createDemoElement('local-1', '原告与被告于 2023 年 4 月签订《房屋买卖合同》', 'GF'),
    createDemoElement('local-2', '依法成立的合同，对当事人具有法律约束力', 'GM-L'),
    createDemoElement('local-3', '被告未按约定期限支付剩余购房款', null)
  ]
  localSequence = 3
  renumberElements()
  activeKey.value = elements.value[0]?.clientKey ?? null
  selectionDraft.value = null
  validationIssues.value = []
  lastSavedAnnotationId.value = null
}

function validateContext(value: CustomContext): void {
  const version = value.documentTextVersion
  const scope = value.annotationScope
  if (!version || typeof version.normalizedText !== 'string') {
    throw new Error('J09 未返回 documentTextVersion.normalizedText')
  }
  if (!scope || !Number.isInteger(scope.startOffset) || !Number.isInteger(scope.endOffset)) {
    throw new Error('J09 未返回有效的 annotationScope')
  }
  if (scope.startOffset < 0 || scope.endOffset <= scope.startOffset || scope.endOffset > version.normalizedText.length) {
    throw new Error('J09 返回的标注范围超出正文边界')
  }
  if (!value.guideVersion?.id) throw new Error('J09 未返回 guideVersion.id')
}

function initializeFromContext(value: CustomContext): void {
  const scope = value.annotationScope
  const fullText = value.documentTextVersion.normalizedText
  context.value = value
  scopeStartOffset.value = scope.startOffset
  documentText.value = fullText.slice(scope.startOffset, scope.endOffset)

  const candidates = [...value.candidateElements]
    .filter((element) => element.startOffset >= scope.startOffset && element.endOffset <= scope.endOffset)
    .sort((left, right) => left.startOffset - right.startOffset)
  const accepted: LocalElement[] = []
  let skipped = 0
  for (const element of candidates) {
    const sameRange = accepted.find(
      (item) => element.startOffset === item.startOffset && element.endOffset === item.endOffset
    )
    if (sameRange) {
      sameRange.sourceElementIds = [...new Set([...sameRange.sourceElementIds, element.id])]
      continue
    }
    if (accepted.some((item) => element.startOffset < item.endOffset && element.endOffset > item.startOffset)) {
      skipped += 1
      continue
    }
    accepted.push({
      clientKey: `source-${element.id}`,
      displayNo: element.displayNo ?? '',
      labelCode: element.labelCode || null,
      labelDefinitionId: element.labelDefinitionId,
      startOffset: element.startOffset,
      endOffset: element.endOffset,
      text: element.text || fullText.slice(element.startOffset, element.endOffset),
      sourceElementIds: [element.id]
    })
  }
  elements.value = accepted
  localSequence = accepted.length
  renumberElements()
  activeKey.value = elements.value[0]?.clientKey ?? null
  selectionDraft.value = null
  loadNotice.value = skipped > 0
    ? `${skipped} 个边界不同且相互重叠的候选未自动加入编辑区，请删除冲突区间后重新划选。`
    : ''
}

async function loadLabels(): Promise<void> {
  if (!context.value?.guideVersion.id) return
  labelsLoading.value = true
  labelsError.value = null
  try {
    labels.value = await listLabels(context.value.guideVersion.id)
  } catch {
    labels.value = []
    labelsError.value = '标签字典加载失败，请重试'
  } finally {
    labelsLoading.value = false
  }
}

async function loadContext(): Promise<void> {
  const params = parseRouteParams()
  demoMode.value = false
  context.value = null
  labels.value = []
  loadNotice.value = ''
  selectionDraft.value = null
  validationIssues.value = []
  lastSavedAnnotationId.value = null

  if (!params) {
    loadState.value = 'missing'
    loadError.value = '缺少 sessionId、itemKind 或 itemId，无法读取裁定上下文。'
    return
  }

  loadState.value = 'loading'
  loadError.value = ''
  try {
    const result = await getCustomContext(params.sessionId, {
      itemKind: params.itemKind,
      itemId: params.itemId
    })
    if (!result) throw new Error('J09 未返回裁定上下文数据')
    validateContext(result)
    initializeFromContext(result)
    loadState.value = 'ready'
    await loadLabels()
  } catch (error) {
    loadState.value = 'error'
    loadError.value = error instanceof Error ? error.message : '裁定上下文加载失败'
  }
}

function sliceDocument(startOffset: number, endOffset: number): string {
  return documentText.value.slice(
    Math.max(0, startOffset - scopeStartOffset.value),
    Math.max(0, endOffset - scopeStartOffset.value)
  )
}

function offsetInDocument(container: Node, offset: number): number | null {
  const element = container.nodeType === Node.ELEMENT_NODE ? (container as Element) : container.parentElement
  const paragraph = element?.closest<HTMLElement>('[data-paragraph-index]')
  if (!paragraph) return null
  const paragraphIndex = Number(paragraph.dataset.paragraphIndex)
  const paragraphRange = paragraphRanges.value[paragraphIndex]
  if (!paragraphRange) return null

  const localRange = document.createRange()
  localRange.selectNodeContents(paragraph)
  try {
    localRange.setEnd(container, offset)
  } catch {
    return null
  }
  return paragraphRange.start + localRange.toString().length
}

function rangesOverlap(startOffset: number, endOffset: number): boolean {
  return elements.value.some((element) => startOffset < element.endOffset && endOffset > element.startOffset)
}

function captureSelection(): void {
  const selection = window.getSelection()
  const article = sourceRef.value
  if (!selection || selection.rangeCount === 0 || selection.isCollapsed || !article) return
  const range = selection.getRangeAt(0)
  if (!article.contains(range.commonAncestorContainer)) return

  let startOffset = offsetInDocument(range.startContainer, range.startOffset)
  let endOffset = offsetInDocument(range.endContainer, range.endOffset)
  if (startOffset === null || endOffset === null) return
  if (startOffset > endOffset) [startOffset, endOffset] = [endOffset, startOffset]

  const rawText = sliceDocument(startOffset, endOffset)
  const leadingLength = rawText.length - rawText.trimStart().length
  const trailingLength = rawText.length - rawText.trimEnd().length
  startOffset += leadingLength
  endOffset -= trailingLength
  const text = sliceDocument(startOffset, endOffset)
  selection.removeAllRanges()
  if (!text) return
  if (rangesOverlap(startOffset, endOffset)) {
    selectionDraft.value = null
    toast('所选文本与已有要素重叠，请重新选择', 'err')
    return
  }
  selectionDraft.value = { startOffset, endOffset, text }
}

function renumberElements(): void {
  elements.value = [...elements.value]
    .sort((left, right) => left.startOffset - right.startOffset)
    .map((element, index) => ({ ...element, displayNo: `P${index + 1}` }))
}

function createElement(): void {
  const draft = selectionDraft.value
  if (!draft) return
  const clientKey = `local-${++localSequence}`
  elements.value.push({
    clientKey,
    displayNo: '',
    labelCode: null,
    labelDefinitionId: null,
    startOffset: draft.startOffset,
    endOffset: draft.endOffset,
    text: draft.text,
    sourceElementIds: []
  })
  renumberElements()
  activeKey.value = clientKey
  selectionDraft.value = null
  validationIssues.value = []
  toast('已创建要素，请选择要素类型')
  void nextTick(() => scrollElementCardIntoView(clientKey))
}

function cancelSelection(): void {
  selectionDraft.value = null
}

function selectElement(clientKey: string, source: 'text' | 'list' = 'list'): void {
  activeKey.value = clientKey
  void nextTick(() => {
    if (source === 'text') scrollElementCardIntoView(clientKey)
    else scrollSourceElementIntoView(clientKey)
  })
}

function scrollElementCardIntoView(clientKey: string): void {
  document.querySelector<HTMLElement>(`[data-element-card="${clientKey}"]`)
    ?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
}

function scrollSourceElementIntoView(clientKey: string): void {
  sourceRef.value?.querySelector<HTMLElement>(`[data-source-element="${clientKey}"]`)
    ?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

function deleteElement(clientKey: string): void {
  const target = elements.value.find((element) => element.clientKey === clientKey)
  elements.value = elements.value.filter((element) => element.clientKey !== clientKey)
  if (activeKey.value === clientKey) activeKey.value = elements.value[0]?.clientKey ?? null
  renumberElements()
  validationIssues.value = []
  if (target) toast(`已删除要素 ${target.displayNo}`)
}

function validateElements(): string[] {
  const issues: string[] = []
  const scope = context.value?.annotationScope
  const sorted = [...elements.value].sort((left, right) => left.startOffset - right.startOffset)

  if (demoMode.value) issues.push('本地演示模式不能提交到后端')
  if (!context.value || !scope) issues.push('尚未加载有效的 J09 裁定上下文')
  if (context.value?.itemKind === 'RELATION') {
    issues.push('当前页面只保存命题要素；关系裁定项需在关系标注模块完成')
  }
  if (elements.value.length === 0) issues.push('至少需要创建一个要素')

  for (const element of sorted) {
    if (!element.labelCode) {
      issues.push(`${element.displayNo} 尚未选择要素类型`)
      continue
    }
    if (!labels.value.some((label) => label.code === element.labelCode)) {
      issues.push(`${element.displayNo} 的类型 ${element.labelCode} 不属于当前指南版本`)
    }
    if (scope && (element.startOffset < scope.startOffset || element.endOffset > scope.endOffset)) {
      issues.push(`${element.displayNo} 超出当前标注范围`)
    }
    if (element.startOffset >= element.endOffset) {
      issues.push(`${element.displayNo} 的文本范围无效`)
    } else if (sliceDocument(element.startOffset, element.endOffset) !== element.text) {
      issues.push(`${element.displayNo} 的正文切片与偏移量不一致`)
    }
  }

  for (let index = 1; index < sorted.length; index += 1) {
    if (sorted[index].startOffset < sorted[index - 1].endOffset) {
      issues.push(`${sorted[index - 1].displayNo} 与 ${sorted[index].displayNo} 的范围重叠`)
    }
  }
  return [...new Set(issues)]
}

function openSubmit(): void {
  validationIssues.value = validateElements()
  if (validationIssues.value.length > 0) {
    toast(`提交前仍有 ${validationIssues.value.length} 项需要处理`, 'err')
    return
  }
  submitReason.value = ''
  submitOpen.value = true
}

function buildSaveRequest(reason: string): CustomAnnotationRequest {
  if (!context.value) throw new Error('裁定上下文不存在')
  return {
    itemKind: context.value.itemKind,
    itemId: context.value.item.id,
    customElements: elements.value.map((element) => ({
      clientKey: element.clientKey,
      startOffset: element.startOffset,
      endOffset: element.endOffset,
      labelCode: element.labelCode as string,
      sourceElementIds: element.sourceElementIds
    })),
    customRelations: [],
    reason,
  }
}

async function submitAnnotation(): Promise<void> {
  const reason = submitReason.value.trim()
  if (reason.length < 2) {
    toast('请填写至少 2 个字的裁定理由', 'err')
    return
  }
  const issues = validateElements()
  validationIssues.value = issues
  if (issues.length > 0) {
    submitOpen.value = false
    toast('要素状态已变化，请处理校验问题后重试', 'err')
    return
  }
  if (!context.value) return

  submitting.value = true
  try {
    const result = await saveCustomAnnotation(
      context.value.session.id,
      buildSaveRequest(reason)
    )
    if (!result?.annotationId) {
      throw new Error('J10 当前返回空数据，后端保存接口尚未实现')
    }
    lastSavedAnnotationId.value = result.annotationId
    submitOpen.value = false
    toast('自定义标注快照已保存')
  } catch (error) {
    toast(error instanceof Error ? error.message : '保存自定义标注失败', 'err')
  } finally {
    submitting.value = false
  }
}

watch(() => route.fullPath, () => void loadContext(), { immediate: true })
</script>

<template>
  <div class="annotation-page">
    <section v-if="!showWorkbench" class="context-state" :aria-busy="loadState === 'loading'">
      <span class="context-state-icon" :class="`is-${loadState}`">
        <Icon :name="loadState === 'loading' ? 'refresh' : loadState === 'error' ? 'alert' : 'info'" />
      </span>
      <h1>{{ loadState === 'loading' ? '正在加载裁定上下文' : '暂时无法进入标注工作台' }}</h1>
      <p v-if="loadState === 'loading'">正在调用 J09 读取文书、候选要素和指南版本。</p>
      <p v-else>{{ loadError }}</p>
      <code v-if="loadState === 'missing'" class="query-example">
        ?sessionId=...&amp;itemKind=PROPOSITION&amp;itemId=...
      </code>
      <div v-if="loadState !== 'loading'" class="context-state-actions">
        <button v-if="loadState === 'error'" type="button" class="btn btn-secondary" @click="loadContext">
          <Icon name="refresh" />重新加载
        </button>
        <button type="button" class="btn btn-primary" @click="activateDemo">
          使用本地演示数据
        </button>
      </div>
      <p v-if="loadState === 'error'" class="context-contract-note">
        请确认后端服务已启动，并检查会话、裁定项和指南版本是否有效。
      </p>
    </section>

    <template v-else>
    <header class="workbench-top">
      <div class="task-meta">
        <span class="eyebrow">ELEMENT ANNOTATION</span>
        <h1>要素标注工作台</h1>
        <p>{{ taskSubtitle }}</p>
      </div>

      <div class="task-badges" aria-label="任务状态">
        <span class="workbench-badge is-guide">{{ guideBadge }}</span>
        <span class="workbench-badge">{{ elements.length }} 个要素</span>
      </div>

      <div class="workbench-actions">
        <span class="save-state">
          <span class="save-dot" :class="lastSavedAnnotationId ? 'is-saved' : 'is-local'" />
          {{
            demoMode
              ? '演示数据仅保存在当前页面'
              : lastSavedAnnotationId
                ? `已保存 ${shortId(lastSavedAnnotationId)}`
                : '真实上下文 · 尚未保存'
          }}
        </span>
        <button type="button" class="btn btn-sm btn-ghost" disabled>
          <Icon name="undo" />撤销
        </button>
        <button type="button" class="btn btn-sm btn-ghost" disabled>
          <Icon name="redo" />重做
        </button>
        <button type="button" class="btn btn-sm btn-secondary" disabled>
          <Icon name="eye" />预览图示
        </button>
        <button
          type="button"
          class="btn btn-sm btn-primary"
          :disabled="demoMode || submitting || !context"
          :title="demoMode ? '本地演示数据不能提交' : '保存 J10 自定义标注快照'"
          @click="openSubmit"
        >
          <Icon name="save" />保存标注
        </button>
      </div>
    </header>

    <div v-if="loadNotice" class="context-notice" role="status">
      <Icon name="alert" />
      <span>{{ loadNotice }}</span>
    </div>

    <div class="workbench-grid">
      <section class="workbench-panel source-panel">
        <div class="panel-head">
          <div>
            <h2>裁判理由原文</h2>
            <p>拖动选择要标注的文本片段</p>
          </div>
          <span class="panel-count">
            {{ scopeStartOffset }}–{{ scopeStartOffset + documentText.length }}
          </span>
        </div>

        <div class="source-body">
          <div class="document-heading">
            <span>民事判决书</span>
            <strong>本院认为</strong>
          </div>
          <article
            ref="sourceRef"
            class="document-text"
            aria-label="裁判理由原文"
            @mouseup="captureSelection"
          >
            <p
              v-for="(segments, paragraphIndex) in renderedParagraphs"
              :key="paragraphIndex"
              :data-paragraph-index="paragraphIndex"
            >
              <template v-for="(segment, segmentIndex) in segments" :key="segmentIndex">
                <mark
                  v-if="segment.elementKey"
                  class="source-element"
                  :class="[
                    `is-${segment.group}`,
                    { 'is-active': activeKey === segment.elementKey }
                  ]"
                  :data-source-element="segment.elementKey"
                  @click="selectElement(segment.elementKey, 'text')"
                >{{ segment.text }}</mark>
                <template v-else>{{ segment.text }}</template>
              </template>
            </p>
          </article>
          <div v-if="selectionDraft" class="selection-draft" role="status">
            <div class="selection-copy">
              <span class="selection-label">待创建片段</span>
              <strong>“{{ selectionDraft.text }}”</strong>
              <span class="selection-offset mono">
                {{ selectionDraft.startOffset }}–{{ selectionDraft.endOffset }}
              </span>
            </div>
            <div class="selection-actions">
              <button type="button" class="btn btn-sm btn-ghost" @click="cancelSelection">
                取消
              </button>
              <button type="button" class="btn btn-sm btn-primary" @click="createElement">
                <Icon name="plus" />创建要素
              </button>
            </div>
          </div>
          <div class="source-tip">
            <Icon name="info" />
            <span>拖动选择未标注的原文，确认片段后创建要素；已有高亮区域不可重叠。</span>
          </div>
        </div>
      </section>

      <section class="workbench-panel element-panel">
        <div class="panel-head">
          <div>
            <h2>要素标注</h2>
            <p>选择要素后分配类型</p>
          </div>
          <span class="panel-count">{{ labeledCount }} / {{ elements.length }} 已标注</span>
        </div>

        <div class="element-list" aria-label="要素列表">
          <div v-if="elements.length === 0" class="element-empty">
            <Icon name="edit" />
            <strong>暂无候选要素</strong>
            <span>请在左侧拖动选择原文并创建第一个要素。</span>
          </div>
          <article
            v-for="element in elements"
            :key="element.clientKey"
            class="element-card"
            :class="{ 'is-active': activeKey === element.clientKey }"
            :data-element-card="element.clientKey"
            tabindex="0"
            @click="selectElement(element.clientKey)"
            @keydown.enter="selectElement(element.clientKey)"
          >
            <div class="element-card-top">
              <span class="element-no">{{ element.displayNo }}</span>
              <Chip :code="element.labelCode || undefined" long />
              <span class="element-offset mono">
                {{ element.startOffset }}–{{ element.endOffset }}
              </span>
              <button
                type="button"
                class="icon-action"
                title="删除要素"
                @click.stop="deleteElement(element.clientKey)"
              >
                <Icon name="trash" />
              </button>
            </div>
            <p>{{ element.text }}</p>
          </article>
        </div>

        <div v-if="validationIssues.length" class="validation-panel" role="alert">
          <div class="validation-title">
            <Icon name="alert" />
            <strong>提交前需要处理</strong>
          </div>
          <ul>
            <li v-for="issue in validationIssues" :key="issue">{{ issue }}</li>
          </ul>
        </div>

        <div class="type-section">
          <div class="section-heading">
            <div>
              <h3>要素类型</h3>
              <p v-if="activeElement">当前要素：{{ activeElement.displayNo }}</p>
              <p v-else>请先选择一个要素</p>
            </div>
            <span class="step-tag">{{ demoMode ? '演示标签' : 'G06' }}</span>
          </div>
          <ElementTypeSelector
            v-model="selectedLabelCode"
            :labels="labels"
            :loading="labelsLoading"
            :error="labelsError"
            :disabled="!activeElement || labelsLoading"
            empty-text="暂无可用类型"
            @retry="loadLabels"
          />
        </div>
      </section>

      <section class="workbench-panel graph-panel">
        <div class="panel-head">
          <div>
            <h2>论证图示</h2>
            <p>关系模块接入后自动生成</p>
          </div>
          <div class="graph-tabs" aria-label="图示切换">
            <button type="button" class="is-active" disabled>图示一</button>
            <button type="button" disabled>图示二</button>
          </div>
        </div>

        <div class="graph-toolbar">
          <button type="button" class="btn btn-sm btn-ghost" disabled>
            <Icon name="zin" />
          </button>
          <button type="button" class="btn btn-sm btn-ghost" disabled>
            <Icon name="zout" />
          </button>
          <button type="button" class="btn btn-sm btn-ghost" disabled>
            <Icon name="fit" />适合窗口
          </button>
        </div>

        <div class="graph-stage-placeholder">
          <div class="graph-node node-one">P1</div>
          <div class="graph-node node-two">P2</div>
          <div class="graph-edge" aria-hidden="true" />
          <div class="graph-relation" aria-hidden="true">S</div>
          <div class="graph-empty-copy">
            <Icon name="share" />
            <strong>论证关系图预览</strong>
            <span>当前阶段仅保留原型布局，不创建或保存关系。</span>
          </div>
        </div>

        <div class="graph-legend">
          <span><i class="legend-dot is-support" />S 支持</span>
          <span><i class="legend-dot is-attack" />A 反对</span>
          <span><i class="legend-dot is-combine" />J 组合</span>
        </div>
      </section>
    </div>
    </template>

    <BaseModal
      v-model:open="submitOpen"
      title="保存自定义要素标注"
      sub="J10 仅保存当前自定义快照，不代表完成裁定决定"
    >
      <div class="submit-summary">
        <div><span>裁定项</span><strong>{{ context?.itemKind }}</strong></div>
        <div><span>要素数量</span><strong>{{ elements.length }}</strong></div>
        <div><span>已标注</span><strong>{{ labeledCount }}</strong></div>
      </div>
      <div class="field submit-reason-field">
        <label for="customAnnotationReason">裁定理由</label>
        <textarea
          id="customAnnotationReason"
          v-model="submitReason"
          class="textarea"
          maxlength="500"
          placeholder="说明为何需要重新划分或调整要素类型（必填）"
        />
        <span class="reason-count">{{ submitReason.trim().length }} / 500</span>
      </div>
      <div class="submit-contract-note">
        <Icon name="info" />
        <span>保存会创建新的自定义裁定快照；重复提交由 Idempotency-Key 安全去重。</span>
      </div>
      <template #footer>
        <button class="btn btn-secondary" :disabled="submitting" @click="submitOpen = false">
          取消
        </button>
        <button class="btn btn-primary" :disabled="submitting" @click="submitAnnotation">
          <Icon name="save" />{{ submitting ? '保存中…' : '确认保存' }}
        </button>
      </template>
    </BaseModal>
  </div>
</template>

<style scoped>
.annotation-page {
  display: grid;
  gap: var(--sp-4);
  min-width: 0;
}

.context-state {
  display: flex;
  min-height: 440px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: var(--sp-3);
  padding: var(--sp-7);
  border: 1px solid var(--border);
  border-radius: var(--r-lg);
  background: var(--surface);
  box-shadow: var(--shadow-1);
  text-align: center;
}
.context-state-icon {
  display: grid;
  width: 48px;
  height: 48px;
  place-items: center;
  border-radius: 50%;
  background: var(--accent-soft);
  color: var(--accent);
}
.context-state-icon.is-error { background: var(--danger-soft); color: var(--danger); }
.context-state-icon.is-loading svg { animation: context-spin 0.9s linear infinite; }
.context-state-icon svg { width: 24px; height: 24px; }
.context-state h1 { font-size: var(--fs-h2); }
.context-state > p { max-width: 620px; color: var(--muted); font-size: var(--fs-sm); }
.query-example {
  max-width: 100%;
  overflow-x: auto;
  padding: 8px 12px;
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  background: var(--surface-2);
  color: var(--fg-2);
  font-family: var(--font-mono);
  font-size: var(--fs-xs);
}
.context-state-actions { display: flex; flex-wrap: wrap; justify-content: center; gap: var(--sp-2); }
.context-contract-note { padding-top: var(--sp-3); border-top: 1px solid var(--border); font-size: var(--fs-xs) !important; }
.context-notice {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  padding: 9px 12px;
  border: 1px solid var(--warn);
  border-radius: var(--r-md);
  background: var(--warn-soft);
  color: var(--warm-strong);
  font-size: var(--fs-xs);
}
.context-notice svg { width: 16px; height: 16px; flex: 0 0 16px; }

@keyframes context-spin {
  to { transform: rotate(360deg); }
}

.workbench-top {
  display: flex;
  align-items: center;
  gap: var(--sp-4);
  min-width: 0;
  padding: 14px 16px;
  border: 1px solid var(--border);
  border-radius: var(--r-lg);
  background: var(--surface);
  box-shadow: var(--shadow-1);
}

.task-meta { min-width: 210px; }
.task-meta h1 { margin-top: 2px; font-size: var(--fs-h2); line-height: 1.3; }
.task-meta p { margin-top: 2px; color: var(--muted); font-size: var(--fs-xs); }
.task-badges { display: flex; flex-wrap: wrap; gap: var(--sp-2); }

.workbench-badge,
.panel-count,
.step-tag {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 2px 8px;
  border: 1px solid var(--border);
  border-radius: var(--r-pill);
  background: var(--surface-2);
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 11px;
  white-space: nowrap;
}

.workbench-badge.is-guide { border-color: var(--accent-line); background: var(--accent-soft); color: var(--accent); }
.workbench-actions { display: flex; align-items: center; justify-content: flex-end; gap: var(--sp-2); margin-left: auto; }
.workbench-actions .btn svg,
.graph-toolbar .btn svg { width: 15px; height: 15px; }
.save-state { display: inline-flex; align-items: center; gap: 6px; margin-right: var(--sp-2); color: var(--muted); font-size: var(--fs-xs); white-space: nowrap; }
.save-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--faint); }
.save-dot.is-local { background: var(--warn); }
.save-dot.is-saved { background: var(--success); }

.workbench-grid {
  display: grid;
  grid-template-columns: minmax(260px, 0.82fr) minmax(360px, 1.12fr) minmax(280px, 0.86fr);
  min-height: 670px;
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: var(--r-lg);
  background: var(--surface);
  box-shadow: var(--shadow-1);
}

.workbench-panel { display: flex; min-width: 0; min-height: 0; flex-direction: column; background: var(--surface); }
.workbench-panel + .workbench-panel { border-left: 1px solid var(--border); }
.panel-head { display: flex; min-height: 60px; align-items: center; justify-content: space-between; gap: var(--sp-3); padding: 10px 14px; border-bottom: 1px solid var(--border); background: var(--surface-2); }
.panel-head h2 { font-size: 13.5px; }
.panel-head p { margin-top: 1px; color: var(--muted); font-size: 11.5px; }
.source-body,
.element-panel,
.graph-panel { min-height: 0; }
.source-body { display: flex; flex: 1; flex-direction: column; overflow-y: auto; }

.document-heading { display: flex; flex-direction: column; align-items: center; gap: 4px; padding: 20px 16px 12px; color: var(--fg-2); font-family: var(--font-serif-cn); }
.document-heading span { color: var(--muted); font-size: var(--fs-xs); letter-spacing: 0.18em; }
.document-heading strong { font-size: var(--fs-h3); }
.document-text { padding: 0 18px 18px; color: var(--fg-2); font-family: var(--font-serif-cn); font-size: 14px; line-height: 2; text-align: justify; }
.document-text p { text-indent: 2em; }
.document-text p + p { margin-top: var(--sp-3); }
.source-element {
  padding: 1px 2px;
  border-bottom: 2px solid var(--nl-line);
  border-radius: 3px;
  background: var(--nl-soft);
  color: inherit;
  cursor: pointer;
  transition: box-shadow 0.12s ease, filter 0.12s ease;
}
.source-element:hover { filter: saturate(1.25); }
.source-element.is-active { box-shadow: 0 0 0 1.5px var(--accent); }
.source-element.is-gm { border-bottom-color: var(--gm-line); background: var(--gm-soft); }
.source-element.is-sm { border-bottom-color: var(--sm-line); background: var(--sm-soft); }
.source-element.is-gf { border-bottom-color: var(--gf-line); background: var(--gf-soft); }
.source-element.is-sf { border-bottom-color: var(--sf-line); background: var(--sf-soft); }
.selection-draft {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  margin: 0 14px 12px;
  padding: 10px 12px;
  border: 1px solid var(--accent-line);
  border-radius: var(--r-md);
  background: var(--surface);
  box-shadow: var(--shadow-1);
}
.selection-copy { display: grid; min-width: 0; gap: 2px; }
.selection-copy strong { overflow: hidden; color: var(--fg-2); font-size: var(--fs-xs); font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }
.selection-label { color: var(--accent); font-size: 11px; font-weight: 600; }
.selection-offset { color: var(--faint); font-size: 10.5px; }
.selection-actions { display: flex; flex: 0 0 auto; gap: 4px; margin-left: auto; }
.source-tip { display: flex; align-items: flex-start; gap: var(--sp-2); margin: auto 14px 14px; padding: 10px; border: 1px solid var(--accent-line); border-radius: var(--r-md); background: var(--accent-soft); color: var(--accent-ink); font-size: var(--fs-xs); }
.source-tip svg { width: 16px; height: 16px; flex: 0 0 16px; margin-top: 1px; }

.element-list { display: grid; gap: 7px; max-height: 286px; overflow-y: auto; padding: 10px; border-bottom: 1px solid var(--border); }
.element-empty {
  display: flex;
  min-height: 132px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 5px;
  color: var(--muted);
  text-align: center;
}
.element-empty svg { width: 24px; height: 24px; color: var(--accent); }
.element-empty strong { color: var(--fg-2); font-size: var(--fs-sm); }
.element-empty span { font-size: var(--fs-xs); }
.validation-panel {
  margin: 10px;
  padding: 10px 12px;
  border: 1px solid var(--danger);
  border-radius: var(--r-md);
  background: var(--danger-soft);
  color: var(--danger);
  font-size: var(--fs-xs);
}
.validation-title { display: flex; align-items: center; gap: 6px; }
.validation-title svg { width: 15px; height: 15px; }
.validation-panel ul { margin: 6px 0 0 21px; list-style: disc; }
.validation-panel li + li { margin-top: 2px; }
.element-card { padding: 9px 10px; border: 1px solid var(--border); border-radius: var(--r-md); background: var(--surface); cursor: pointer; }
.element-card:hover { border-color: var(--accent-line); }
.element-card:focus-visible { outline: 2px solid var(--accent); outline-offset: 1px; }
.element-card.is-active { border-color: var(--accent); box-shadow: 0 0 0 2px var(--accent-soft); }
.element-card-top { display: flex; align-items: center; gap: 7px; margin-bottom: 5px; }
.element-no { color: var(--accent); font-family: var(--font-mono); font-size: 11px; font-weight: 700; }
.element-offset { margin-left: auto; color: var(--faint); font-size: 10px; }
.element-card p { color: var(--fg-2); font-size: var(--fs-sm); line-height: 1.55; }
.icon-action { display: grid; width: 26px; height: 26px; place-items: center; border: 0; border-radius: var(--r-sm); background: transparent; color: var(--faint); }
.icon-action:hover { background: var(--danger-soft); color: var(--danger); }
.icon-action svg { width: 15px; height: 15px; }
.type-section { display: grid; gap: var(--sp-3); padding: 14px; overflow-y: auto; }
.section-heading { display: flex; align-items: center; justify-content: space-between; gap: var(--sp-3); }
.section-heading h3 { font-family: var(--font-mono); font-size: var(--fs-xs); letter-spacing: 0.04em; text-transform: uppercase; }
.section-heading p { margin-top: 2px; color: var(--muted); font-size: var(--fs-xs); }
.step-tag { border-color: var(--warm-soft); background: var(--warm-soft); color: var(--warm-strong); }

.graph-panel { background: var(--surface-2); }
.graph-tabs { display: flex; gap: 2px; }
.graph-tabs button { padding: 4px 7px; border: 1px solid transparent; border-radius: var(--r-sm); background: transparent; color: var(--muted); font-size: var(--fs-xs); }
.graph-tabs button.is-active { border-color: var(--accent-line); background: var(--accent-soft); color: var(--accent); }
.graph-toolbar { display: flex; gap: 4px; padding: 7px 10px; border-bottom: 1px solid var(--border); background: var(--surface); }
.graph-stage-placeholder { position: relative; flex: 1; min-height: 420px; overflow: hidden; background: radial-gradient(circle at 1px 1px, var(--border) 1px, transparent 0) 0 0 / 22px 22px, var(--surface-2); }
.graph-node { position: absolute; z-index: 2; display: grid; width: 52px; height: 34px; place-items: center; border: 1.5px solid var(--fg-2); border-radius: var(--r-sm); background: var(--surface); color: var(--fg-2); font-family: var(--font-mono); font-size: var(--fs-xs); font-weight: 700; opacity: 0.55; }
.node-one { top: 92px; left: 20%; }
.node-two { top: 92px; right: 20%; }
.graph-edge { position: absolute; top: 108px; left: calc(20% + 52px); right: calc(20% + 52px); height: 1px; background: var(--border-strong); }
.graph-relation { position: absolute; z-index: 3; top: 96px; left: 50%; display: grid; width: 24px; height: 24px; place-items: center; transform: translateX(-50%); border-radius: 50%; background: var(--rel-s); color: var(--surface); font-family: var(--font-mono); font-size: 10px; opacity: 0.45; }
.graph-empty-copy { position: absolute; top: 53%; left: 50%; display: flex; width: min(260px, calc(100% - 40px)); flex-direction: column; align-items: center; gap: var(--sp-2); transform: translate(-50%, -50%); color: var(--muted); text-align: center; }
.graph-empty-copy svg { width: 30px; height: 30px; color: var(--accent); }
.graph-empty-copy strong { color: var(--fg-2); font-size: var(--fs-sm); }
.graph-empty-copy span { font-size: var(--fs-xs); }
.graph-legend { display: flex; flex-wrap: wrap; gap: var(--sp-3); padding: 9px 12px; border-top: 1px solid var(--border); background: var(--surface); color: var(--muted); font-size: 11.5px; }
.graph-legend span { display: inline-flex; align-items: center; gap: 5px; }
.legend-dot { width: 8px; height: 8px; border-radius: 50%; background: var(--rel-s); }
.legend-dot.is-attack { border: 1.5px solid var(--rel-a); background: transparent; }
.legend-dot.is-combine { background: var(--rel-j); }
button:disabled { cursor: not-allowed; }

.submit-summary {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--sp-2);
}
.submit-summary > div {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  background: var(--surface-2);
}
.submit-summary span { color: var(--muted); font-size: var(--fs-xs); }
.submit-summary strong { font-size: var(--fs-sm); }
.submit-reason-field { position: relative; margin-top: var(--sp-4); }
.submit-reason-field label { display: block; margin-bottom: 6px; color: var(--fg-2); font-size: var(--fs-sm); font-weight: 600; }
.submit-reason-field .textarea { width: 100%; min-height: 112px; padding-bottom: 26px; }
.reason-count { position: absolute; right: 10px; bottom: 7px; color: var(--faint); font-family: var(--font-mono); font-size: 10.5px; }
.submit-contract-note {
  display: flex;
  align-items: flex-start;
  gap: var(--sp-2);
  margin-top: var(--sp-3);
  padding: 9px 10px;
  border: 1px solid var(--accent-line);
  border-radius: var(--r-md);
  background: var(--accent-soft);
  color: var(--accent-ink);
  font-size: var(--fs-xs);
}
.submit-contract-note svg { width: 15px; height: 15px; flex: 0 0 15px; margin-top: 1px; }

@media (max-width: 1280px) {
  .workbench-top { align-items: flex-start; flex-wrap: wrap; }
  .workbench-actions { width: 100%; justify-content: flex-start; margin-left: 0; }
  .workbench-grid { grid-template-columns: minmax(280px, 0.9fr) minmax(400px, 1.1fr); }
  .graph-panel { grid-column: 1 / -1; min-height: 520px; border-top: 1px solid var(--border); border-left: 0 !important; }
}

@media (max-width: 820px) {
  .workbench-actions { overflow-x: auto; padding-bottom: 2px; }
  .save-state { display: none; }
  .workbench-grid { display: flex; min-height: 0; flex-direction: column; overflow: visible; }
  .workbench-panel + .workbench-panel { border-top: 1px solid var(--border); border-left: 0; }
  .source-panel,
  .element-panel,
  .graph-panel { min-height: 520px; }
  .graph-stage-placeholder { min-height: 360px; }
}

@media (max-width: 560px) {
  .workbench-top { padding: 12px; }
  .task-badges { width: 100%; }
  .panel-head { align-items: flex-start; }
  .document-text { padding-inline: 14px; }
  .selection-draft { align-items: stretch; flex-direction: column; }
  .selection-actions { width: 100%; justify-content: flex-end; }
  .submit-summary { grid-template-columns: 1fr; }
}
</style>
