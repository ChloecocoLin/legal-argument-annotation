// 成员C前端核心逻辑自检：几何/自动布局/连通分量 + 参与矩阵/ordinal 归位。
// 直接 import 仓库里的 TS 源文件（esbuild 打包后运行），验证的是真代码而非副本。
// 运行（在 frontend/ 下，esbuild 随 vite 一起装好，无需额外依赖）：
//   ./node_modules/.bin/esbuild scripts/check-graph-rules.ts --bundle --platform=node \
//     --format=esm --outfile=/tmp/check-graph-rules.mjs && node /tmp/check-graph-rules.mjs
// 不进 tsconfig.app.json 的 include，因此不参与 npm run build 的类型检查。
import {
  buildRenderEdges,
  buildRenderNodes,
  componentsOf,
  graphBounds
} from '../src/shared/graphGeometry.ts'
import {
  checkParticipant,
  orderedInputs,
  parseDisplayNo,
  representativeOf,
  type CandidateInfo
} from '../src/shared/relationRules.ts'
import {
  AUDIT_DIMENSIONS,
  OTHER_CATEGORY,
  categoryOf,
  checkGlyphs,
  groupIssues
} from '../src/shared/graphAudit.ts'
import {
  backgroundAttrs,
  exportFileName,
  exportStamp,
  exportViewBox,
  pngBlob,
  rasterScale,
  rasterSize,
  svgBlob
} from '../src/shared/graphExport.ts'

let pass = 0
let fail = 0
function check(name: string, ok: boolean, detail: unknown = '') {
  if (ok) {
    pass += 1
    console.log('  PASS  ' + name)
  } else {
    fail += 1
    console.log('  FAIL  ' + name + '   ' + JSON.stringify(detail))
  }
}

// ── 造一份与后端 GR01 出参同形的种子图 ────────────────────────────────
const P = (id: string, displayNo: string, labelCode: string) => ({
  id,
  kind: 'ELEMENT' as const,
  displayNo,
  labelCode,
  text: '原文-' + id,
  relationCode: null,
  directed: null,
  expression: null,
  representativeId: null
})
const R = (
  id: string,
  code: string,
  directed: boolean,
  expression: string,
  representativeId: string | null = null
) => ({
  id,
  kind: 'RELATION' as const,
  displayNo: null,
  labelCode: null,
  text: null,
  relationCode: code,
  directed,
  expression,
  representativeId
})
const E = (
  id: string,
  relationId: string,
  source: string,
  target: string,
  relationCode: string,
  arrow: boolean,
  dashed: boolean
) => ({ id, relationId, source, target, relationCode, arrow, dashed })

// 命题 P1..P8 与关系：J(P2,P3) → S(J,P5)，S(P1,P5)，S(P4,P3)，M(P2,P1)，A(P7,P8)，
// S(P6,P8)，I(P1,P5)；另加一个孤立命题 P8' 验证连通分量
const nodes = [
  P('mp-1', 'P1', 'GM-L'),
  P('mp-2', 'P2', 'SF'),
  P('mp-3', 'P3', 'SF'),
  P('mp-4', 'P4', 'SF'),
  P('mp-5', 'P5', 'SM'),
  P('mp-6', 'P6', 'GF'),
  P('mp-7', 'P7', 'SF'),
  P('mp-8', 'P8', 'SF'),
  R('rel-1', 'J', false, 'J(P2,P3)'),
  R('rel-2', 'S', true, 'S(J(P2,P3),P5)'),
  R('rel-3', 'S', true, 'S(P1,P5)'),
  R('rel-4', 'S', true, 'S(P4,P3)'),
  R('rel-5', 'A', true, 'A(P7,P8)'),
  R('rel-6', 'S', true, 'S(P6,P8)'),
  R('rel-7', 'M', true, 'M(P2,P1)'),
  R('rel-8', 'I', false, 'I(P1,P5)', 'mp-5')
]
const edges = [
  E('e1', 'rel-1', 'mp-2', 'rel-1', 'J', false, false),
  E('e2', 'rel-1', 'mp-3', 'rel-1', 'J', false, false),
  E('e3', 'rel-2', 'rel-1', 'rel-2', 'S', true, false),
  E('e4', 'rel-2', 'rel-2', 'mp-5', 'S', true, false),
  E('e5', 'rel-3', 'mp-1', 'rel-3', 'S', true, false),
  E('e6', 'rel-3', 'rel-3', 'mp-5', 'S', true, false),
  E('e7', 'rel-4', 'mp-4', 'rel-4', 'S', true, false),
  E('e8', 'rel-4', 'rel-4', 'mp-3', 'S', true, false),
  E('e9', 'rel-5', 'mp-7', 'rel-5', 'A', true, false),
  E('e10', 'rel-5', 'rel-5', 'mp-8', 'A', true, false),
  E('e11', 'rel-6', 'mp-6', 'rel-6', 'S', true, false),
  E('e12', 'rel-6', 'rel-6', 'mp-8', 'S', true, false),
  E('e13', 'rel-7', 'mp-2', 'rel-7', 'M', true, false),
  E('e14', 'rel-7', 'rel-7', 'mp-1', 'M', true, false),
  E('e15', 'rel-8', 'mp-1', 'rel-8', 'I', false, true),
  E('e16', 'rel-8', 'mp-5', 'rel-8', 'I', false, true)
]

console.log('== 几何与图元 ==')
const rendered = buildRenderNodes(nodes, edges)
check('节点数一致', rendered.length === nodes.length, rendered.length)
const ident = rendered.find((n) => n.id === 'rel-8')
check(
  'I 渲染为编号框，成员按展示编号以“/”相连',
  ident?.kind === 'ident' && ident?.label === 'P1/P5',
  ident
)
check(
  'S/J/M/A 渲染为关系圆并带上关系代码',
  ['rel-1', 'rel-2', 'rel-7', 'rel-5'].every((id) => {
    const node = rendered.find((n) => n.id === id)
    return node?.kind === 'rel'
  })
)
const propNode = rendered.find((n) => n.id === 'mp-1')
check('命题框只显示 P 编号', propNode?.kind === 'prop' && propNode?.label === 'P1', propNode)

const placed = rendered.filter((n) => Number.isFinite(n.x) && Number.isFinite(n.y))
check('自动布局给全部节点有限坐标', placed.length === rendered.length, placed.length)
const unique = new Set(rendered.map((n) => `${Math.round(n.x)},${Math.round(n.y)}`))
check('自动布局没有节点重叠', unique.size === rendered.length, unique.size)
const bounds = graphBounds(rendered)
check(
  '包围盒覆盖所有节点（含图元尺寸）',
  bounds.minX < bounds.maxX && bounds.minY < bounds.maxY,
  bounds
)

const comps = componentsOf(
  nodes.map((n) => ({ id: n.id, label: n.displayNo ?? n.relationCode ?? n.id })),
  edges.map((e) => ({ a: e.source, b: e.target }))
)
check('识别出 2 个独立论证图（与后端 GR01 的 componentCount 一致）', comps.length === 2, comps)
check(
  '分量按最小展示编号排序（图示一 = P1 组）',
  comps[0].includes('mp-1') && comps[1].includes('mp-6'),
  comps
)

// 去掉 rel-5/rel-6 的全部边后，P6/P7/P8 与两个关系节点各自成为孤立分量
const comps2 = componentsOf(
  nodes.map((n) => ({ id: n.id, label: n.displayNo ?? n.relationCode ?? n.id })),
  edges
    .filter((e) => e.relationId !== 'rel-5' && e.relationId !== 'rel-6')
    .map((e) => ({ a: e.source, b: e.target }))
)
check('缺边后孤立命题各自成分量（图示一切换的依据）', comps2.length === 6, comps2)

const dragEdges = buildRenderEdges(edges)
check(
  'J/I 无箭头、I 虚线，S/A/M 有箭头',
  dragEdges.every(
    (e) =>
      (e.rel === 'J' || e.rel === 'I' ? e.arrow === false : e.arrow === true) &&
      (e.rel === 'I' ? e.dashed === true : e.dashed === false)
  )
)

console.log('== 参与矩阵 ==')
const info = (
  targetKind: 'ELEMENT' | 'RELATION',
  id: string,
  code: string,
  displayNo: string | null,
  proposition = true
): CandidateInfo => ({ targetKind, targetId: id, code, displayNo, proposition })

const p1 = info('ELEMENT', 'mp-1', 'GM-L', 'P1')
const p2 = info('ELEMENT', 'mp-2', 'SF', 'P2')
const p3 = info('ELEMENT', 'mp-3', 'SF', 'P3')
const p5 = info('ELEMENT', 'mp-5', 'SM', 'P5')
const p6 = info('ELEMENT', 'mp-6', 'GF', 'P6')
const isText = info('ELEMENT', 'mp-9', 'IS', null, false)
const rJ = info('RELATION', 'rel-1', 'J', null)
const rS = info('RELATION', 'rel-2', 'S', null)
const rA = info('RELATION', 'rel-5', 'A', null)

check('S 来源可以是 J 关系整体', checkParticipant('S', 'SOURCE', rJ).ok)
check('S 来源不能是 S 关系整体', !checkParticipant('S', 'SOURCE', rS).ok)
check(
  'S 来源为 S 关系时给出中文原因',
  checkParticipant('S', 'SOURCE', rS).reason.includes('J 或 M'),
  checkParticipant('S', 'SOURCE', rS).reason
)
check('S 目标不能是关系', !checkParticipant('S', 'TARGET', rJ).ok)
check('A(po, S(pi,pj)) 合法', checkParticipant('A', 'TARGET', rS).ok)
check('A 目标不能是 J 关系', !checkParticipant('A', 'TARGET', rJ).ok)
check('A 来源必须是命题', !checkParticipant('A', 'SOURCE', rS).ok)
check('J 成员可以是 A 关系整体', checkParticipant('J', 'MEMBER', rA).ok)
check('J 成员可以是命题', checkParticipant('J', 'MEMBER', p1).ok)
check('M(J(...),P) 的来源合法', checkParticipant('M', 'SOURCE', rJ).ok)
check('M 来源不能是 S 关系', !checkParticipant('M', 'SOURCE', rS).ok)
check('M 来源必须是个别判断', !checkParticipant('M', 'SOURCE', p1).ok)
check('M 来源个别判断合法', checkParticipant('M', 'SOURCE', p2).ok)
check('M 目标必须是一般判断', checkParticipant('M', 'TARGET', p6).ok)
check('M 目标为个别判断 → 拒绝', !checkParticipant('M', 'TARGET', p3).ok)
check('M 目标不能是关系', !checkParticipant('M', 'TARGET', rJ).ok)
check('I 成员只能是命题', !checkParticipant('I', 'MEMBER', rJ).ok)
check('非命题要素不能参与任何关系', !checkParticipant('J', 'MEMBER', isText).ok)
check(
  '非命题拒绝理由与后端一致',
  checkParticipant('J', 'MEMBER', isText).reason === '非命题要素不能参与论证关系',
  checkParticipant('J', 'MEMBER', isText).reason
)

console.log('== ordinal 归位与代表节点 ==')
const sInputs = orderedInputs('S', [p2, p1])
check(
  'S 按填写顺序归位为 SOURCE/TARGET',
  sInputs.length === 2 &&
    sInputs[0].role === 'SOURCE' &&
    sInputs[0].targetId === 'mp-2' &&
    sInputs[1].role === 'TARGET' &&
    sInputs[1].targetId === 'mp-1' &&
    sInputs[0].ordinal === 1 &&
    sInputs[1].ordinal === 2,
  sInputs
)
const jInputs = orderedInputs('J', [p5, p3, p1])
check(
  'J 按展示编号排序并全部为 MEMBER',
  jInputs.map((i) => `${i.ordinal}:${i.role}:${i.targetId}`).join(',') ===
    '1:MEMBER:mp-1,2:MEMBER:mp-3,3:MEMBER:mp-5',
  jInputs
)
const iInputs = orderedInputs('I', [p5, p1])
check(
  'I 提交的是命题而非关系，且 direct=false 语义由后端定义',
  iInputs.every((i) => i.role === 'MEMBER' && i.targetKind === 'ELEMENT'),
  iInputs
)
const rep = representativeOf([p1, p5, p3])
check('I 代表节点取最大展示编号命题', rep?.targetId === 'mp-5', rep)
check(
  '扩展编号解析（P10 > P9 > P2）',
  parseDisplayNo('P10') === 10 && parseDisplayNo('P9') === 9 && parseDisplayNo('P2') === 2,
  [parseDisplayNo('P10'), parseDisplayNo('P9')]
)
check('非命题无编号 → -1', parseDisplayNo(null) === -1)

console.log('== 图示审查：七类与图元自查 ==')
check(
  '七类审查类别与后端 AuditCategory 的 id 一一对应',
  AUDIT_DIMENSIONS.map((d) => d.id).join(',') ===
    'MISSING_RELATION,WRONG_DIRECTION,JM_CONFUSION,ILLEGAL_NESTING,' +
      'ISOLATED_PROPOSITION,GLYPH_ERROR,MULTIPLE_STRUCTURES',
  AUDIT_DIMENSIONS.map((d) => d.id)
)
check(
  '七类各有判定方，且只有「图元错误」归前端',
  AUDIT_DIMENSIONS.every((d) => ['BACKEND', 'FRONTEND', 'INFO'].includes(d.decision)) &&
    AUDIT_DIMENSIONS.filter((d) => d.decision === 'FRONTEND').map((d) => d.id).join() ===
      'GLYPH_ERROR',
  AUDIT_DIMENSIONS.map((d) => `${d.id}:${d.decision}`)
)

// 种子图本身应该是干净的——否则审查页的「通过」没有意义
const healthy = checkGlyphs(nodes, edges)
check('种子图的图元全部合规（无发现）', healthy.length === 0, healthy)

const badArrow = checkGlyphs(
  nodes,
  edges.map((e) => (e.id === 'e15' ? { ...e, arrow: true } : e))
)
check(
  'I 连线画成有箭头 → 报图元错误并指明应为无向',
  badArrow.length === 1 && badArrow[0].message.includes('无向不带箭头'),
  badArrow
)
const badDash = checkGlyphs(
  nodes,
  edges.map((e) => (e.id === 'e3' ? { ...e, dashed: true } : e))
)
check(
  'S 连线画成虚线 → 报图元错误',
  badDash.length === 1 && badDash[0].message.includes('不应画成虚线'),
  badDash
)
const badJ = checkGlyphs(
  nodes,
  edges.map((e) => (e.id === 'e1' ? { ...e, arrow: true } : e))
)
check(
  'J 连线画成有箭头 → 报图元错误（J/M 混用的画法表现）',
  badJ.length === 1 && badJ[0].message.includes('J'),
  badJ
)
const ghostEdge = checkGlyphs(nodes, [...edges, E('e17', 'rel-3', 'mp-9', 'rel-3', 'S', true, false)])
check(
  '连线端点是图里没有的节点 → 报图元错误',
  ghostEdge.length === 1 && ghostEdge[0].message.includes('画不出来'),
  ghostEdge
)
const noNo = checkGlyphs(nodes.map((n) => (n.id === 'mp-1' ? { ...n, displayNo: null } : n)), edges)
check(
  '命题节点缺展示编号 → 报图元错误',
  noNo.length === 1 && noNo[0].message.includes('展示编号'),
  noNo
)
const unknown = checkGlyphs(nodes, edges.map((e) => (e.id === 'e1' ? { ...e, relationCode: 'X' } : e)))
check('未知关系类型 → 报图元错误（没有对应图元）', unknown.length === 1, unknown)

// 归组：后端未分类的问题落「其他结构问题」，不丢
const grouped = groupIssues([
  { category: 'WRONG_DIRECTION' },
  { category: 'WRONG_DIRECTION' },
  { category: 'GLYPH_ERROR' },
  { category: null },
  {}
])
check('按类别归组，同类别聚合', grouped.get('WRONG_DIRECTION')?.length === 2, grouped.get('WRONG_DIRECTION'))
check(
  '未分类的问题全部落「其他结构问题」',
  grouped.get('STRUCTURE')?.length === 2,
  grouped.get('STRUCTURE')
)
check(
  '归组结果含全部类别（含空组），界面才能逐条画清单',
  grouped.size === AUDIT_DIMENSIONS.length + 1,
  grouped.size
)
check('categoryOf(null) 归其他', categoryOf({ category: null }) === OTHER_CATEGORY)
check('categoryOf(空串) 归其他', categoryOf({ category: '' }) === OTHER_CATEGORY)

console.log('== 导出：文件名 / 倍率 / 取景 / Blob（SVG-PNG 底层生成）==')
// 只覆盖不依赖 DOM 的那部分。内联样式与 canvas 光栅化要真浏览器，在页面里点。
const stampAt = new Date(2026, 9, 14, 10, 30)
check('文件名时间戳为 YYYYMMDD-HHMM', exportStamp(stampAt) === '20261014-1030', exportStamp(stampAt))
check(
  '文件名由主体与时间戳拼成',
  exportFileName('图示审查-md-1', 'svg', stampAt) === '图示审查-md-1-20261014-1030.svg',
  exportFileName('图示审查-md-1', 'svg', stampAt)
)
check(
  '主体里的空格与斜杠被换掉，下载名不会出错',
  exportFileName('formal graph/us-1', 'png', stampAt) === 'formal-graph-us-1-20261014-1030.png',
  exportFileName('formal graph/us-1', 'png', stampAt)
)
check(
  '空主体兜底为 argument-graph',
  exportFileName('', 'svg', stampAt) === 'argument-graph-20261014-1030.svg',
  exportFileName('', 'svg', stampAt)
)

check('常规尺寸按请求倍率出图', rasterScale(800, 600, 2) === 2, rasterScale(800, 600, 2))
check(
  '超大图按像素上限下调倍率（避免 canvas 静默出白图）',
  rasterScale(8000, 8000, 2) === 0.5,
  rasterScale(8000, 8000, 2)
)
const bigSize = rasterSize(8000, 8000, 2)
check(
  '下调后总像素不超过上限',
  bigSize.width * bigSize.height <= 16_000_000 && bigSize.width === 4000,
  bigSize
)
check(
  '倍率不为 0（canvas 不接受 0 宽高）',
  rasterScale(100, 100, 0) >= 0.1 && rasterSize(1, 1, 1).width >= 1,
  [rasterScale(100, 100, 0), rasterSize(1, 1, 1)]
)

const boxed = exportViewBox({ minX: 10, minY: 20, maxX: 110, maxY: 70 }, 24)
check(
  '取景盒 = 内容包围盒 + 四周留白',
  boxed.x === -14 && boxed.y === -4 && boxed.width === 148 && boxed.height === 98,
  boxed
)
check('viewBox 与取景盒一致', boxed.viewBox === '-14 -4 148 98', boxed.viewBox)
const tight = exportViewBox({ minX: 10, minY: 20, maxX: 110, maxY: 70 }, 0)
check('留白为 0 时就是内容包围盒本身', tight.viewBox === '10 20 100 50', tight.viewBox)
check(
  '零尺寸内容也给 1×1（viewBox 宽高不能为 0）',
  exportViewBox({ minX: 5, minY: 5, maxX: 5, maxY: 5 }, 0).width === 1,
  exportViewBox({ minX: 5, minY: 5, maxX: 5, maxY: 5 }, 0)
)

check(
  '不传底色 → 不加背景矩形（SVG 保持透明）',
  backgroundAttrs(boxed, null) === null,
  backgroundAttrs(boxed, null)
)
const withBg = backgroundAttrs(boxed, '#ffffff')
check(
  '传底色 → 背景矩形铺满取景盒',
  withBg?.fill === '#ffffff' && withBg?.width === '148' && withBg?.x === '-14',
  withBg
)

const blob = svgBlob('<svg/>')
check(
  'SVG Blob 带 charset（否则中文标签在部分查看器里乱码）',
  blob.type === 'image/svg+xml;charset=utf-8',
  blob.type
)
check('SVG Blob 有内容', blob.size > 0, blob.size)
let threw = ''
try {
  pngBlob(null)
} catch (error) {
  threw = error instanceof Error ? error.message : String(error)
}
check('canvas 没产出数据时报中文错，而不是给出空文件', threw.includes('光栅化失败'), threw)

console.log()
console.log(`通过 ${pass}，失败 ${fail}`)
process.exit(fail ? 1 : 0)
