import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import { setUnauthenticatedHandler } from './api/request'
import { useUserStore } from './stores/user'
import './styles/main.css'
import './styles/tokens.css'
import 'element-plus/dist/index.css'
import './styles/app.css'

const app = createApp(App)
const pinia = createPinia()

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(pinia)
app.use(router)
app.use(ElementPlus, { locale: zhCn })

// 登录失效的统一出口（分工.md §六）：拦截器只知道「401 了」，不知道该清哪些内存状态、
// 该跳哪一页，那些判断放在这里。
// 放在 main.ts 还有一个原因：这是唯一同时看得见 router 与 store 又不被它们 import 的地方，
// 否则 request → router → stores/user → api/auth → request 就成了循环依赖。
setUnauthenticatedHandler(() => {
  useUserStore(pinia).reset()
  // 已经在登录页就什么都不做：登录失败的 401 也会走到这里，
  // 跳一下会打断正在输入的表单（而且会把 redirect 冲掉）。
  if (router.currentRoute.value.name === 'login') return
  router.replace({
    name: 'login',
    query: { redirect: router.currentRoute.value.fullPath }
  })
})

app.mount('#app')

// 用本地令牌换回用户信息（刷新页面后顶栏要显示姓名/角色；令牌本身不含这些内容）。
// 故意**不 await**：它只影响顶栏那几个字，不该拦住首屏。令牌已失效时它会 401，
// 上面的处理器会清掉登录态并跳登录页——所以这里不需要写结果判断。
useUserStore(pinia).loadMe()

