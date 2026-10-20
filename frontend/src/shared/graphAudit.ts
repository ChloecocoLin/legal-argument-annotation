// 图示审查（分工.md §四.6）的审查类别与前端判定项。
//
// 分工原文列出七类：遗漏关系、错误方向、J/M 混用、非法嵌套、孤立命题、图元错误、
// 多个论证结构识别。它们**不是同一层的东西**，页面必须如实区分判定方：
//   BACKEND  —— 由关系校验器/构图逻辑判定，随 GR02 的 issues 下发（后端是唯一事实来源，
//               前端不再重算一遍，避免两处各写一份规则）；
//   FRONTEND —— 后端判不了：图元最终画成什么样由渲染层决定，只能拿 GR01 的
//               nodes/edges 自查「画法契约」有没有被破坏；
//   INFO     —— 不是缺陷，只是需要人知道（多个独立论证图要分别出图）。
//
// 后端分类见 com.argument.relation.rules.AuditCategory，两端的 id 必须一一对应。
import type { GraphEdge, GraphNode } from '@/types/graph'
import { relationMeta } from './relationMeta'

export type AuditDecision = 'BACKEND' | 'FRONTEND' | 'INFO'

export interface AuditDimension {
  /** 与后端 AuditCategory 同名 */
  id: string
  /** 分工.md §四.6 的中文名 */
  label: string
  decision: AuditDecision
  /** 这一项在查什么（写给裁定者看） */
  hint: string
}

/** 归不上七类的问题（端点、快照、ordinal……）落在这里，界面显示为「其他结构问题」。 */
export const OTHER_CATEGORY = 'STRUCTURE'

export const AUDIT_DIMENSIONS: AuditDimension[] = [
  {
    id: 'MISSING_RELATION',
    label: '遗漏关系',
    decision: 'BACKEND',
    hint: '图示未覆盖本快照全部命题与关系，只能算局部视图'
  },
  {
    id: 'WRONG_DIRECTION',
    label: '错误方向',
    decision: 'BACKEND',
    hint: '有向关系（S/A/M）缺唯一的 SOURCE 或 TARGET'
  },
  {
    id: 'JM_CONFUSION',
    label: 'J/M 混用',
    decision: 'BACKEND',
    hint: '无向关系给了方向，或 J、M 的角色与成员规则被违反'
  },
  {
    id: 'ILLEGAL_NESTING',
    label: '非法嵌套',
    decision: 'BACKEND',
    hint: '关系整体作为参与项时违反参与矩阵，或嵌套成环'
  },
  {
    id: 'ISOLATED_PROPOSITION',
    label: '孤立命题',
    decision: 'BACKEND',
    hint: '命题未参与任何关系'
  },
  {
    id: 'GLYPH_ERROR',
    label: '图元错误',
    decision: 'FRONTEND',
    hint: '图元的箭头、虚线、编号与关系类型规定的画法不一致'
  },
  {
    id: 'MULTIPLE_STRUCTURES',
    label: '多个论证结构识别',
    decision: 'INFO',
    hint: '连通分量多于一个，应分别出图'
  }
]

export const OTHER_DIMENSION: AuditDimension = {
  id: OTHER_CATEGORY,
  label: '其他结构问题',
  decision: 'BACKEND',
  hint: '端点不存在、不属于当前快照、ordinal 重复等不属于上述七类的缺陷'
}

/** 只要求带 category 字段，好让后端 issues 与界面归一化后的审查结果共用同一套归组。 */
export interface Categorized {
  category?: string | null
}

/** 问题所属类别；后端未分类的统一归「其他结构问题」。 */
export function categoryOf(item: Categorized): string {
  const code = item.category
  return code && code.length > 0 ? code : OTHER_CATEGORY
}

/** 按审查类别归组（含空组），供界面逐条画清单。 */
export function groupIssues<T extends Categorized>(items: T[]): Map<string, T[]> {
  const groups = new Map<string, T[]>()
  for (const dimension of [...AUDIT_DIMENSIONS, OTHER_DIMENSION]) {
    groups.set(dimension.id, [])
  }
  for (const item of items) {
    const bucket = groups.get(categoryOf(item)) ?? groups.get(OTHER_CATEGORY)!
    bucket.push(item)
  }
  return groups
}

export interface GlyphFinding {
  /** 出问题的图元（节点或连线的 id） */
  targetId: string
  message: string
}

/**
 * 图元错误（分工.md §四.6）——前端判定。
 *
 * 后端只产出节点与连线（含 arrow/dashed），最终画成什么由渲染层决定，所以这一类
 * 只能在渲染前用同一份数据自查「画法契约」：箭头与关系类型的有向性是否一致、
 * 虚线是否只用在 I、连线两端与对应的关系节点是否都在图里、命题是否都有展示编号、
 * 关系类型是否有图元定义。返回空数组即图元全部合规。
 */
export function checkGlyphs(nodes: GraphNode[], edges: GraphEdge[]): GlyphFinding[] {
  const findings: GlyphFinding[] = []
  const byId = new Map(nodes.map((node) => [node.id, node]))

  for (const edge of edges) {
    const meta = relationMeta(edge.relationCode)
    if (!meta) {
      findings.push({
        targetId: edge.id,
        message: `连线引用了未知关系类型 ${edge.relationCode}，没有对应图元`
      })
      continue
    }
    if (edge.arrow !== meta.directed) {
      findings.push({
        targetId: edge.id,
        message:
          `${edge.relationCode} 的连线箭头与规定不符：` +
          `应为${meta.directed ? '有向带箭头' : '无向不带箭头'}`
      })
    }
    const shouldDash = edge.relationCode === 'I'
    if (edge.dashed !== shouldDash) {
      findings.push({
        targetId: edge.id,
        message: shouldDash
          ? 'I 的连线应为虚线'
          : `${edge.relationCode} 的连线不应画成虚线`
      })
    }
    for (const endpoint of [edge.source, edge.target]) {
      if (!byId.has(endpoint)) {
        findings.push({
          targetId: edge.id,
          message: `连线端点 ${endpoint} 不在节点集合中，图元画不出来`
        })
      }
    }
    if (!byId.has(edge.relationId)) {
      findings.push({
        targetId: edge.id,
        message: `连线找不到对应的关系节点 ${edge.relationId}`
      })
    }
  }

  for (const node of nodes) {
    if (node.kind === 'RELATION') {
      const meta = relationMeta(node.relationCode)
      if (!meta) {
        findings.push({
          targetId: node.id,
          message: `关系节点引用了未知关系类型 ${node.relationCode}，没有对应图元`
        })
        continue
      }
      if (node.directed !== null && node.directed !== meta.directed) {
        findings.push({
          targetId: node.id,
          message: `${node.relationCode} 关系节点的有向性与规定不符`
        })
      }
    } else if (!node.displayNo) {
      findings.push({
        targetId: node.id,
        message: '命题节点缺少展示编号，图上无法标注'
      })
    }
  }

  return findings
}
