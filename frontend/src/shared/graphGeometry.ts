// 论证图几何工具：从逻辑图（节点 + 边）算出绘图所需的节点与端点。
// 几何常量与画法逐条对应原型 法律论证标注系统_可运行原型/assets/graph.js，
// 复用样式 app.css §11 的 .g-* 与 tokens.css 的 --rel-*，不引入新依赖。
import type { GraphEdge, GraphNode, GraphNodePosition } from '@/types/graph'

export const PROP_W = 58
export const PROP_H = 38
export const REL_R = 12

export type RenderKind = 'prop' | 'rel' | 'ident'

// 绘图节点：命题矩形 / 关系圆 / I 的编号框
export interface RenderNode {
  id: string
  kind: RenderKind
  x: number
  y: number
  /** prop：展示编号（P1…）；rel：关系代码；ident：成员编号以“/”相连 */
  label: string
  /** 关系代码（rel/ident 有值），用于取色与图元样式类 */
  rel: string | null
}

export interface RenderEdge {
  id: string
  a: string
  b: string
  arrow: boolean
  dashed: boolean
  rel: string
}

export interface Point {
  x: number
  y: number
}

export interface Bounds {
  minX: number
  minY: number
  maxX: number
  maxY: number
}

/** 节点半宽/半高。命题固定 29×19，关系圆半径 12，I 编号框按文字长度伸缩。 */
export function nodeSize(node: { kind: RenderKind; label: string }): {
  hw: number
  hh: number
} {
  if (node.kind === 'rel') return { hw: REL_R, hh: REL_R }
  if (node.kind === 'ident') {
    return { hw: Math.max(26, 12 + node.label.length * 4.2), hh: 15 }
  }
  return { hw: PROP_W / 2, hh: PROP_H / 2 }
}

/** 从节点中心朝 other 的射线上，节点边界的交点（矩形取两轴约束的较小者，圆取半径）。 */
export function boundaryPointTowards(node: RenderNode, other: Point): Point {
  const dx = other.x - node.x
  const dy = other.y - node.y
  const len = Math.hypot(dx, dy) || 1
  const sx = dx / len
  const sy = dy / len
  const size = nodeSize(node)
  const t =
    node.kind === 'rel'
      ? size.hw
      : Math.min(
          size.hw / Math.max(Math.abs(sx), 1e-6),
          size.hh / Math.max(Math.abs(sy), 1e-6)
        )
  return { x: node.x + sx * t, y: node.y + sy * t }
}

/** 一条边的两个端点：分别落在各自节点的边界上，避免线穿过图元。 */
export function edgeEndpoints(
  a: RenderNode,
  b: RenderNode
): { x1: number; y1: number; x2: number; y2: number } {
  const p1 = boundaryPointTowards(a, b)
  const p2 = boundaryPointTowards(b, a)
  return { x1: p1.x, y1: p1.y, x2: p2.x, y2: p2.y }
}

function displayNoValue(label: string | null | undefined): number {
  const matched = /(\d+)/.exec(label || '')
  return matched ? Number(matched[1]) : -1
}

/**
 * 逻辑图 → 绘图节点。
 * - ELEMENT → 命题矩形，框内只显示展示编号；
 * - RELATION 且 code=I → 编号框，内容为成员编号以“/”分隔（无成员时退回后端生成的表达式）；
 * - 其余 RELATION → 关系圆（S 实心、A 空心、J/M 带“+”）。
 */
export function buildRenderNodes(
  nodes: GraphNode[],
  edges: GraphEdge[],
  positions?: GraphNodePosition[]
): RenderNode[] {
  const labelOf = new Map<string, string>()
  nodes.forEach((node) => {
    labelOf.set(node.id, node.displayNo ?? node.labelCode ?? node.id)
  })

  const rendered = nodes.map<RenderNode>((node) => {
    if (node.kind === 'ELEMENT') {
      return {
        id: node.id,
        kind: 'prop',
        x: 0,
        y: 0,
        label: node.displayNo ?? '?',
        rel: null
      }
    }
    const code = node.relationCode ?? ''
    if (code === 'I') {
      const members = edges
        .filter((e) => e.relationId === node.id)
        .map((e) => e.source)
        .filter((id) => labelOf.has(id))
        .sort((a, b) => displayNoValue(labelOf.get(a)) - displayNoValue(labelOf.get(b)))
      const joined = members.map((id) => labelOf.get(id)).join('/')
      const fallback = (node.expression ?? '')
        .replace(/^I\(/, '')
        .replace(/\)$/, '')
        .replace(/\s*,\s*/g, '/')
      return {
        id: node.id,
        kind: 'ident',
        x: 0,
        y: 0,
        label: joined || fallback || 'I',
        rel: code
      }
    }
    return { id: node.id, kind: 'rel', x: 0, y: 0, label: code, rel: code }
  })

  const renderEdges = buildRenderEdges(edges)
  // 后端保存过的布局（GraphLayout）完整覆盖时优先使用；否则整图自动布局，
  // 避免出现“一半用旧坐标、一半用新排布”的错位。
  if (applyPositions(rendered, positions) < rendered.length) {
    autoLayout(rendered, renderEdges)
  }
  return rendered
}

/**
 * 套用后端保存的坐标。返回成功套用的节点数，调用方据此判断布局是否完整。
 * 改变坐标不更改关系、也不产生新的标注版本（契约）。
 */
export function applyPositions(
  nodes: RenderNode[],
  positions?: GraphNodePosition[]
): number {
  if (!positions || positions.length === 0) return 0
  const index = new Map<string, GraphNodePosition>()
  positions.forEach((p) => index.set(p.targetId, p))
  let placed = 0
  nodes.forEach((node) => {
    const p = index.get(node.id)
    if (p && Number.isFinite(p.x) && Number.isFinite(p.y)) {
      node.x = p.x
      node.y = p.y
      placed += 1
    }
  })
  return placed
}

/** 逻辑边 → 绘图边（箭头/虚线由后端在 GraphEdge 上给出）。 */
export function buildRenderEdges(edges: GraphEdge[]): RenderEdge[] {
  return edges.map((e) => ({
    id: e.id,
    a: e.source,
    b: e.target,
    arrow: e.arrow,
    dashed: e.dashed,
    rel: e.relationCode
  }))
}

const COL_W = PROP_W + 96
const ROW_H = PROP_H + 56
const GROUP_GAP = 80
const MARGIN = 60

/**
 * 自动布局（只在后端尚无布局时使用）。
 * 按连通分量分组，组内从入度为 0 的节点做分层 BFS：层 → 列，层内按展示编号排序成行。
 */
export function autoLayout(nodes: RenderNode[], edges: RenderEdge[]): void {
  if (nodes.length === 0) return
  const index = new Map<string, RenderNode>()
  nodes.forEach((n) => index.set(n.id, n))

  let offsetX = MARGIN
  componentsOf(nodes, edges).forEach((group) => {
    const inGroup = new Set(group)
    const next = new Map<string, string[]>()
    const indegree = new Map<string, number>()
    group.forEach((id) => indegree.set(id, 0))
    edges.forEach((e) => {
      if (!inGroup.has(e.a) || !inGroup.has(e.b)) return
      const list = next.get(e.a) ?? []
      list.push(e.b)
      next.set(e.a, list)
      indegree.set(e.b, (indegree.get(e.b) ?? 0) + 1)
    })

    const roots = group.filter((id) => (indegree.get(id) ?? 0) === 0)
    const depth = new Map<string, number>()
    const queue: string[] = []
    ;(roots.length ? roots : [group[0]]).forEach((id) => {
      depth.set(id, 0)
      queue.push(id)
    })
    while (queue.length > 0) {
      const current = queue.shift() as string
      const level = depth.get(current) as number
      for (const nb of next.get(current) ?? []) {
        if (depth.has(nb)) continue
        depth.set(nb, level + 1)
        queue.push(nb)
      }
    }
    // 环上的节点不会被 BFS 触达，统一挂到最后一列右侧
    const maxDepth = Math.max(0, ...Array.from(depth.values()))
    group.forEach((id) => {
      if (!depth.has(id)) depth.set(id, maxDepth + 1)
    })

    const levels = new Map<number, string[]>()
    group.forEach((id) => {
      const d = depth.get(id) as number
      const list = levels.get(d) ?? []
      list.push(id)
      levels.set(d, list)
    })
    const columnKeys = Array.from(levels.keys()).sort((a, b) => a - b)
    let maxRows = 1
    columnKeys.forEach((d) => {
      const list = levels.get(d) as string[]
      list.sort((a, b) => {
        const diff = displayNoValue(index.get(a)?.label) - displayNoValue(index.get(b)?.label)
        return diff !== 0 ? diff : a.localeCompare(b)
      })
      maxRows = Math.max(maxRows, list.length)
    })

    const height = maxRows * ROW_H
    columnKeys.forEach((d) => {
      const list = levels.get(d) as string[]
      const top = MARGIN + (height - list.length * ROW_H) / 2
      list.forEach((id, row) => {
        const node = index.get(id) as RenderNode
        node.x = offsetX + d * COL_W
        node.y = top + row * ROW_H
      })
    })
    offsetX += Math.max(1, columnKeys.length) * COL_W + GROUP_GAP
  })
}

/**
 * 连通分量识别（节点 id 分组）。用于：
 * 1) 自动布局分组；2) 工作台“图示一 / 图示二”切换，体现多个独立论证图。
 * 单个无边的命题自成一组（孤立命题）。
 */
export function componentsOf(
  nodes: { id: string; label?: string }[],
  edges: { a: string; b: string }[]
): string[][] {
  const parent = new Map<string, string>()
  const find = (x: string): string => {
    let root = x
    while (parent.get(root) !== root) root = parent.get(root) as string
    let cursor = x
    while (parent.get(cursor) !== root) {
      const prev = parent.get(cursor) as string
      parent.set(cursor, root)
      cursor = prev
    }
    return root
  }
  const union = (a: string, b: string) => {
    const ra = find(a)
    const rb = find(b)
    if (ra !== rb) parent.set(ra, rb)
  }

  nodes.forEach((n) => parent.set(n.id, n.id))
  edges.forEach((e) => {
    if (!parent.has(e.a) || !parent.has(e.b)) return
    union(e.a, e.b)
  })

  const grouped = new Map<string, string[]>()
  nodes.forEach((n) => {
    const root = find(n.id)
    const list = grouped.get(root) ?? []
    list.push(n.id)
    grouped.set(root, list)
  })

  const labelOf = new Map<string, string>()
  nodes.forEach((n) => labelOf.set(n.id, n.label ?? n.id))
  const score = (ids: string[]) =>
    Math.min(...ids.map((id) => displayNoValue(labelOf.get(id))).filter((v) => v >= 0), Infinity)

  return Array.from(grouped.values()).sort((a, b) => {
    const sa = score(a)
    const sb = score(b)
    if (sa !== sb) return sa - sb
    return (a[0] ?? '').localeCompare(b[0] ?? '')
  })
}

/** 绘图节点的包围盒（含图元自身尺寸），供 fit() 使用。 */
export function graphBounds(nodes: RenderNode[]): Bounds {
  if (nodes.length === 0) return { minX: 0, minY: 0, maxX: 0, maxY: 0 }
  let minX = Infinity
  let minY = Infinity
  let maxX = -Infinity
  let maxY = -Infinity
  nodes.forEach((node) => {
    const { hw, hh } = nodeSize(node)
    minX = Math.min(minX, node.x - hw)
    minY = Math.min(minY, node.y - hh)
    maxX = Math.max(maxX, node.x + hw)
    maxY = Math.max(maxY, node.y + hh)
  })
  return { minX, minY, maxX, maxY }
}
