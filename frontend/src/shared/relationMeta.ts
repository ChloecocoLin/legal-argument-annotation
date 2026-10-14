// 关系类型的视觉与文案元数据。
// 规则来源：数据模型 §1.1 参与矩阵、§2.6 字典，以及 mock-data/base/relation-definitions.json 的
// diagramRule / directionRule（逐字引用）。业务定义仍以 G07 返回的 RelationDefinition 为准，
// 这里只提供颜色、图元符号与界面提示。
import type { RelationType } from '@/types/common'

export interface RelationMetaItem {
  code: RelationType
  name: string
  directed: boolean
  /** 图元用色（tokens.css 的 --rel-*） */
  color: string
  /** 关系节点符号：实心圆 / 空心圆 / 带 + 圆 / 框内斜杠 */
  glyph: string
  /** SOURCE 槽位的中文名（有向关系用；无向关系为空串） */
  sourceLabel: string
  /** TARGET 槽位的中文名（有向关系用） */
  targetLabel: string
  /** 参与项构成的一句话说明 */
  participants: string
  /** 建立顺序说明（工作台每次只建一种关系，用于提示该先做什么） */
  directionRule: string
  /** 图元画法（图例与图示审查共用） */
  diagramRule: string
}

export const REL_META: Record<RelationType, RelationMetaItem> = {
  S: {
    code: 'S',
    name: '支持关系',
    directed: true,
    color: 'var(--rel-s)',
    glyph: '●',
    sourceLabel: '支持者',
    targetLabel: '被支持命题',
    participants: '支持者 1 · 被支持命题 1',
    directionRule: '从支持者指向被支持命题。',
    diagramRule:
      '矩形命题节点之间经实心圆关系节点形成有向边，箭头指向被支持命题。'
  },
  A: {
    code: 'A',
    name: '反对关系',
    directed: true,
    color: 'var(--rel-a)',
    glyph: '○',
    sourceLabel: '反对者',
    targetLabel: '被反对的命题或关系',
    participants: '反对者 1 · 被反对对象 1',
    directionRule: '从反对者指向被反对的命题或关系节点。',
    diagramRule: '使用空心圆关系节点和有向边；攻击支持关系时箭头指向该支持关系节点。'
  },
  J: {
    code: 'J',
    name: '组合关系',
    directed: false,
    color: 'var(--rel-j)',
    glyph: '+',
    sourceLabel: '',
    targetLabel: '',
    participants: '成员 ≥ 2（命题或已成立的关系整体）',
    directionRule: '连线无箭头。',
    diagramRule: '带“+”圆节点连接各成员，连线无箭头；嵌套按指南从内到外构造。'
  },
  M: {
    code: 'M',
    name: '匹配关系',
    directed: true,
    color: 'var(--rel-m)',
    glyph: '+',
    sourceLabel: '个别判断',
    targetLabel: '被匹配的一般判断',
    participants: '来源 1（个别判断或 J 关系整体）· 目标 1（一般判断）',
    directionRule: '箭头指向被匹配的一般判断。',
    diagramRule: '带“+”圆节点的有向结构，箭头指向被匹配的一般判断；M(J(...),...) 按指南特例图示。'
  },
  I: {
    code: 'I',
    name: '同一关系',
    directed: false,
    color: 'var(--rel-i)',
    glyph: '/',
    sourceLabel: '',
    targetLabel: '',
    participants: '成员 ≥ 2，且只能是命题',
    directionRule: '仅表示“同一”，不产生支持传递。',
    diagramRule: '在同一矩形框中用“/”分隔命题编号，不另画圆形关系节点。'
  }
}

/** 标注工作台的建立顺序（分工.md §四）：S → J → M → A → I → 嵌套检查 */
export const REL_ANNOTATION_ORDER: RelationType[] = ['S', 'J', 'M', 'A', 'I']

export function relationMeta(code?: string | null): RelationMetaItem | null {
  if (!code) return null
  return REL_META[code as RelationType] ?? null
}

/** 图元样式类后缀：.g-rel.s / .a / .j / .m；I 走 .g-ident，无圆节点类 */
export function relClass(code?: string | null): string {
  return (code || '').toLowerCase()
}

/** 关系节点是否画成圆（I 例外：用矩形框 + “/”） */
export function isCircleNode(code?: string | null): boolean {
  return code !== 'I'
}

/** 关系节点内是否画“+”号（J/M） */
export function hasPlusSign(code?: string | null): boolean {
  return code === 'J' || code === 'M'
}
