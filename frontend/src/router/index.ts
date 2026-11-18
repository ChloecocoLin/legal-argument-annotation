import { useUserStore } from '@/stores/user'
import { canEnter, homeForRoles } from '@/shared/roleAccess'
import { toast } from '@/shared/toast'
import type { UserRole } from '@/types/auth'
import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

// 成员B负责的页面路由。其余成员页面在联调阶段接入。
//
// meta.roles 是**页面级**的角色声明（RBAC 的界面侧，分工.md §六）。不填 = 登录即可进；
// 真正的权限判定在后端控制器的 @PreAuthorize 上，这里只负责「别让人点进一个注定 403 的页面」。
const routes: RouteRecordRaw[] = [
  // Specialist features now share D's authenticated case workbench and review flow.
  { path: '/annotation/workbench', redirect: '/tasks' },
  { path: '/annotation/graph-audit', redirect: '/final-review' },
  { path: '/adjudication/relation', redirect: to => ({ path: '/adjudication/conflict', query: to.query }) },
  { path: '/teaching/exercises', redirect: '/teaching' },
  {path:'/teaching',component:()=>import('@/features/teaching/TeachingHub.vue'),props:{page:'home'},meta:{title:'教学工作台'}},
  {path:'/assignment-create',component:()=>import('@/features/teaching/TeachingHub.vue'),props:{page:'assignment'},meta:{title:'创建教学作业'}},
  {path:'/teaching-standard',component:()=>import('@/features/teaching/TeachingHub.vue'),props:{page:'standard'},meta:{title:'标准标注样例'}},
  {path:'/student-results',component:()=>import('@/features/teaching/TeachingHub.vue'),props:{page:'results'},meta:{title:'学生结果总览'}},
  {path:'/student-compare',component:()=>import('@/features/teaching/TeachingHub.vue'),props:{page:'compare'},meta:{title:'作答比对与反馈'}},
  {path:'/student-annotate/:assignmentId?',component:()=>import('@/features/teaching/StudentPractice.vue'),meta:{title:'学生标注练习'}},
  {path:'/auto-annotate',component:()=>import('@/features/automation/AutomationCenter.vue'),meta:{title:'自动标注'}},
  {path:'/login',name:'login',component:()=>import('@/features/auth/LoginView.vue'),meta:{title:'登录工作台',public:true}},
  {path:'/documents',component:()=>import('@/features/document/DocumentLibrary.vue'),meta:{title:'文书总库'}},
  {path:'/documents/:documentId',component:()=>import('@/features/document/DocumentDetail.vue'),meta:{title:'文书详情'}},
  {path:'/tasks/create',component:()=>import('@/features/task/TaskCreate.vue'),meta:{title:'创建任务'}},
  {path:'/tasks/:taskId/assign',component:()=>import('@/features/task/TaskAssign.vue'),meta:{title:'任务分配'}},
  {path:'/tasks/:taskId/progress',component:()=>import('@/features/versions/TaskProgress.vue'),meta:{title:'版本进度'}},
  { path: '/results', component: () => import('@/features/results/ResultLibrary.vue'), meta: {title:'已入库成果'} },
  { path: '/tasks/:taskId/cases/:caseId/annotate', component: () => import('@/features/annotate/DraftWorkbench.vue'), meta: {title:'本人标注工作台'} },
  { path: '/segmentation', component: () => import('@/features/annotate/IndependentSegmentation.vue'), meta: {title:'独立切割与统一关口'} },
  { path: '/tasks/:taskId/cases/:caseId/segmentation', component: () => import('@/features/annotate/IndependentSegmentation.vue'), meta: {title:'独立切割与统一关口'} },
  { path: '/audit', name: 'audit-events', component: () => import('@/features/audit/AuditEvents.vue'), meta: { title: '入库与审计记录' } },
  { path: '/admin-home', name: 'admin-home', component: () => import('@/features/dashboard/AdminHome.vue'), meta: { title: '管理工作台' } },
  { path: '/export', name: 'export', component: () => import('@/features/export/ExportCenter.vue'), meta: { title: '导出中心' } },
  { path: '/export-records', name: 'export-records', component: () => import('@/features/export/ExportRecords.vue'), meta: { title: '导出记录' } },
  { path: '/final-review', name: 'final-review-entry', component: () => import('@/features/final-review/FinalReview.vue'), meta: { title: '最终版本审查' } },
  { path: '/final-review/:finalVersionId', name: 'final-review', component: () => import('@/features/final-review/FinalReview.vue'), meta: { title: '最终版本审查' } },
  { path: '/tasks', name: 'task-list', component: () => import('@/features/task/TaskList.vue'), meta: { title: '任务进度' } },
  { path: '/tasks/:taskId', name: 'task-detail', component: () => import('@/features/task/TaskDetail.vue'), meta: { title: '任务详情' } },
  { path: '/tasks/:taskId/cases/:caseId/versions', name: 'annotation-versions', component: () => import('@/features/versions/VersionList.vue'), meta: { title: '标注版本' } },
  { path: '/versions/:versionId', name: 'annotation-version', component: () => import('@/features/versions/VersionDetail.vue'), meta: { title: '完整标注版本' } },
  { path: '/annotations/:draftId/submit', name: 'annotation-submit', component: () => import('@/features/versions/SubmitAnnotation.vue'), meta: { title: '提交标注' } },
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
    path: '/adjudication/conflict',
    name: 'conflict-resolve',
    component: () => import('@/features/adjudication/ConflictResolve.vue'),
    // 命题裁定是裁定员的活；admin 保留全量入口。这一行与 B 的页面一起确认过。
    meta: { title: '冲突解决', roles: ['admin', 'arbiter'] }
  },
  {
    path: '/adjudication/annotate',
    name: 'arbiter-annotate',
    component: () => import('@/features/adjudication/ArbiterAnnotate.vue'),
    meta: { title: '裁定者自定义标注', roles: ['admin', 'arbiter'] }
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
 * 判据是本地有没有令牌；令牌失效时后端会回 401，拦截器负责清本地并跳登录。
 * 角色判定必须在 loadMe 之后：刷新页面时本地只有令牌，roles 还是空的。
 */
router.beforeEach(async (to) => {
  if (to.meta.public) return true
  const store = useUserStore()
  if (!store.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (store.roles.length === 0) {
    const ok = await store.loadMe()
    if (!ok) {
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }
  if (canEnter(store.roles, to.meta.roles as UserRole[] | undefined)) return true

  toast('当前账号没有这个功能', 'err')
  const home = homeForRoles(store.roles)
  if (to.path === home) return home === '/' ? true : { path: '/' }
  return home
})
router.afterEach((to) => {
  const title = (to.meta.title as string) || ''
  document.title = title ? `${title} · 法律论证标注系统` : '法律论证标注系统'
})

export default router
