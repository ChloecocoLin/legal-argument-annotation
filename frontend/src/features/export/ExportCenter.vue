<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import LocalSession from '@/features/versions/LocalSession.vue'
import { errorMessage, getLocalContext } from '@/features/versions/api'
import { loadExportPreview, type ExportPreview } from './api'
const data = ref<ExportPreview>(), loading = ref(false), error = ref(''), guide = ref(''), source = ref('')
const picked = ref<string[]>([]), format = ref('JSON'), includeMeta = ref(true)
const formats = computed(() => [format.value])
const guides = computed(() => Array.from(new Map((data.value?.items || []).map(x => [x.guideVersionId,x.guideVersionCode]))))
const candidates = computed(() => (data.value?.items || []).filter(x => (!guide.value || x.guideVersionId === guide.value) && (!source.value || x.sourceType === source.value)))
const selected = computed(() => candidates.value.filter(x => picked.value.includes(x.id)))
const preview = computed(() => selected.value.flatMap(x => x.preview.map(e => ({ ...e, finalId: x.id }))).slice(0,5))
const counts = computed(() => selected.value.reduce((a,x) => ({elements:a.elements+x.elementCount,relations:a.relations+x.relationCount}),{elements:0,relations:0}))
watch([guide,source], () => { picked.value = picked.value.filter(id => candidates.value.some(x => x.id === id)) })
async function load() {
  loading.value = true; error.value = ''; data.value = undefined; picked.value = []
  try {
    if (import.meta.env.DEV) await getLocalContext()
    data.value = await loadExportPreview()
  } catch (e) { error.value = errorMessage(e) } finally { loading.value = false }
}
onMounted(load)
</script>
<template>
  <LocalSession description="导出页面预览；只读取已入库版本，暂不生成文件或下载记录" />
  <div class="page-head between"><div><div class="eyebrow">EXPORT CENTER</div><h1 class="t-h1">导出中心</h1><p class="t-sm muted">将已入库的最终标注版本导出为结构化数据与论证图示，用于研究、教学或数据构建。</p></div><router-link class="btn btn-secondary" to="/export-records">导出记录</router-link></div>
  <div v-if="loading" class="card card-body muted" role="status">正在读取已入库版本…</div>
  <div v-else-if="error" class="note danger" role="alert">{{ error }} <button class="btn btn-secondary" @click="load">重试</button></div>
  <template v-else-if="data">
    <div class="note warn boundary" role="status">{{ data.reason }}</div>
    <section class="grid-2 export-body">
      <div class="stack-5">
        <div class="card"><div class="card-head"><div class="card-title"><span class="dot"></span>选择导出范围</div><button class="btn btn-sm btn-ghost" @click="load">刷新</button></div><div class="card-body stack-4">
          <div class="field-row"><div class="field"><label for="export-guide">指南版本</label><select id="export-guide" class="select" v-model="guide"><option value="">全部指南版本</option><option v-for="[id,code] in guides" :key="id" :value="id">{{ code }}</option></select></div><div class="field"><label for="export-source">文书来源</label><select id="export-source" class="select" v-model="source"><option value="">全部已入库</option><option value="ORDINARY_JUDGMENT">普通裁判文书</option><option value="GUIDING_CASE">指导性案例</option><option value="REFERENCE_CASE">参考案例</option></select></div></div>
          <div class="field"><label>选择文书（已入库）</label><div class="tbl-wrap"><table class="tbl"><thead><tr><th></th><th>文书 / 案号</th><th>要素</th><th>关系</th><th>状态</th></tr></thead><tbody><tr v-for="item in candidates" :key="item.id"><td><input type="checkbox" v-model="picked" :value="item.id" :aria-label="`选择 ${item.title} F${item.versionNumber}`"></td><td><b>{{ item.title }}</b><div class="mono t-xs muted">{{ item.caseNumber }} · F{{ item.versionNumber }}</div></td><td>{{ item.elementCount }}</td><td>{{ item.relationCount }}</td><td><span class="badge success">已入库</span></td></tr><tr v-if="!candidates.length"><td colspan="5" class="empty muted">{{ data.items.length ? '当前筛选条件下没有已入库版本' : '当前权限范围内没有已入库版本。请先完成最终审查和正式入库。' }}</td></tr></tbody></table></div></div>
        </div></div>
        <div class="card"><div class="card-head"><div class="card-title"><span class="dot"></span>导出格式</div></div><div class="card-body"><div class="grid-2 formats"><label v-for="[value,title] in [['JSON','JSON'],['XLSX','Excel'] ]" :key="value" class="fmt" :class="{'is-on':format === value}"><input type="radio" name="export-format" v-model="format" :value="value"><span class="fi">{{ value }}</span><b>{{ title }}</b></label><div v-for="value in ['PNG 图示','SVG 图示']" :key="value" class="fmt disabled"><span class="fi">{{ value.split(' ')[0] }}</span><span>{{ value }}<small class="muted">等待图示模块接入</small></span></div></div><p class="t-xs muted options-title">每次选择一种格式；多格式打包需另行提供。</p>
          <div class="section-label options-title">导出选项</div><div class="stack-3 t-sm"><label><input type="checkbox" checked disabled> 包含要素序号、完整文本与 UTF-16 位置</label><label><input type="checkbox" checked disabled> 包含结构化关系、参与项与来源版本</label><label><input type="checkbox" v-model="includeMeta"> 包含标注者与裁定者展示信息</label><p class="muted t-xs">关系公式仅作展示；动态标签字典、版本及来源引用属于必备数据。格式选择为本页配置预览。</p></div>
        </div></div>
      </div>
      <div class="stack-5"><div class="card"><div class="card-head"><div class="card-title">导出预览</div></div><div class="card-body"><p class="t-xs muted">要素表（所选文书前 5 行）</p><div class="tbl-wrap"><table class="tbl"><thead><tr><th>序号</th><th>文本</th><th>类型</th></tr></thead><tbody><tr v-for="row in preview" :key="`${row.finalId}:${row.id}`"><td class="mono">{{ row.displayNo }}</td><td>{{ row.text }}</td><td>{{ row.type }}</td></tr><tr v-if="!preview.length"><td colspan="3" class="empty muted">选择已入库版本后显示预览</td></tr></tbody></table></div></div></div>
        <div class="card"><div class="card-body stack-3"><div class="between"><b>本次导出</b><span class="badge accent">{{ formats.length }} 种格式</span></div><div v-for="[label,value] in [['文书',selected.length+' 篇'],['要素记录',counts.elements+' 条'],['关系记录',counts.relations+' 条'],['格式',formats.join(' / ')||'未选择']]" :key="label" class="between t-sm"><span class="muted">{{ label }}</span><b>{{ value }}</b></div><button class="btn btn-primary btn-block" disabled>开始导出（待接入）</button><p class="muted t-xs">正式入库与文件流程完成后开放导出。</p></div></div>
      </div>
    </section>
  </template>
</template>
<style scoped>
.t-h1{margin:6px 0}.boundary{margin-bottom:20px}.export-body{grid-template-columns:1.6fr 1fr;align-items:start}.card-head{justify-content:space-between;gap:12px}.empty{padding:28px 16px;text-align:center;white-space:normal}.formats{gap:12px}.fmt{display:flex;align-items:center;gap:10px;padding:11px 13px;border:1px solid var(--border);border-radius:var(--r-md)}.fmt.is-on{border-color:var(--accent);background:var(--accent-soft)}.fi{font-family:var(--font-mono);font-size:11px;font-weight:700;padding:10px;background:var(--surface-3);border-radius:var(--r-sm)}.fmt small{display:block;font-size:10px;margin-top:3px}.disabled{opacity:.65}.options-title{margin:20px 0 10px}.field label,.stack-3>label{display:block}.tbl td{overflow-wrap:anywhere}.tbl{font-size:12px}@media(max-width:1100px){.export-body{grid-template-columns:1fr}}@media(max-width:620px){.field-row,.formats{grid-template-columns:1fr}.page-head{gap:14px;align-items:flex-start;flex-direction:column}}
</style>
