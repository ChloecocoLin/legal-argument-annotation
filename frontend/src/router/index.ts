import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'

// 成员B负责的页面路由。其余成员页面在联调阶段接入。
const routes: RouteRecordRaw[] = [
  {
    // 成员C：登录页（分工.md §六）。meta.public 是唯一「未登录可进」的页面；
    // meta.blank 让 App.vue 不套工作台外壳（登录页占满整屏，不该有侧栏）。
    path: '/login',
    name: 'login',
    component: () => import('@/features/auth/LoginView.vue'),
    meta: { title: '登录', public: true, blank: true }
  },
  {
    path: '/',
    name: 'home',
    component: () => import('@/features/HomeView.vue'),
    meta: { title: '首页' }
  },
  {
    path: '/guide',
    name: 'guide-management',
    component: () => import('@/features/guide/GuideManagement.vue'),
    meta: { title: '标注指南管理' }
  },
  {
    path: '/guide/:guideId',
    name: 'guide-version',
    component: () => import('@/features/guide/GuideVersion.vue'),
    meta: { title: '指南版本详情' }
  },
  {
    path: '/annotation/workbench',
    name: 'annotation-workbench',
    component: () => import('@/features/annotation/AnnotateWorkbench.vue'),
    meta: { title: '标注工作台' }
  },
  {
    // 成员C：图示审查（分工.md §四.6 的七类逐条审查）
    path: '/annotation/graph-audit',
    name: 'graph-audit',
    component: () => import('@/features/annotation/GraphAudit.vue'),
    meta: { title: '图示审查' }
  },
  {
    // 成员C：教学练习（分工.md §四-7 的五类练习，共用一个页面）
    path: '/teaching/exercises',
    name: 'teaching-exercises',
    component: () => import('@/features/teaching/ExercisePractice.vue'),
    meta: { title: '关系练习' }
  },
  {
    path: '/adjudication/conflict',
    name: 'conflict-resolve',
    component: () => import('@/features/adjudication/ConflictResolve.vue'),
    meta: { title: '冲突解决' }
  },
  {
    // 成员C：冲突解决的第二阶段——关系裁定（J06 + 生成关系裁定结果）
    path: '/adjudication/relation',
    name: 'relation-adjudication',
    component: () => import('@/features/adjudication/RelationAdjudication.vue'),
    meta: { title: '关系裁定' }
  },
  {
    path: '/adjudication/annotate',
    name: 'arbiter-annotate',
    component: () => import('@/features/adjudication/ArbiterAnnotate.vue'),
    meta: { title: '裁定者标注' }
  },
  {
    path: '/expert',
    name: 'expert-review',
    component: () => import('@/features/expert/ExpertReview.vue'),
    meta: { title: '专家复核工作台' }
  },
  {
    path: '/expert/issues',
    name: 'expert-issues',
    component: () => import('@/features/expert/ExpertIssues.vue'),
    meta: { title: '疑难问题说明文档' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/**
 * 登录守卫（分工.md §六）。
 *
 * 判据是**本地有没有令牌**，不去问后端：每个路由都先打一次 `/auth/me` 会让
 * 「点一下等一圈」，而且令牌失效时后端本来就会回 401 —— 拦截器负责那时再清本地并跳登录。
 * 这里只挡「压根没登录就手输 URL」这一种情况。
 *
 * 注意这只是**界面层的便捷措施，不是安全边界**：真正的权限判定在后端
 * （`@PreAuthorize` + 令牌过滤器）。把守卫当防线的人会以为改了 JS 就能越权，其实什么也改不了。
 */
router.beforeEach((to) => {
  if (to.meta.public) return true
  const store = useUserStore()
  if (store.isLoggedIn) return true
  // 记下想去哪，登录后回到那一页；query 本身也带上，避免丢了筛选条件。
  return { name: 'login', query: { redirect: to.fullPath } }
})

router.afterEach((to) => {
  const title = (to.meta.title as string) || ''
  document.title = title ? `${title} · 法律论证标注系统` : '法律论证标注系统'
})

export default router
