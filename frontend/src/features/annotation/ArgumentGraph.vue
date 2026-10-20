<script setup lang="ts">
// 论证图示（SVG）。画法依据 mock-data/base/relation-definitions.json 的 diagramRule：
//   S 实心圆 ● 有向、A 空心圆 ○ 有向、J/M 带“+”圆（J 无向、M 有向）、I 用矩形框内“/”不分圆节点。
// 几何与交互移植原型 assets/graph.js（拖动 / 滚轮缩放 / 平移 / fit），
// 样式全部复用 app.css §11 的 .g-*，不新造类名、不加依赖（不使用 AntV X6）。
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import type { GraphEdge, GraphNode, GraphNodePosition } from '@/types/graph'
import {
  PROP_H,
  PROP_W,
  REL_R,
  buildRenderEdges,
  buildRenderNodes,
  edgeEndpoints,
  graphBounds,
  nodeSize,
  type RenderEdge,
  type RenderNode
} from '@/shared/graphGeometry'
import {
  EXPORT_PAD,
  SVG_NS,
  backgroundAttrs,
  exportViewBox,
  inlineComputedStyles,
  rasterSize,
  rasterize,
  stageBackground,
  stripUiArtifacts,
  type ExportOptions
} from '@/shared/graphExport'
import { hasPlusSign, relClass } from '@/shared/relationMeta'

const MIN_SCALE = 0.35
const MAX_SCALE = 2.6
const FIT_PAD = 44

const props = defineProps<{
  nodes: GraphNode[]
  edges: GraphEdge[]
  /** GR01 返回的已保存坐标；为空或不完整时自动布局 */
  positions?: GraphNodePosition[]
  activeId?: string | null
  /** 是否允许拖动节点（拖动只改坐标，不改关系） */
  editable?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:activeId', value: string | null): void
  (e: 'node-click', node: GraphNode): void
  (e: 'positions-change', positions: GraphNodePosition[]): void
}>()

const hostEl = ref<HTMLDivElement | null>(null)
const svgEl = ref<SVGSVGElement | null>(null)

const rendered = ref<RenderNode[]>([])
const renderedEdges = ref<RenderEdge[]>([])

const scale = ref(1)
const tx = ref(FIT_PAD)
const ty = ref(FIT_PAD)
const panning = ref(false)

const dragMode = ref<'pan' | 'node' | null>(null)
const dragNodeId = ref<string | null>(null)
const lastPoint = ref({ x: 0, y: 0 })
const moved = ref(false)

const nodeMap = computed(() => {
  const map = new Map<string, RenderNode>()
  rendered.value.forEach((node) => map.set(node.id, node))
  return map
})

const edgeLines = computed(() => {
  const map = nodeMap.value
  const lines: Array<{
    id: string
    rel: string
    arrow: boolean
    dashed: boolean
    x1: number
    y1: number
    x2: number
    y2: number
  }> = []
  renderedEdges.value.forEach((edge) => {
    const a = map.get(edge.a)
    const b = map.get(edge.b)
    if (!a || !b) return
    const ends = edgeEndpoints(a, b)
    lines.push({
      id: edge.id,
      rel: edge.rel,
      arrow: edge.arrow,
      dashed: edge.dashed,
      ...ends
    })
  })
  return lines
})

const transform = computed(
  () => `translate(${tx.value} ${ty.value}) scale(${scale.value})`
)

/** 节点键：节点集合变化（重新拉取 / 切换图示）时才重新取景，拖动与保存布局不触发。 */
const nodeKey = computed(() => props.nodes.map((n) => n.id).join('|'))

function sizeOf(node: RenderNode): { hw: number; hh: number } {
  return nodeSize(node)
}

function edgeClass(rel: string): Record<string, boolean> {
  // 样式只区分 .g-edge（S/A/M）与 .g-edge.j / .g-edge.i；J 与 I 都无箭头
  return { j: rel === 'J', i: rel === 'I' }
}

function rebuild(): void {
  rendered.value = buildRenderNodes(props.nodes, props.edges, props.positions)
  renderedEdges.value = buildRenderEdges(props.edges)
}

function clampScale(value: number): number {
  return Math.min(MAX_SCALE, Math.max(MIN_SCALE, value))
}

function fit(): void {
  const host = hostEl.value
  if (!host || rendered.value.length === 0) return
  const width = host.clientWidth
  const height = host.clientHeight
  if (width === 0 || height === 0) return
  const bounds = graphBounds(rendered.value)
  const spanX = Math.max(1, bounds.maxX - bounds.minX)
  const spanY = Math.max(1, bounds.maxY - bounds.minY)
  const k = clampScale(
    Math.min((width - FIT_PAD * 2) / spanX, (height - FIT_PAD * 2) / spanY, 1.4)
  )
  scale.value = k
  tx.value = (width - spanX * k) / 2 - bounds.minX * k
  ty.value = (height - spanY * k) / 2 - bounds.minY * k
}

function zoomBy(factor: number): void {
  const host = hostEl.value
  if (!host) return
  zoomAt(host.clientWidth / 2, host.clientHeight / 2, factor)
}

function zoomAt(px: number, py: number, factor: number): void {
  const next = clampScale(scale.value * factor)
  if (next === scale.value) return
  tx.value = px - (px - tx.value) * (next / scale.value)
  ty.value = py - (py - ty.value) * (next / scale.value)
  scale.value = next
}

function reset(): void {
  scale.value = 1
  tx.value = FIT_PAD
  ty.value = FIT_PAD
}

function onWheel(event: WheelEvent): void {
  const host = hostEl.value
  if (!host) return
  const rect = host.getBoundingClientRect()
  zoomAt(event.clientX - rect.left, event.clientY - rect.top, event.deltaY < 0 ? 1.12 : 0.89)
}

function onStagePointerDown(event: PointerEvent): void {
  if (event.button !== 0) return
  panning.value = true
  dragMode.value = 'pan'
  moved.value = false
  lastPoint.value = { x: event.clientX, y: event.clientY }
  svgEl.value?.setPointerCapture(event.pointerId)
}

function onNodePointerDown(event: PointerEvent, node: RenderNode): void {
  if (event.button !== 0 || props.editable === false) return
  dragMode.value = 'node'
  dragNodeId.value = node.id
  moved.value = false
  lastPoint.value = { x: event.clientX, y: event.clientY }
  svgEl.value?.setPointerCapture(event.pointerId)
}

function onPointerMove(event: PointerEvent): void {
  if (!dragMode.value) return
  const dx = event.clientX - lastPoint.value.x
  const dy = event.clientY - lastPoint.value.y
  if (dx === 0 && dy === 0) return
  moved.value = true
  lastPoint.value = { x: event.clientX, y: event.clientY }
  if (dragMode.value === 'pan') {
    tx.value += dx
    ty.value += dy
    return
  }
  const node = dragNodeId.value ? nodeMap.value.get(dragNodeId.value) : undefined
  if (!node) return
  node.x += dx / scale.value
  node.y += dy / scale.value
}

function onPointerUp(event: PointerEvent): void {
  const wasNodeDrag = dragMode.value === 'node' && moved.value
  panning.value = false
  dragMode.value = null
  if (svgEl.value?.hasPointerCapture(event.pointerId)) {
    svgEl.value.releasePointerCapture(event.pointerId)
  }
  if (wasNodeDrag) {
    emit('positions-change', currentPositions())
  }
  dragNodeId.value = null
}

/** 当前绘图坐标 → GR03 的 nodePositions（关系节点同样有坐标）。 */
function currentPositions(): GraphNodePosition[] {
  const kindOf = new Map(props.nodes.map((n) => [n.id, n.kind]))
  return rendered.value
    .filter((node) => kindOf.has(node.id))
    .map((node) => ({
      targetKind: kindOf.get(node.id) as GraphNodePosition['targetKind'],
      targetId: node.id,
      x: Math.round(node.x),
      y: Math.round(node.y)
    }))
}

function onNodeClick(node: RenderNode): void {
  // 刚拖拽过就不算点击，避免松开鼠标时误改选中项
  if (moved.value) {
    moved.value = false
    return
  }
  const source = props.nodes.find((n) => n.id === node.id)
  emit('update:activeId', node.id)
  if (source) emit('node-click', source)
}

/**
 * 导出整图为自包含 SVG 字符串（分工.md §八.5）。
 *
 * 与屏幕上那张图的三个差别，都是有意的：
 *   1. 导出整图 —— 用内容包围盒做 viewBox 并把内容组的 transform 归零，
 *      所以当前缩放到 2.6 倍或平移出屏，导出的仍是完整图示；
 *   2. 自包含 —— 内联每个图元的计算样式（.g-* 用的是 var(--rel-*)，
 *      单独一个 SVG 文件没有 app.css，不内联就是一张只有黑线的残图）；
 *   3. 去掉交互态 —— 选中描边（.halo）与 is-active 是当前会话的状态，不是图的内容。
 *
 * 注意顺序：内联样式必须排在去交互态之前，因为它是按 childNodes 并行下走的，
 * 克隆树一旦删了 .halo 就不再与源树同构。
 */
function exportSvg(options: ExportOptions = {}): string {
  const svg = svgEl.value
  if (!svg || rendered.value.length === 0) return ''
  const clone = svg.cloneNode(true) as SVGSVGElement

  inlineComputedStyles(svg, clone, window)
  stripUiArtifacts(clone)

  const view = contentView(options.padding)
  clone.setAttribute('xmlns', SVG_NS)
  clone.setAttribute('viewBox', view.viewBox)
  clone.setAttribute('width', String(view.width))
  clone.setAttribute('height', String(view.height))

  // 内容组的 transform 归零：整图由 viewBox 定位，与当前平移/缩放无关
  const content = clone.querySelector('g[transform]')
  content?.setAttribute('transform', 'translate(0 0)')

  const background = backgroundAttrs(view, options.background)
  if (background) {
    const rect = document.createElementNS(SVG_NS, 'rect')
    Object.entries(background).forEach(([name, value]) => rect.setAttribute(name, value))
    clone.insertBefore(rect, clone.firstChild)
  }

  return '<?xml version="1.0" encoding="UTF-8"?>\n' + new XMLSerializer().serializeToString(clone)
}

/** PNG：同一份 SVG 交给 canvas 光栅化。底色默认取绘图区底色（透明图在文档里看不清）。 */
async function exportPng(options: ExportOptions = {}): Promise<Blob> {
  const svg = exportSvg(options)
  if (!svg) throw new Error('没有可导出的图示')
  const view = contentView(options.padding)
  const background =
    options.background === undefined ? stageBackground(hostEl.value, window) : options.background
  const size = rasterSize(view.width, view.height, options.scale ?? 2, options.maxPixels)
  return rasterize(svg, size, background)
}

/** 内容包围盒 + 留白。导出与光栅化共用，避免两处各算一遍尺寸。 */
function contentView(padding?: number): ReturnType<typeof exportViewBox> {
  return exportViewBox(graphBounds(rendered.value), padding ?? EXPORT_PAD)
}

watch([() => props.nodes, () => props.edges, () => props.positions], rebuild, {
  immediate: true
})

watch(
  nodeKey,
  () => {
    nextTick(fit)
  },
  { immediate: true }
)

onMounted(() => {
  nextTick(fit)
})

defineExpose({ fit, zoomBy, reset, exportSvg, exportPng, currentPositions })
</script>

<template>
  <div ref="hostEl" class="graph-stage">
    <svg
      ref="svgEl"
      :class="{ 'is-panning': panning }"
      @wheel.prevent="onWheel"
      @pointerdown="onStagePointerDown"
      @pointermove="onPointerMove"
      @pointerup="onPointerUp"
      @pointercancel="onPointerUp"
    >
      <defs>
        <!-- S/A/M 的有向边共用箭头；J/I 连线本身无箭头（分则：J 连线无箭头、I 虚线） -->
        <marker
          id="agArrow"
          viewBox="0 0 10 10"
          refX="9"
          refY="5"
          markerWidth="7"
          markerHeight="7"
          orient="auto-start-reverse"
        >
          <path class="g-arrow" d="M 0 0 L 10 5 L 0 10 z" />
        </marker>
      </defs>

      <g :transform="transform">
        <line
          v-for="line in edgeLines"
          :key="line.id"
          class="g-edge"
          :class="edgeClass(line.rel)"
          :x1="line.x1"
          :y1="line.y1"
          :x2="line.x2"
          :y2="line.y2"
          :marker-end="line.arrow ? 'url(#agArrow)' : undefined"
        />

        <g
          v-for="node in rendered"
          :key="node.id"
          class="g-node"
          :class="{ 'is-active': node.id === props.activeId }"
          :transform="`translate(${node.x} ${node.y})`"
          @pointerdown.stop="onNodePointerDown($event, node)"
          @click.stop="onNodeClick(node)"
        >
          <!-- halo 仅用于选中态描边，不参与命中以外的绘制 -->
          <circle v-if="node.kind === 'rel'" class="halo" :r="REL_R + 5" />
          <rect
            v-else
            class="halo"
            :x="-sizeOf(node).hw - 5"
            :y="-sizeOf(node).hh - 5"
            :width="sizeOf(node).hw * 2 + 10"
            :height="sizeOf(node).hh * 2 + 10"
          />

          <template v-if="node.kind === 'prop'">
            <rect
              class="g-prop"
              :x="-PROP_W / 2"
              :y="-PROP_H / 2"
              :width="PROP_W"
              :height="PROP_H"
              rx="5"
            />
            <text class="g-prop-lbl" x="0" y="0">{{ node.label }}</text>
          </template>

          <template v-else-if="node.kind === 'ident'">
            <rect
              class="g-ident"
              :x="-sizeOf(node).hw"
              :y="-sizeOf(node).hh"
              :width="sizeOf(node).hw * 2"
              :height="sizeOf(node).hh * 2"
              rx="3"
            />
            <text class="g-ident-lbl" x="0" y="0">{{ node.label }}</text>
          </template>

          <template v-else>
            <circle class="g-rel" :class="relClass(node.rel)" :r="REL_R" />
            <text
              v-if="hasPlusSign(node.rel)"
              class="g-rel-lbl"
              :class="`plus-${relClass(node.rel)}`"
              x="0"
              y="0"
            >
              +
            </text>
          </template>
        </g>
      </g>
    </svg>
  </div>
</template>
