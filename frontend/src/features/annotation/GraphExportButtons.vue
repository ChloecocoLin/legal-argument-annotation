<script setup lang="ts">
// 论证图的 SVG / PNG 导出按钮（分工.md §八.5）。三个用到图示的页面（标注工作台 /
// 图示审查 / 关系裁定）放同一对按钮，避免各处重抄一遍取名与下载逻辑。
//
// 导出的是「文件」不是「截图」：ArgumentGraph 会把整图用内容包围盒重新取景并内联
// 计算样式，所以缩放到多少、当前选中哪个图元，都不影响产物内容。
import { ref } from 'vue'
import Icon from '@/shared/Icon.vue'
import { toast } from '@/shared/toast'
import {
  downloadBlob,
  exportFileName,
  svgBlob,
  type GraphExportTarget
} from '@/shared/graphExport'

const props = defineProps<{
  /** ArgumentGraph 实例（模板 ref）。尚未挂载或没有图元时为 null。 */
  target: GraphExportTarget | null
  /** 文件名主体，用于区分导出来源，如「图示审查-md-1」。 */
  name: string
}>()

const busy = ref(false)

function saveSvg(): void {
  if (!props.target) {
    toast('图示尚未就绪，无法导出', 'err')
    return
  }
  const svg = props.target.exportSvg()
  if (!svg) {
    toast('没有可导出的图示', 'err')
    return
  }
  const filename = exportFileName(props.name, 'svg', new Date())
  downloadBlob(svgBlob(svg), filename)
  toast(`已导出 ${filename}`)
}

async function savePng(): Promise<void> {
  if (!props.target || busy.value) return
  busy.value = true
  try {
    const blob = await props.target.exportPng()
    const filename = exportFileName(props.name, 'png', new Date())
    downloadBlob(blob, filename)
    toast(`已导出 ${filename}`)
  } catch (error) {
    // 光栅化是唯一依赖真 canvas 的一段，失败原因如实抛给用户
    toast(error instanceof Error ? error.message : '导出 PNG 失败', 'err')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <button
    class="btn btn-icon btn-ghost"
    title="导出 SVG（矢量，可再编辑）"
    :disabled="!target"
    @click="saveSvg"
  >
    <Icon name="download" />
    <span class="t-xs">SVG</span>
  </button>
  <button
    class="btn btn-icon btn-ghost"
    title="导出 PNG（位图，2 倍图）"
    :disabled="!target || busy"
    @click="savePng"
  >
    <Icon name="download" />
    <span class="t-xs">{{ busy ? '导出中' : 'PNG' }}</span>
  </button>
</template>
