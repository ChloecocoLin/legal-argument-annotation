import type { Element, Relation, RelationParticipant } from './types'
// Display is derived from structured participants; expressions never become saved relationship facts.
export function formula(relation: Relation, elements: Element[], relations: Relation[], participants: RelationParticipant[], seen = new Set<string>()): string {
  if (seen.has(relation.id)) return '循环引用'
  const path = new Set(seen).add(relation.id)
  const parts = participants.filter(p => p.relationId === relation.id).sort((a, b) => {
    if (relation.directed) return (a.role === 'SOURCE' ? 0 : 1) - (b.role === 'SOURCE' ? 0 : 1)
    return a.ordinal - b.ordinal
  }).map(p => {
    if (p.targetKind === 'ELEMENT') return elements.find(e => e.id === p.targetId)?.displayNo || '缺失命题'
    const nested = relations.find(r => r.id === p.targetId)
    return nested ? formula(nested, elements, relations, participants, path) : '缺失关系'
  })
  return `${relation.relationCode}(${parts.join(', ')})`
}
