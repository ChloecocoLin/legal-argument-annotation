import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { canEnter, homeForRoles } from '@/shared/roleAccess'
import { toast } from '@/shared/toast'

// 成员B负责的页面路由。其余成员页面在联调阶段接入。
//
// meta.roles 是**页面级**的角色声明（RBAC 的界面侧，分工.md §六）。不填 = 登录即可进；
// 真正的权限判定在后端控制器的 @PreAuthorize 上，这里只负责「别让人点进一个注定 403 的页面」。
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
    // R02–R04 写关系限 admin/annotator（与 RelationController 的 @PreAuthorize 一致）
    meta: { title: '标注工作台', roles: ['admin', 'annotator'] }
  },
  {
    // 成员C：图示审查（分工.md §四.6 的七类逐条审查）
    path: '/annotation/graph-audit',
    name: 'graph-audit',
    component: () => import('@/features/annotation/GraphAudit.vue'),
    // GR02 审查是裁定员的活，所以比关系写多一个 arbiter
    meta: { title: '图示审查', roles: ['admin', 'annotator', 'arbiter'] }
  },
  {
    // 成员C：教学练习（分工.md §四-7 的五类练习，共用一个页面）
    path: '/teaching/exercises',
    name: 'teaching-exercises',
    component: () => import('@/features/teaching/ExercisePractice.vue'),
    meta: { title: '关系练习', roles: ['admin', 'teacher', 'student'] }
  },
  {
    path: '/adjudication/conflict',
    name: 'conflict-resolve',
    component: () => import('@/features/adjudication/ConflictResolve.vue'),
    // 命题裁定是裁定员的活；admin 保留全量入口。这一行与 B 的页面一起确认过。
    meta: { title: '冲突解决', roles: ['admin', 'arbiter'] }
  },
  {
    // 成员C：冲突解决的第二阶段——关系裁定（J06 + 生成关系裁定结果）
    path: '/adjudication/relation',
    name: 'relation-adjudication',
    component: () => import('@/features/adjudication/RelationAdjudication.vue'),
    meta: { title: '关系裁定', roles: ['admin', 'arbiter'] }
  },
  {
    path: '/adjudication/annotate',
    name: 'arbiter-annotate',
    component: () => import('@/features/adjudication/ArbiterAnnotate.vue'),
    // 裁定者自定义标注：只有裁定员会用到（B 的页面，角色与其 J06 写接口一致）
    meta: { title: '裁定者标注', roles: ['admin', 'arbiter'] }
  },
  {
    path: '/expert',
    name: 'expert-review',
    component: () => import('@/features/expert/ExpertReview.vue'),
    meta: { title: '专家复核工作台', roles: ['admin', 'expert'] }
  },
  {
    path: '/expert/issues',
    name: 'expert-issues',
    component: () => import('@/features/expert/ExpertIssues.vue'),
    meta: { title: '疑难问题说明文档', roles: ['admin', 'expert'] }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/**
 * 登录 + 角色守卫（分工.md §六）。
 *
 * 判据是**本地有没有令牌**，不去问后端每个路由：令牌失效时后端会回 401，
 * 拦截器负责清本地并跳登录。这里只挡「压根没登录就手输 URL」这一种情况。
 *
 * 角色判定必须在 **loadMe 之后**：刷新页面时本地只有令牌，roles 还是空的，
 * 直接按空角色判断会把所有有权的人一起弹回首页。因此这里 `await loadMe()` 一次；
 * 已有用户信息时 loadMe 会立刻返回，不会每页都打一次 /auth/me。
 *
 * 注意这仍然只是**界面层的便捷措施，不是安全边界**：真正的权限判定在后端
 * （`@PreAuthorize` + 令牌过滤器）。把守卫当防线的人会以为改了 JS 就能越权，其实什么也改不了。
 */
router.beforeEach(async (to) => {
  if (to.meta.public) return true
  const store = useUserStore()
  if (!store.isLoggedIn) {
    // 记下想去哪，登录后回到那一页；query 本身也带上，避免丢了筛选条件。
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (store.roles.length === 0) {
    const ok = await store.loadMe()
    if (!ok) {
      return { name: 'login', query: { redirect: to.fullPath } }
    }
  }
  if (canEnter(store.roles, to.meta.roles)) return true

  toast('当前账号没有这个功能', 'err')
  const home = homeForRoles(store.roles)
  // 落点自身若也被限制（配置写错），退回总览页；总览页不声明 roles，不会再被挡。
  if (to.path === home) return home === '/' ? true : { path: '/' }
  return home
})

router.afterEach((to) => {
  const title = (to.meta.title as string) || ''
  document.title = title ? `${title} · 法律论证标注系统` : '法律论证标注系统'
})

export default router
