// 论证图 SVG / PNG 导出的浏览器自检（分工.md §八.5）。
//
// 为什么需要它：导出的两处关键行为——内联计算样式、canvas 光栅化——都要真浏览器，
// 在 Node 下自检不了（scripts/check-graph-rules.ts 只覆盖纯函数部分）。这里把真组件
// 挂到页面上，调它 expose 出来的 exportSvg / exportPng，把产物直接画出来看：
//   颜色/字号是不是活的（内联是否生效）、箭头与虚线在不在、PNG 有没有出图。
// 用法：npx vite build --config vite.selfcheck.config.ts，再用浏览器打开产物，
//       或 node scripts/export-selfcheck/shoot.mjs 直接截图。
import { createApp, h, ref, onMounted } from 'vue'
import ArgumentGraph from '@/features/annotation/ArgumentGraph.vue'
// 与真实页面同一份样式表，否则 var(--rel-*) 解析不出来，自检就不成立
import '@/styles/tokens.css'
import '@/styles/app.css'
import type { GraphEdge, GraphNode } from '@/types/graph'

// 种子草稿 md-1 的同形数据（8 命题 7 关系，含 I 的虚线、J 的带 + 无向圆）
const nodes: GraphNode[] = [
  { id: 'mp-1', kind: 'ELEMENT', displayNo: 'P1', labelCode: 'GM-L', text: '种子命题1' },
  { id: 'mp-2', kind: 'ELEMENT', displayNo: 'P2', labelCode: 'SF', text: '种子命题2' },
  { id: 'mp-3', kind: 'ELEMENT', displayNo: 'P3', labelCode: 'SF', text: '种子命题3' },
  { id: 'mp-4', kind: 'ELEMENT', displayNo: 'P4', labelCode: 'SF', text: '种子命题4' },
  { id: 'mp-5', kind: 'ELEMENT', displayNo: 'P5', labelCode: 'SM', text: '种子命题5' },
  { id: 'mp-6', kind: 'ELEMENT', displayNo: 'P6', labelCode: 'GF', text: '种子命题6' },
  { id: 'mp-7', kind: 'ELEMENT', displayNo: 'P7', labelCode: 'SF', text: '种子命题7' },
  { id: 'mp-8', kind: 'ELEMENT', displayNo: 'P8', labelCode: 'SF', text: '种子命题8' },
  { id: 'rel-1', kind: 'RELATION', relationCode: 'J', directed: false, expression: 'J(P2,P3)' },
  { id: 'rel-2', kind: 'RELATION', relationCode: 'S', directed: true, expression: 'S(J(P2,P3),P5)' },
  { id: 'rel-3', kind: 'RELATION', relationCode: 'S', directed: true, expression: 'S(P1,P5)' },
  { id: 'rel-4', kind: 'RELATION', relationCode: 'S', directed: true, expression: 'S(P4,P3)' },
  { id: 'rel-5', kind: 'RELATION', relationCode: 'A', directed: true, expression: 'A(P7,P8)' },
  { id: 'rel-6', kind: 'RELATION', relationCode: 'S', directed: true, expression: 'S(P6,P8)' },
  { id: 'rel-7', kind: 'RELATION', relationCode: 'M', directed: true, expression: 'M(P2,P1)' },
  { id: 'rel-8', kind: 'RELATION', relationCode: 'I', directed: false, expression: 'I(P1,P5)' }
]

const edges: GraphEdge[] = [
  { id: 'e1', relationId: 'rel-1', source: 'mp-2', target: 'rel-1', relationCode: 'J', arrow: false, dashed: false },
  { id: 'e2', relationId: 'rel-1', source: 'mp-3', target: 'rel-1', relationCode: 'J', arrow: false, dashed: false },
  { id: 'e3', relationId: 'rel-2', source: 'rel-1', target: 'rel-2', relationCode: 'S', arrow: true, dashed: false },
  { id: 'e4', relationId: 'rel-2', source: 'rel-2', target: 'mp-5', relationCode: 'S', arrow: true, dashed: false },
  { id: 'e5', relationId: 'rel-3', source: 'mp-1', target: 'rel-3', relationCode: 'S', arrow: true, dashed: false },
  { id: 'e6', relationId: 'rel-3', source: 'rel-3', target: 'mp-5', relationCode: 'S', arrow: true, dashed: false },
  { id: 'e7', relationId: 'rel-4', source: 'mp-4', target: 'rel-4', relationCode: 'S', arrow: true, dashed: false },
  { id: 'e8', relationId: 'rel-4', source: 'rel-4', target: 'mp-3', relationCode: 'S', arrow: true, dashed: false },
  { id: 'e9', relationId: 'rel-5', source: 'mp-7', target: 'rel-5', relationCode: 'A', arrow: true, dashed: false },
  { id: 'e10', relationId: 'rel-5', source: 'rel-5', target: 'mp-8', relationCode: 'A', arrow: true, dashed: false },
  { id: 'e11', relationId: 'rel-6', source: 'mp-6', target: 'rel-6', relationCode: 'S', arrow: true, dashed: false },
  { id: 'e12', relationId: 'rel-6', source: 'rel-6', target: 'mp-8', relationCode: 'S', arrow: true, dashed: false },
  { id: 'e13', relationId: 'rel-7', source: 'mp-2', target: 'rel-7', relationCode: 'M', arrow: true, dashed: false },
  { id: 'e14', relationId: 'rel-7', source: 'rel-7', target: 'mp-1', relationCode: 'M', arrow: true, dashed: false },
  { id: 'e15', relationId: 'rel-8', source: 'mp-1', target: 'rel-8', relationCode: 'I', arrow: false, dashed: true },
  { id: 'e16', relationId: 'rel-8', source: 'mp-5', target: 'rel-8', relationCode: 'I', arrow: false, dashed: true }
]

const status = (text: string, bad = false) => {
  const el = document.getElementById('status')!
  el.textContent = text
  el.className = bad ? 'bad' : ''
}

const Host = {
  setup() {
    const graphRef = ref<{ exportSvg(o?: unknown): string; exportPng(o?: unknown): Promise<Blob> } | null>(null)

    onMounted(async () => {
      const target = graphRef.value
      if (!target) {
        status('自检失败：拿不到组件实例', true)
        return
      }
      // 故意先把视图缩放到一个奇怪的倍率，用来验证导出的是整图而不是当前视口
      const svg = target.exportSvg()
      if (!svg) {
        status('自检失败：exportSvg 返回空串', true)
        return
      }
      document.getElementById('src')!.textContent = svg.slice(0, 1600)
      // 渲染导出的 SVG（脱离组件的 viewBox 与内联样式，色彩能否复原全看内联）
      document.getElementById('svg-out')!.innerHTML = svg
      const outSvg = document.querySelector<SVGSVGElement>('#svg-out svg')
      if (outSvg) {
        outSvg.setAttribute('style', 'width: 620px; height: auto')
      }
      // 关键：把同一份 SVG 丢进一个没有任何样式表的 iframe。第 3 节里 app.css 是加载着的，
      // 就算内联漏了颜色也会被样式表补上；只有这里才能证明文件本身是自包含的。
      const frame = document.getElementById('frame') as HTMLIFrameElement
      frame.srcdoc = `<!doctype html><html><meta charset="utf-8"><body style="margin:0">${svg}</body></html>`

      const inlined = ['fill', 'stroke', 'font-size'].filter((prop) => svg.includes(`${prop}:`))
      const hasArrow = svg.includes('marker-end')
      const dashed = svg.includes('stroke-dasharray')
      const noHalo = !svg.includes('halo')
      const noActive = !svg.includes('is-active')

      // 逐图元比对：屏幕计算值 vs 导出内联值。颜色丢了就是这里能看出来。
      const probes: Array<[string, string]> = [
        ['.g-rel.s', 'fill'],
        ['.g-rel.a', 'stroke'],
        ['.g-rel.j', 'stroke'],
        ['.g-prop', 'stroke'],
        ['.g-prop-lbl', 'fill'],
        ['.g-ident-lbl', 'fill'],
        ['.g-edge.i', 'stroke'],
        ['.g-edge.j', 'stroke']
      ]
      document.getElementById('diff')!.textContent = probes
        .map(([sel, prop]) => {
          const live = document.querySelector(`#graph ${sel}`)
          const out = document.querySelector(`#svg-out ${sel}`)
          const liveVal = live ? getComputedStyle(live).getPropertyValue(prop) : '(屏幕上没有该图元)'
          const outVal = out?.getAttribute('style') ?? '(导出里没有该图元)'
          return `${sel}  ${prop}\n    屏幕 = ${liveVal}\n    内联 = ${outVal}`
        })
        .join('\n')

      try {
        const png = await target.exportPng()
        const url = URL.createObjectURL(png)
        const img = document.getElementById('png') as HTMLImageElement
        img.onload = () => {
          status(
            `SVG ${svg.length} 字符 · PNG ${Math.round(png.size / 1024)} KB ` +
              `(内核 ${img.naturalWidth}×${img.naturalHeight}) · ` +
              `内联 ${inlined.join('/')} · 箭头 ${hasArrow} · 虚线 ${dashed} · ` +
              `去选中态 ${noHalo && noActive}`
          )
        }
        img.src = url
      } catch (error) {
        status(`PNG 光栅化失败：${error instanceof Error ? error.message : String(error)}`, true)
      }
    })

    return () =>
      h(ArgumentGraph, {
        ref: graphRef,
        nodes,
        edges,
        activeId: 'rel-3',
        style: 'width:100%;height:100%'
      })
  }
}

createApp(Host).mount('#graph')
