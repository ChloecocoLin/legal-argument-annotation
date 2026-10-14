// 关系参与矩阵（数据模型 §1.1）的前端副本。
// 用途：候选列表即时标记可选项、给不可用项写中文原因、以及在提交前排出 ordinal。
// **权威判定在后端 RelationValidator**（R02/R03 会返回 RELATION_CYCLE / INVALID_PARTICIPANT），
// 这里只做同一套规则的提前提示，不做拦截性校验的最终依据。
import type { RelationType } from '@/types/common'
import type {
  ParticipantInput,
  ParticipantRole,
  ParticipantTargetKind
} from '@/types/relation'
import { REL_META } from './relationMeta'

/** 个别判断：普通个别规范判断、合同及合同解释、个别事实判断（M 的来源） */
export const INDIVIDUAL_LABELS = ['SM', 'SM-C', 'SF']

/** 一般判断：一般事实判断与各类一般规范判断（M 的目标） */
export const GENERAL_LABELS = ['GF', 'GM-L', 'GM-I', 'GM-U', 'GM-M', 'GM-O']

export function isIndividualLabel(code?: string | null): boolean {
  return !!code && INDIVIDUAL_LABELS.includes(code)
}

export function isGeneralLabel(code?: string | null): boolean {
  return !!code && GENERAL_LABELS.includes(code)
}

export function isKnownRelationCode(code?: string | null): code is RelationType {
  return !!code && code in REL_META
}

/** 展示编号 → 数值（P12 → 12）；无法解析返回 -1，与后端 RelationValidator.parseDisplayNo 一致。 */
export function parseDisplayNo(displayNo?: string | null): number {
  const matched = /(\d+)/.exec(displayNo || '')
  return matched ? Number(matched[1]) : -1
}

export interface CandidateInfo {
  targetKind: ParticipantTargetKind
  targetId: string
  /** 命题的标签代码，或关系的关系代码 */
  code: string | null
  displayNo: string | null
  /** ELEMENT 候选是否为命题（非命题不能参与任何关系） */
  proposition: boolean
}

export interface CandidateCheck {
  ok: boolean
  reason: string
}

const OK: CandidateCheck = { ok: true, reason: '' }

function reject(reason: string): CandidateCheck {
  return { ok: false, reason }
}

/**
 * 判断某候选能否填入某关系的某个角色槽位。
 * 有向关系：role = SOURCE（第 1 槽）/ TARGET（第 2 槽）；无向关系（J/I）：role = MEMBER。
 */
export function checkParticipant(
  relationCode: RelationType,
  role: ParticipantRole,
  candidate: CandidateInfo
): CandidateCheck {
  const isElement = candidate.targetKind === 'ELEMENT'
  if (isElement && !candidate.proposition) {
    return reject('非命题要素不能参与论证关系')
  }
  const code = candidate.code || ''

  switch (relationCode) {
    case 'S':
      if (role === 'SOURCE') {
        if (!isElement && code !== 'J' && code !== 'M') {
          return reject('来源关系只能是 J 或 M')
        }
        return OK
      }
      return isElement ? OK : reject('被支持命题只能是命题，不能是关系')

    case 'A':
      if (role === 'SOURCE') {
        return isElement ? OK : reject('反对者必须是命题，不能是关系')
      }
      if (!isElement && code !== 'S') {
        return reject('被反对对象只能是命题或 S 关系')
      }
      return OK

    case 'J':
      // 成员可以是命题，也可以是已成立的 J/M/S/A 关系整体
      return OK

    case 'M':
      if (role === 'SOURCE') {
        if (!isElement) {
          return code === 'J' ? OK : reject('M 的来源关系只能是 J')
        }
        return isIndividualLabel(code) ? OK : reject('来源必须是个别判断（SF / SM / SM-C）')
      }
      if (!isElement) {
        return reject('被匹配的一般判断不能是关系')
      }
      return isGeneralLabel(code) ? OK : reject('目标必须是一般判断（GF / GM-*）')

    case 'I':
      return isElement ? OK : reject('I 的成员只能是命题（后续关系请用该组最大展示编号命题）')

    default:
      return reject('未知关系类型')
  }
}

/**
 * 把界面上的候选（有向：来源 → 目标；无向：成员集合）整理成 R02/R03 的 participants。
 * 无向关系先按展示编号数值序排列（I 的代表节点即组内最大编号命题）；ordinal 从 1 起。
 */
export function orderedInputs(
  relationCode: RelationType,
  picked: CandidateInfo[]
): ParticipantInput[] {
  const directed = REL_META[relationCode].directed
  const ordered = directed
    ? picked.slice(0, 2)
    : [...picked].sort((a, b) => parseDisplayNo(a.displayNo) - parseDisplayNo(b.displayNo))

  return ordered.map((candidate, index) => ({
    ordinal: index + 1,
    role: (directed ? (index === 0 ? 'SOURCE' : 'TARGET') : 'MEMBER') as ParticipantRole,
    targetKind: candidate.targetKind,
    targetId: candidate.targetId
  }))
}

/** I 的代表：组内最大展示编号命题（用于界面提示，后端 GR01 也会给出 representativeId） */
export function representativeOf(picked: CandidateInfo[]): CandidateInfo | null {
  const elements = picked.filter((c) => c.targetKind === 'ELEMENT')
  if (elements.length === 0) return null
  return elements.reduce((best, cur) =>
    parseDisplayNo(cur.displayNo) > parseDisplayNo(best.displayNo) ? cur : best
  )
}
