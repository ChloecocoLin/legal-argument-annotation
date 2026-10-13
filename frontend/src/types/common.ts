// 公共类型与枚举。依据《公共规范与基础数据模型》《核心标注与裁定数据模型》整理。

// 统一响应外层：成功 code = 0
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  traceId?: string
}

// 排序
export interface SortField {
  field: string
  direction: 'asc' | 'desc'
}

// 分页请求
export interface PageQuery {
  page?: number
  size?: number
  keyword?: string
  sort?: SortField[]
}

// 分页返回（数据模型文档写法）
export interface PageResponse<T> {
  list: T[]
  total: number
  page: number
  size: number
}

// openapi-ready.yaml 分页返回：data.items + meta{page,size,total}
export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  size: number
}

// 仅含 items 的列表返回
export interface ListResult<T> {
  items: T[]
}

// 时间区间
export interface TimeRange {
  start?: string
  end?: string
}

// 元素（要素）类型代码
// 命题层
export type PropositionType =
  | 'GM'
  | 'GM-L'
  | 'GM-I'
  | 'GM-U'
  | 'GM-M'
  | 'GM-O'
  | 'SM'
  | 'SM-C'
  | 'GF'
  | 'SF'

// 非命题层
export type NonPropositionType = 'IS' | 'AS' | 'SS' | 'JG' | 'JD' | 'Non-O'

export type ElementType = PropositionType | NonPropositionType

// 关系类型代码
export type RelationType = 'S' | 'A' | 'J' | 'M' | 'I'

// 指南状态
export type GuideStatus = 'draft' | 'active' | 'archived'

// 指南解析状态
export type GuideParseStatus = 'pending' | 'parsing' | 'parsed' | 'parse_failed'

// 裁定会话状态
export type AdjudicationSessionStatus =
  | 'pending'
  | 'proposition'
  | 'relation'
  | 'returned'
  | 'expert_review'
  | 'completed'

// 分歧分类（需求 8.2.4）
export type DiffCategory = 'label' | 'boundary' | 'relation'

// 命题裁定选择
export type PropositionDecision = 'A' | 'B' | 'custom' | 'expert'

// 专家复核状态
export type ExpertReviewStatus = 'pending' | 'offline_discussing' | 'confirmed'

// 疑难问题状态
export type ExpertIssueStatus = 'open' | 'resolved' | 'confirmed'

// 参与者类型（关系端点 / 嵌套节点）
export type ParticipantKind = 'element' | 'relation'

export interface ParticipantRef {
  kind: ParticipantKind
  id: string
}

// 通用 id 对象
export interface EntityBase {
  id: string
  createdAt?: string
  updatedAt?: string
}
