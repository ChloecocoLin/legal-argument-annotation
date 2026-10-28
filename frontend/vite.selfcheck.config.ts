import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// 「论证图 SVG / PNG 导出自检」专用构建（分工.md §八.5）。
// 独立于 vite.config.ts：npm run build 的产物不受它影响，它也不进 tsconfig.app.json。
//   npx vite build --config vite.selfcheck.config.ts
// 产物在 scripts/export-selfcheck/dist，用浏览器打开其中的 index.html 即可看结果。
export default defineConfig({
  root: fileURLToPath(new URL('./scripts/export-selfcheck', import.meta.url)),
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  build: {
    outDir: fileURLToPath(new URL('./scripts/export-selfcheck/dist', import.meta.url)),
    emptyOutDir: true,
    // 自检页要看源码，别压缩糊成一行
    minify: false,
    rollupOptions: {
      input: fileURLToPath(new URL('./scripts/export-selfcheck/index.html', import.meta.url))
    }
  }
})
