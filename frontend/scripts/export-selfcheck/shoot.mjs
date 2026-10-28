// 一条命令跑完导出自检：起静态服务 → 无头 Chrome 渲染 → 出截图。
//
//   npx vite build --config vite.selfcheck.config.ts
//   node scripts/export-selfcheck/shoot.mjs
//
// 截图里应当看到：1) 屏幕上的图 2) exportPng 的产物 3) exportSvg 重排的图
// 4) SVG 源码 5) 逐图元「屏幕 vs 内联」比对 6) 无样式表 iframe 里的导出 SVG。
// 其中第 6 节才证明文件自包含（第 3 节有 app.css 兜着，看不出内联漏没漏）。
// 只要 Chrome/Edge 在，没有任何 npm 依赖。
import { createServer } from 'node:http'
import { spawn } from 'node:child_process'
import { readFile, mkdir, access } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { extname, join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { tmpdir } from 'node:os'

const here = dirname(fileURLToPath(import.meta.url))
const dist = join(here, 'dist')
const PORT = 8899
const SHOT = join(tmpdir(), 'graph-export-selfcheck.png')

const MIME = { '.html': 'text/html;charset=utf-8', '.js': 'text/javascript', '.css': 'text/css' }

if (!existsSync(dist)) {
  console.error('没有找到产物，请先跑：npx vite build --config vite.selfcheck.config.ts')
  process.exit(1)
}

const server = createServer(async (req, res) => {
  const path = (req.url ?? '/').split('?')[0]
  const file = join(dist, path === '/' ? 'index.html' : path)
  try {
    await access(file)
    res.writeHead(200, { 'Content-Type': MIME[extname(file)] ?? 'application/octet-stream' })
    res.end(await readFile(file))
  } catch {
    res.writeHead(404).end('not found')
  }
})

function chromePath() {
  const candidates = [
    process.env.CHROME_PATH,
    'C:/Program Files/Google/Chrome/Application/chrome.exe',
    'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe',
    'C:/Program Files/Microsoft/Edge/Application/msedge.exe',
    'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
  ].filter(Boolean)
  const hit = candidates.find((p) => existsSync(p))
  if (!hit) {
    console.error('没找到 Chrome/Edge，可用 CHROME_PATH 指定可执行文件')
    process.exit(1)
  }
  return hit
}

await new Promise((resolve) => server.listen(PORT, resolve))
const profile = join(tmpdir(), 'graph-export-selfcheck-profile')
await mkdir(profile, { recursive: true })

const child = spawn(
  chromePath(),
  [
    '--headless=new',
    '--disable-gpu',
    '--hide-scrollbars',
    '--no-first-run',
    `--user-data-dir=${profile}`,
    `--screenshot=${SHOT}`,
    '--window-size=1500,2300',
    '--virtual-time-budget=9000',
    `http://localhost:${PORT}/`
  ],
  { stdio: 'inherit' }
)

child.on('exit', (code) => {
  server.close()
  if (code === 0) console.log(`截图已写入 ${SHOT}`)
  process.exit(code ?? 1)
})
