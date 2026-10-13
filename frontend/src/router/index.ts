import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

// 成员B负责的页面路由。其余成员页面在联调阶段接入。
const routes: RouteRecordRaw[] = [
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

router.afterEach((to) => {
  const title = (to.meta.title as string) || ''
  document.title = title ? `${title} · 法律论证标注系统` : '法律论证标注系统'
})

export default router
