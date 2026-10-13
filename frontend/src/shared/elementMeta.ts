// 要素类型的视觉元数据。
// 业务字典必须以 G06 返回的 LabelDefinition 为准；这里不负责决定标签是否存在或如何分组。
export interface TypeMetaItem {
  name: string
  group: string
  short: string
}
export const TYPE_META: Record<string, TypeMetaItem> = {
  IS: { name: '争议焦点', group: 'nl', short: '争议焦点' },
  AS: { name: '态度声明', group: 'nl', short: '态度声明' },
  SS: { name: '总结性陈述', group: 'nl', short: '总结性陈述' },
  JG: { name: '判决依据', group: 'nl', short: '判决依据' },
  JD: { name: '判决结果', group: 'nl', short: '判决结果' },
  'Non-O': { name: '其它非论证成分', group: 'nl', short: '其它非论证' },
  'GM-L': { name: '法律条文', group: 'gm', short: '法律条文' },
  'GM-I': { name: '法律解释', group: 'gm', short: '法律解释' },
  'GM-U': { name: '习惯与行业惯例', group: 'gm', short: '习惯与行业惯例' },
  'GM-M': { name: '道德与价值观念', group: 'gm', short: '道德与价值观念' },
  'GM-O': { name: '其他规范判断', group: 'gm', short: '其他规范判断' },
  SM: { name: '普通个别规范判断', group: 'sm', short: '普通个别规范判断' },
  'SM-C': { name: '合同及合同解释', group: 'sm', short: '合同及合同解释' },
  GF: { name: '一般事实判断', group: 'gf', short: '一般事实判断' },
  SF: { name: '个别事实判断', group: 'sf', short: '个别事实判断' }
}

export function typeGroup(code?: string): string {
  if (!code) return 'nl'
  if (code.indexOf('GM') === 0) return 'gm'
  if (code.indexOf('SM') === 0) return 'sm'
  if (code === 'GF') return 'gf'
  if (code === 'SF') return 'sf'
  return 'nl'
}
