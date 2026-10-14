// 论证图类型（GR01–GR03，归成员C）。依据《核心标注与裁定数据模型》§1 与 openapi-ready.yaml。
import type { RelationIssue, RelationOwnerType } from './relation'

export type GraphStatus = 'VALID' | 'INVALID'
export type GraphNodeKind = 'ELEMENT' | 'RELATION'

// 逻辑节点，**不含坐标**；坐标在布局里（GraphNodePosition）
export interface GraphNode {
  id: string
  kind: GraphNodeKind
  displayNo: string | null
  labelCode: string | null
  text: string | null
  relationCode: string | null
  directed: boolean | null
  expression: string | null
  // I 关系的代表节点：组内最大展示编号命题
  representativeId: string | null
}

export interface GraphEdge {
  id: string
  source: string
  target: string
  relationCode: string
  arrow: boolean
  dashed: boolean
  relationId: string
}

export interface GraphNodePosition {
  targetKind: GraphNodeKind
  targetId: string
  x: number
  y: number
}

export interface GraphView {
  graphId: string
  ownerType: RelationOwnerType
  ownerId: string
  status: GraphStatus
  layoutVersion: number
  canvasWidth: number | null
  canvasHeight: number | null
  positions: GraphNodePosition[]
  nodes: GraphNode[]
  edges: GraphEdge[]
  isolatedNodeIds: string[]
  componentCount: number
  issues: RelationIssue[]
}

export interface GraphValidateRequest {
  elementIds: string[]
  relationIds: string[]
}

export interface GraphValidateResult {
  status: GraphStatus
  issues: RelationIssue[]
  componentCount: number
  isolatedNodeIds: string[]
}

export interface LayoutCreateRequest {
  canvasWidth: number
  canvasHeight: number
  nodePositions: GraphNodePosition[]
}

export interface LayoutCreateResult {
  layoutVersion: number
}
