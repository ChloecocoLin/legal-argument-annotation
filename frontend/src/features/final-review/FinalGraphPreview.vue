<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { FinalSnapshot } from './api'
const props = defineProps<{ snapshot: FinalSnapshot }>()
const selected = ref(0), zoom = ref(1)
const components = computed(() => {
  const graph = props.snapshot.graph, ids = [...graph.elementIds, ...graph.relationIds], remaining = new Set(ids), neighbors = new Map(ids.map(id => [id, [] as string[]]))
  for (const p of props.snapshot.participants) { neighbors.get(p.relationId)?.push(p.targetId); neighbors.get(p.targetId)?.push(p.relationId) }
  const groups: string[][] = []
  for (const id of ids) { if (!remaining.has(id)) continue; const group: string[] = [], queue = [id]; remaining.delete(id); while (queue.length) { const next = queue.shift()!; group.push(next); for (const n of neighbors.get(next) || []) if (remaining.delete(n)) queue.push(n) } groups.push(group) }
  return groups
})
watch(() => props.snapshot.version.id, () => { selected.value = 0; zoom.value = 1 })
const nodes = computed(() => {
  const ids = components.value[selected.value] || [], elements = props.snapshot.elements.filter(e => ids.includes(e.id)), relations = props.snapshot.relations.filter(r => ids.includes(r.id))
  return [...elements.map((e, i) => ({ id: e.id, x: 150, y: 70 + i * 100, label: e.displayNo || '非命题', title: `${e.labelCode} · ${e.text}`, relation: false })), ...relations.map((r, i) => ({ id: r.id, x: 410 + i % 2 * 200, y: 90 + Math.floor(i / 2) * 130, label: r.relationCode, title: '关系 ' + r.relationCode, relation: true }))]
})
const links = computed(() => props.snapshot.participants.flatMap(p => {
  const relation = nodes.value.find(n => n.id === p.relationId), target = nodes.value.find(n => n.id === p.targetId)
  if (!relation || !target) return []
  return [{ id: p.id, from: p.role === 'TARGET' ? relation : target, to: p.role === 'TARGET' ? target : relation, directed: p.role !== 'MEMBER', role: p.role }]
}))
const height = computed(() => Math.max(520, ...nodes.value.map(n => n.y + 80)))
const marker = computed(() => 'preview-arrow-' + props.snapshot.graph.id)
const viewBox = computed(() => `${(820 - 820 / zoom.value) / 2} ${(height.value - height.value / zoom.value) / 2} ${820 / zoom.value} ${height.value / zoom.value}`)
</script>
<template>
 <div class="card"><div class="card-head between"><div class="card-title"><span class="dot"></span>最终论证图示</div><div class="row tools"><button class="btn btn-sm btn-ghost" aria-label="放大图示" @click="zoom = Math.min(2, zoom + .2)">＋</button><button class="btn btn-sm btn-ghost" aria-label="缩小图示" @click="zoom = Math.max(.5, zoom - .2)">－</button><button class="btn btn-sm btn-ghost" @click="zoom = 1">适合窗口</button><button class="btn btn-sm btn-ghost" disabled title="正式图与文件服务待 C 接入">导出 PNG</button></div></div>
  <div class="preview-toolbar"><span class="badge warn">工作视图 · 结构预览</span><select v-model.number="selected" class="select" aria-label="独立论证结构"><option v-for="(ids, i) in components" :key="i" :value="i">结构 {{ i + 1 }} / {{ components.length }}（{{ ids.length }} 个对象）</option></select><button class="btn btn-sm btn-secondary" disabled>正式视图待接入</button></div>
  <div class="graph-stage"><p v-if="!components.length" class="muted">没有可展示的命题或关系。</p><svg v-else :viewBox="viewBox" role="img" :aria-label="`独立结构 ${selected + 1} 的命题与结构化参与项预览`"><defs><marker :id="marker" viewBox="0 0 10 10" refX="20" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse"><path d="M 0 0 L 10 5 L 0 10 z" fill="currentColor" /></marker></defs><g v-for="p in links" :key="p.id"><line :x1="p.from.x" :y1="p.from.y" :x2="p.to.x" :y2="p.to.y" :marker-end="p.directed ? `url(#${marker})` : undefined" /><title>{{ p.role }}</title></g><g v-for="node in nodes" :key="node.id" :transform="`translate(${node.x}, ${node.y})`"><title>{{ node.title }}</title><circle v-if="node.relation" r="23" /><rect v-else x="-42" y="-20" width="84" height="40" rx="7" /><text text-anchor="middle" dominant-baseline="central">{{ node.label }}</text></g></svg></div>
  <p class="legend muted t-xs">覆盖全部独立结构及孤立命题。连接由关系参与项生成；此预览不替代 C 的正式图示、布局和完整校验。</p>
 </div>
</template>
<style scoped>.tools{gap:4px;flex-wrap:wrap}.card-head{gap:12px;flex-wrap:wrap}.preview-toolbar{display:flex;gap:12px;align-items:center;flex-wrap:wrap;padding:12px 18px;border-bottom:1px solid var(--border)}.select{width:230px}.graph-stage{height:520px;background:var(--surface-2);overflow:auto}.graph-stage svg{width:100%;height:100%;min-width:420px;color:var(--accent)}line{stroke:var(--muted);stroke-width:1.5}circle,rect{fill:var(--surface);stroke:var(--accent);stroke-width:1.5}text{fill:var(--fg);font-size:14px;font-family:var(--font-mono)}.legend{padding:12px 18px;border-top:1px solid var(--border);line-height:1.6}</style>
