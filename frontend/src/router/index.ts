import { useUserStore } from '@/stores/user'
import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

// 成员B负责的页面路由。其余成员页面在联调阶段接入。
const routes: RouteRecordRaw[] = [
  {path:'/auto-annotate',component:()=>import('@/features/automation/AutomationCenter.vue'),meta:{title:'自动标注'}},
  {path:'/login',component:()=>import('@/features/auth/LoginView.vue'),meta:{title:'登录工作台',public:true}},
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
    meta: { title: '冲突解决' }
  },
  {
    path: '/adjudication/annotate',
    name: 'arbiter-annotate',
    component: () => import('@/features/adjudication/ArbiterAnnotate.vue'),
    meta: { title: '裁定者自定义标注' }
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

router.beforeEach(async to => {
  if(to.meta.public) return true
  const user=useUserStore()
  if(!user.token || !await user.loadMe()) return {path:'/login',query:{redirect:to.fullPath}}
  return true
})
router.afterEach((to) => {
  const title = (to.meta.title as string) || ''
  document.title = title ? `${title} · 法律论证标注系统` : '法律论证标注系统'
})

export default router
