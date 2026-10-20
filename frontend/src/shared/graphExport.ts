// 论证图的 SVG / PNG 底层生成（分工.md §八.5「SVG、PNG 底层生成由 C 负责」）。
//
// 分工边界：本模块只管「把图变成文件」；导出页面、导出记录、Excel/JSON 归 D（§五.5）。
// 因此这里不依赖 Vue、不依赖任何 store，只有纯函数与浏览器原生 API，
// D 的导出页直接 import 即可复用，不需要 C 再包一层接口。
//
// 为什么 SVG 在后端生成不了：图元坐标与画法的事实来源是 shared/graphGeometry.ts
// （ArgumentGraph.vue 与自检脚本共用同一份）。后端出图要么把这份几何翻成 Java（第二份
// 实现，必然与画出来的图漂移），要么引入 Batik（改 pom）。故 SVG 由渲染层自己序列化，
// 契约不动（openapi-ready.yaml 本来就没有导出接口）。
//
// 浏览器风险点只有两处，都集中在 toPngBlob：canvas 光栅化与字体就绪。其余全部可在
// Node 下自检（见 scripts/check-graph-rules.ts 的「导出」一节）。

import type { Bounds } from './graphGeometry'

export const SVG_NS = 'http://www.w3.org/2000/svg'

/** 导出图四周留白（绘图坐标）。给箭头与选中描边留出空间，避免贴边被裁。 */
export const EXPORT_PAD = 24

/** PNG 像素上限，超过就下调倍率——canvas 各浏览器上限不同，16M 像素是安全区。 */
export const MAX_PNG_PIXELS = 16_000_000

export interface ExportOptions {
  /** 文件名主体（不含扩展名），非法字符会被替换。 */
  name?: string
  /** 背景色（CSS 颜色）。不传则 SVG 透明、PNG 取绘图区底色。 */
  background?: string | null
  /** 四周留白，默认 EXPORT_PAD。 */
  padding?: number
  /** PNG 倍率，默认 2（Retina）。会被 MAX_PNG_PIXELS 下调。 */
  scale?: number
  /** PNG 画布总像素上限，默认 MAX_PNG_PIXELS。 */
  maxPixels?: number
}

/** 有图可导出的组件实例（ArgumentGraph.vue 的 defineExpose）。 */
export interface GraphExportTarget {
  exportSvg(options?: ExportOptions): string
  exportPng(options?: ExportOptions): Promise<Blob>
}

// ── 纯函数：文件名、倍率、画布尺寸（可在 Node 下自检）────────────────

/** 文件名时间戳 `YYYYMMDD-HHMM`，本地时间。 */
export function exportStamp(at: Date): string {
  const p = (n: number, w = 2) => String(n).padStart(w, '0')
  return (
    `${at.getFullYear()}${p(at.getMonth() + 1)}${p(at.getDate())}` +
    `-${p(at.getHours())}${p(at.getMinutes())}`
  )
}

/** 文件名：`<主体>-<时间戳>.<扩展名>`。主体里的空格与标点换成 `-`，避免下载名出错。 */
export function exportFileName(name: string, ext: 'svg' | 'png', at: Date): string {
  const safe = (name || 'argument-graph').trim().replace(/[^\w.一-龥-]+/g, '-')
  return `${safe.replace(/^-+|-+$/g, '') || 'argument-graph'}-${exportStamp(at)}.${ext}`
}

/**
 * PNG 倍率：实际倍率 = min(请求倍率, 像素上限允许的倍率)，且不低于 0.1。
 * 单独抽出来是为了可自检——大图导出失败通常就是这里没兜住。
 */
export function rasterScale(
  width: number,
  height: number,
  scale: number,
  maxPixels: number = MAX_PNG_PIXELS
): number {
  const w = Math.max(1, width)
  const h = Math.max(1, height)
  const want = Math.max(0.1, scale)
  const cap = Math.sqrt(Math.max(1, maxPixels) / (w * h))
  return Math.max(0.1, Math.min(want, cap))
}

/** PNG 画布像素尺寸（四舍五入后至少 1×1，canvas 不接受 0）。 */
export function rasterSize(
  width: number,
  height: number,
  scale: number,
  maxPixels: number = MAX_PNG_PIXELS
): { width: number; height: number; scale: number } {
  const k = rasterScale(width, height, scale, maxPixels)
  return {
    width: Math.max(1, Math.round(width * k)),
    height: Math.max(1, Math.round(height * k)),
    scale: k
  }
}

/** 内容包围盒 + 留白 → SVG viewBox 与画布尺寸。 */
export function exportViewBox(
  bounds: Bounds,
  padding: number = EXPORT_PAD
): { x: number; y: number; width: number; height: number; viewBox: string } {
  const pad = Math.max(0, padding)
  const x = Math.round(bounds.minX - pad)
  const y = Math.round(bounds.minY - pad)
  const width = Math.max(1, Math.round(bounds.maxX - bounds.minX + pad * 2))
  const height = Math.max(1, Math.round(bounds.maxY - bounds.minY + pad * 2))
  return { x, y, width, height, viewBox: `${x} ${y} ${width} ${height}` }
}

/**
 * 背景矩形：画在内容之下，覆盖整个 viewBox。透明背景返回 null。
 *
 * 返回属性而不是标记串，是为了让调用方用 createElementNS 自己建节点——
 * 在 <svg> 上用 insertAdjacentHTML 依赖浏览器的片段解析行为（foreign content），
 * 而这条路径在本仓库里没法自检。属性表是纯数据，可以自检。
 */
export function backgroundAttrs(
  view: { x: number; y: number; width: number; height: number },
  background?: string | null
): Record<string, string> | null {
  if (!background) return null
  return {
    x: String(view.x),
    y: String(view.y),
    width: String(view.width),
    height: String(view.height),
    fill: background
  }
}

/** Blob 可被测试与下载复用；MIME 必须带 charset，否则中文标签在部分查看器里乱码。 */
export function svgBlob(svg: string): Blob {
  return new Blob([svg], { type: 'image/svg+xml;charset=utf-8' })
}

export function pngBlob(png: Blob | null): Blob {
  if (!png || png.size === 0) throw new Error('光栅化失败：canvas 未产出 PNG 数据')
  return png
}

// ── 自包含化：把全局 CSS 内联进克隆（浏览器）────────────────────────

/**
 * 导出必须自包含：图元样式写在 app.css 的 .g-* 里，而 .g-* 用的是 var(--rel-s) 这类
 * 自定义属性。单独一个 SVG 文件没有那份样式表，直接序列化出去会是「白底黑线、无颜色
 * 无字号」的残图。所以按计算样式逐个图元内联，导出的就是屏幕上看到的那张图。
 */
const INLINE_PROPS = [
  'fill',
  'stroke',
  'stroke-width',
  'stroke-dasharray',
  'stroke-linecap',
  'stroke-linejoin',
  'fill-opacity',
  'stroke-opacity',
  'opacity',
  'font-family',
  'font-size',
  'font-weight',
  'font-style',
  'letter-spacing',
  'text-anchor',
  'dominant-baseline',
  'rx',
  'ry'
] as const

/** 内联计算样式：源树与克隆树同构，按 childNodes 并行下走。 */
export function inlineComputedStyles(
  source: Element,
  clone: Element,
  win: { getComputedStyle(el: Element): CSSStyleDeclaration }
): void {
  const computed = win.getComputedStyle(source)
  const declarations: string[] = []
  INLINE_PROPS.forEach((prop) => {
    const value = computed.getPropertyValue(prop)
    if (value && value !== 'none' && value !== 'normal' && value !== 'auto') {
      declarations.push(`${prop}:${value}`)
    }
  })
  if (declarations.length) clone.setAttribute('style', declarations.join(';'))

  const sourceChildren = source.childNodes
  const cloneChildren = clone.childNodes
  for (let i = 0; i < sourceChildren.length && i < cloneChildren.length; i += 1) {
    const s = sourceChildren[i]
    const c = cloneChildren[i]
    if (s && c && s.nodeType === 1 && c.nodeType === 1) {
      inlineComputedStyles(s as Element, c as Element, win)
    }
  }
}

/**
 * 去掉只属于操作界面的东西，导出的是「文件」不是「截图」：
 *   - .halo 选中描边（谁被点中是当前会话的状态，不该固化成图的内容）
 *   - is-active / is-panning 这类交互态类名
 *   - aria-* 与指针事件无关的类名保留（.g-* 是画法，必须留）
 */
export function stripUiArtifacts(root: Element): void {
  root.querySelectorAll('.halo').forEach((el) => el.remove())
  const all: Element[] = [root]
  root.querySelectorAll('*').forEach((el) => all.push(el))
  all.forEach((el) => {
    el.classList.remove('is-active')
    el.classList.remove('is-panning')
  })
}

// ── 下载（浏览器）──────────────────────────────────────────────────

/** 触发一次下载并回收 object URL。 */
export function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.rel = 'noopener'
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  // 立刻 revoke 会让部分浏览器来不及读取，留一拍
  window.setTimeout(() => URL.revokeObjectURL(url), 4000)
}

/** 取绘图区当前底色，供 PNG 兜底（深色主题下透明背景在文档里几乎看不清）。 */
export function stageBackground(
  host: Element | null,
  win: { getComputedStyle(el: Element): CSSStyleDeclaration }
): string | null {
  if (!host) return null
  const value = win.getComputedStyle(host).backgroundColor
  return value && value !== 'transparent' && value !== 'rgba(0, 0, 0, 0)' ? value : null
}

/**
 * SVG 字符串 → PNG Blob。
 *
 * 这是全模块唯一无法在 Node 下自检的一段（要真 canvas 与真字体），
 * 故把失败点都收敛成明确的中文报错，页面直接 toast 出去。
 */
export async function rasterize(
  svg: string,
  size: { width: number; height: number },
  background?: string | null
): Promise<Blob> {
  const url = URL.createObjectURL(svgBlob(svg))
  try {
    const image = new Image()
    image.decoding = 'sync'
    await new Promise<void>((resolve, reject) => {
      image.onload = () => resolve()
      image.onerror = () => reject(new Error('光栅化失败：SVG 无法被浏览器解析'))
      image.src = url
    })
    // 字体没就绪就画，文字会退化成 fallback（图和文字对不上）
    if (document.fonts?.ready) await document.fonts.ready

    const canvas = document.createElement('canvas')
    canvas.width = size.width
    canvas.height = size.height
    const ctx = canvas.getContext('2d')
    if (!ctx) throw new Error('光栅化失败：浏览器不支持 canvas 2d')
    if (background) {
      ctx.fillStyle = background
      ctx.fillRect(0, 0, size.width, size.height)
    }
    ctx.drawImage(image, 0, 0, size.width, size.height)

    const png = await new Promise<Blob | null>((resolve) => {
      canvas.toBlob((blob) => resolve(blob), 'image/png')
    })
    return pngBlob(png)
  } finally {
    URL.revokeObjectURL(url)
  }
}
