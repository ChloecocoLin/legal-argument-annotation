// 要素 Element 类型。依据契约 6.3 与《核心标注与裁定数据模型》。
import type { EntityBase, ElementType } from './common'

// 要素归属对象类型（随模型确认可能扩展 FINAL）
export type ElementOwnerType = 'DRAFT' | 'CUSTOM_ANNOTATION' | 'FINAL_ANNOTATION'

export interface Element extends EntityBase {
  // 归属
  ownerType: ElementOwnerType
  ownerId: string

  // 文本版本与范围
  documentTextVersionId: string
  annotationScopeId?: string
  startOffset: number
  endOffset: number
  text: string

  // 标签
  labelDefinitionId: string | null
  labelCode: ElementType
  isProposition: boolean

  // 展示编号，未确定为 null
  displayNo: string | null

  // 拆分/合并来源
  sourceElementIds: string[]

  recordVersion?: number
}
