<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import ArgumentGraph from '@/features/annotation/ArgumentGraph.vue'
import type { GraphView } from '@/types/graph'
import type { FinalSnapshot } from './api'
import { componentsOf } from '@/shared/graphGeometry'
import { AUDIT_DIMENSIONS, OTHER_DIMENSION, categoryOf, checkGlyphs } from '@/shared/graphAudit'
const props=defineProps<{snapshot:FinalSnapshot;graph:GraphView}>()
const selected=ref(-1), graphRef=ref<InstanceType<typeof ArgumentGraph>>()
const components=computed(()=>componentsOf(props.graph.nodes,props.graph.edges.map(e=>({a:e.source,b:e.target}))))
const selectedIds=computed(()=>new Set(selected.value<0?props.graph.nodes.map(n=>n.id):components.value[selected.value]||[]))
const nodes=computed(()=>props.graph.nodes.filter(n=>selectedIds.value.has(n.id)))
const edges=computed(()=>props.graph.edges.filter(e=>selectedIds.value.has(e.source)&&selectedIds.value.has(e.target)))
const glyphs=computed(()=>checkGlyphs(props.graph.nodes,props.graph.edges))
const checks=computed(()=>[...AUDIT_DIMENSIONS,OTHER_DIMENSION].map(d=>({...d,count:d.id==='GLYPH_ERROR'?glyphs.value.length:d.id==='MULTIPLE_STRUCTURES'?props.graph.componentCount:d.id==='ISOLATED_PROPOSITION'?props.graph.isolatedNodeIds.length:props.graph.issues.filter(i=>categoryOf(i)===d.id).length})))
watch(()=>props.snapshot.version.id,()=>{selected.value=-1})
</script>
<template>
<div class="card"><div class="card-head between"><div class="card-title"><span class="dot"></span>最终论证图示</div><div class="row"><button class="btn btn-sm btn-ghost" @click="graphRef?.zoomBy(1.15)">＋</button><button class="btn btn-sm btn-ghost" @click="graphRef?.zoomBy(1/1.15)">－</button><button class="btn btn-sm btn-secondary" @click="graphRef?.fit()">适合窗口</button></div></div>
<div class="toolbar"><select v-model.number="selected" class="select" aria-label="独立论证结构"><option :value="-1">全部结构（{{graph.componentCount}}）</option><option v-for="(ids,index) in components" :key="index" :value="index">结构 {{index+1}}（{{ids.length}} 个对象）</option></select><span class="badge outline">孤立命题 {{graph.isolatedNodeIds.length}}</span></div>
<ArgumentGraph ref="graphRef" :nodes="nodes" :edges="edges" :positions="graph.positions" :editable="false" />
<div class="audit"><div v-for="check in checks" :key="check.id" class="between"><span :title="check.hint">{{check.label}}</span><span>{{check.id==='MULTIPLE_STRUCTURES'?`${check.count} 个结构`:check.count?`${check.count} 项提示`:'检查通过'}}</span></div><p v-for="issue in graph.issues" :key="issue.code+issue.message" class="muted">{{issue.message}}</p><p v-for="issue in glyphs" :key="issue.targetId+issue.message" class="note danger">{{issue.message}}</p><p class="muted t-xs">按冻结快照只读构图，覆盖全部结构；孤立命题和多结构提示仍需人工审查。图布局不修改逻辑关系。</p></div></div>
</template>
<style scoped>.toolbar{padding:12px 18px;display:flex;gap:12px;align-items:center}.select{width:240px}.audit{padding:18px;border-top:1px solid var(--border)}.audit>.between{padding:5px 0}.row{gap:6px}</style>
