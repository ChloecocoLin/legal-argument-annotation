// 教学评分类型（分工.md §四-7，归成员C）。
// 与后端 com.argument.teaching.dto 的 record 一一对应；字段名不要改，改了就是接口变了。
import type { RelationType } from './common'
import type { GraphEdge, GraphNode } from './graph'
import type { ParticipantRole, ParticipantTargetKind, RelationIssue } from './relation'

/** 五类练习：S 专项 / J-M 区分 / A-I 区分 / 嵌套 / 学生图与标准图比较 */
export type TeachingExerciseType =
  | 'SUPPORT'
  | 'JOIN_VS_MATCH'
  | 'ATTACK_VS_IDENTIFY'
  | 'NESTING'
  | 'GRAPH_COMPARE'

/**
 * 题库列表项。**刻意不含标准答案的任何计数**（关系条数、独立图个数）：
 * 列表页学生可见，「标准答案有几条关系」本身就是解题线索。
 */
export interface ExerciseSummary {
  id: string
  exerciseCode: string
  exerciseType: TeachingExerciseType
  exerciseTypeLabel: string
  title: string
  sortOrder: number
}

/**
 * 练习范围内的一条命题。非命题（IS / JD）也会列出且 `proposition=false`——
 * 学生要自己判断「这个不能参与关系」，这正是练习要考的一点。
 */
export interface ExerciseElement {
  id: string
  displayNo: string | null
  labelCode: string
  text: string
  proposition: boolean
}

/** 一个评分维度的权重（不含得分）；key 见后端 TeachingDimension 枚举名。 */
export interface DimensionWeight {
  key: string
  label: string
  weight: number
}

/**
 * 单题详情（学生做题时取）。**后端不返回标准答案与讲评要点**，
 * 它们只在 score 的响应里出现——前端也不要提前渲染占位，避免误以为「详情里就有答案」。
 */
export interface ExerciseDetail {
  id: string
  exerciseCode: string
  exerciseType: TeachingExerciseType
  exerciseTypeLabel: string
  title: string
  prompt: string
  snapshotId: string
  elements: ExerciseElement[]
  dimensions: DimensionWeight[]
}

/** 答题模板的一个参与项。ELEMENT 用 targetId 指命题，RELATION 用 targetRef 指本作答内的另一条关系。 */
export interface TemplateParticipant {
  ordinal: number
  role: ParticipantRole
  targetKind: ParticipantTargetKind
  targetId: string | null
  targetRef: string | null
}

/** 一条**尚未落库**的关系。ref 在本次提交内唯一，嵌套用它互相引用。 */
export interface RelationTemplate {
  ref: string
  code: RelationType
  participants: TemplateParticipant[]
  note?: string | null
}

/** 单个维度的得分。`applicable=false` 表示本练习里该维度不可判，其权重已被剔除并重新归一。 */
export interface DimensionScore {
  key: string
  label: string
  weight: number
  value: number
  applicable: boolean
  detail: string
}

/**
 * 一条教学反馈。code 取值见后端 `TeachingFeedbackCode`（是字符串常量类，**不是枚举**）。
 * level=INFO 的只提示不扣分。
 */
export interface TeachingFeedback {
  code: string
  level: 'ERROR' | 'INFO'
  dimension: string | null
  message: string
  studentExpression: string | null
  standardExpression: string | null
}

/** 计数汇总：回答「错了几处、错成什么样」，供 D 做「高频错误」聚合。 */
export interface ScoreStatistics {
  standardRelations: number
  studentRelations: number
  matched: number
  missing: number
  extra: number
  wrongDirection: number
  wrongType: number
  wrongParticipants: number
  wrongNesting: number
  standardStructures: number | null
  studentStructures: number | null
  isolatedMismatch: number | null
}

/** 标准答案里的一条关系（表达式已由后端渲染）。 */
export interface StandardAnswerRelation {
  ref: string
  code: RelationType
  expression: string
}

/** 标准答案的可展示形态；只在评分后回显。 */
export interface StandardAnswer {
  relations: StandardAnswerRelation[]
  structureCount: number
  isolatedDisplayNos: string[]
}

/** 学生图与标准图的对照；只有 GRAPH_COMPARE 这类给「图结构」配了权重的练习才返回。 */
export interface TeachingGraphView {
  standardNodes: GraphNode[]
  standardEdges: GraphEdge[]
  studentNodes: GraphNode[]
  studentEdges: GraphEdge[]
}

/** 一次教学评分的完整结果。**只读试算，不落库**——成绩留档归 D。 */
export interface TeachingScoreResult {
  exerciseId: string
  exerciseType: TeachingExerciseType
  exerciseTypeLabel: string
  score: number
  dimensions: DimensionScore[]
  feedback: TeachingFeedback[]
  statistics: ScoreStatistics
  validationIssues: RelationIssue[]
  standardAnswer: StandardAnswer
  graph: TeachingGraphView | null
  explanation: string
}
