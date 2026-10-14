import type { LabelDefinition } from '@/types/guide'

export type ElementTypeGroupKey =
  | 'NON_PROPOSITION'
  | 'GM'
  | 'SM'
  | 'GF'
  | 'SF'
  | 'OTHER_PROPOSITION'

export interface ElementTypeGroup {
  key: ElementTypeGroupKey
  title: string
  description: string
  labels: LabelDefinition[]
}

interface ElementTypeGroupMeta {
  key: ElementTypeGroupKey
  title: string
  description: string
}

const GROUPS: ElementTypeGroupMeta[] = [
  {
    key: 'NON_PROPOSITION',
    title: '非命题要素',
    description: '不参与命题关系构建的文本成分'
  },
  {
    key: 'GM',
    title: '一般规范判断 GM',
    description: '法律条文、法律解释等一般规范判断'
  },
  {
    key: 'SM',
    title: '个别规范判断 SM',
    description: '针对个案形成的规范判断'
  },
  {
    key: 'GF',
    title: '一般事实判断 GF',
    description: '具有一般性的事实判断'
  },
  {
    key: 'SF',
    title: '个别事实判断 SF',
    description: '与当前案件相关的个别事实判断'
  },
  {
    key: 'OTHER_PROPOSITION',
    title: '其他命题要素',
    description: '指南中新增加且尚未归入现有基础类型的命题要素'
  }
]

function groupKeyOf(label: LabelDefinition): ElementTypeGroupKey {
  if (label.category === 'NON_PROPOSITION') return 'NON_PROPOSITION'

  if (label.baseType === 'GM') return 'GM'
  if (label.baseType === 'SM') return 'SM'
  if (label.baseType === 'GF') return 'GF'
  if (label.baseType === 'SF') return 'SF'

  return 'OTHER_PROPOSITION'
}

function bySortOrder(a: LabelDefinition, b: LabelDefinition): number {
  return a.sortOrder - b.sortOrder || a.code.localeCompare(b.code)
}

/**
 * 将 G06 标签字典转换为选择器分组。
 * 分组与顺序仅由接口字段决定，不依赖前端硬编码的标签代码清单。
 */
export function groupElementTypes(labels: readonly LabelDefinition[]): ElementTypeGroup[] {
  const grouped = new Map<ElementTypeGroupKey, LabelDefinition[]>()

  for (const label of labels) {
    const key = groupKeyOf(label)
    const items = grouped.get(key) ?? []
    items.push(label)
    grouped.set(key, items)
  }

  return GROUPS.flatMap((group) => {
    const items = grouped.get(group.key)
    if (!items?.length) return []

    return [{ ...group, labels: [...items].sort(bySortOrder) }]
  })
}
