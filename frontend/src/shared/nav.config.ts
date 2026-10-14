// 侧边导航。path 为空表示该模块由其他成员负责、当前未接入。
// 固定以 admin 角色视角展示（M1）。
import type { UserRole } from '@/types/auth'

export interface NavItem {
  id: string
  label: string
  icon: string
  path?: string
  /**
   * 允许看到这一项的角色。**不填 = 所有登录用户都看得到。**
   *
   * 只给 C 的页面填了（分工.md §六的 RBAC 矩阵）。这纯粹是**界面便利**：
   * 后端才是权限的判定方，这里只是不让「学生点进关系裁定 → 弹一句没有权限」这种
   * 演示事故把真正的功能演示冲掉。没填的项保持原样，B/D 各自的矩阵由他们自己补。
   *
   * ⚠️ 它和后端控制器上的 `@PreAuthorize` 是**两份声明**，改一处必须想着另一处。
   * 只改这里 = 藏了入口但接口还开着（安全上无害，因为拦在后端）；只改后端 = 点了会报错。
   */
  roles?: UserRole[]
}
export interface NavGroup {
  group: string
  items: NavItem[]
}

export const NAV: NavGroup[] = [
  {
    group: '总览',
    items: [{ id: 'index', label: '系统总览', icon: 'grid', path: '/' }]
  },
  {
    group: '指南与文书',
    items: [
      { id: 'admin-home', label: '管理工作台', icon: 'dash' },
      { id: 'guide-management', label: '指南管理', icon: 'book', path: '/guide' },
      { id: 'document-library', label: '文书总库', icon: 'doc' }
    ]
  },
  {
    group: '标注任务',
    items: [
      { id: 'task-list', label: '任务列表', icon: 'list' },
      { id: 'task-create', label: '创建任务', icon: 'plus' },
      { id: 'task-assign', label: '任务分配', icon: 'share' }
    ]
  },
  {
    group: '标注与裁定',
    items: [
      {
        id: 'annotation-workbench',
        label: '标注工作台',
        icon: 'edit',
        path: '/annotation/workbench',
        // R02–R04 写关系限 admin/annotator（见 SecurityConfig 的矩阵）
        roles: ['admin', 'annotator']
      },
      {
        id: 'graph-audit',
        label: '图示审查',
        icon: 'checkc',
        path: '/annotation/graph-audit',
        // GR02 审查是裁定员的活，所以比关系写多一个 arbiter
        roles: ['admin', 'annotator', 'arbiter']
      },
      { id: 'conflict-resolve', label: '冲突解决', icon: 'merge', path: '/adjudication/conflict' },
      {
        id: 'relation-adjudication',
        label: '关系裁定',
        icon: 'merge',
        path: '/adjudication/relation',
        roles: ['admin', 'arbiter']
      },
      { id: 'final-review', label: '最终版本审查', icon: 'checkc' }
    ]
  },
  {
    // 成员C：教学练习（分工.md §四-7）。
    // 五类练习（S 专项 / J-M 区分 / A-I 区分 / 嵌套 / 学生图与标准图比较）共用一个页面，
    // 因此这里只有一项；未来若拆成多页再按类型展开。
    group: '教学与练习',
    items: [
      {
        id: 'teaching-exercises',
        label: '关系练习',
        icon: 'list',
        path: '/teaching/exercises',
        roles: ['admin', 'student', 'teacher']
      }
    ]
  },
  {
    group: '专家复核',
    items: [
      { id: 'expert-review', label: '专家工作台', icon: 'scale', path: '/expert' },
      { id: 'expert-issues', label: '疑难问题说明文档', icon: 'bookOpen', path: '/expert/issues' }
    ]
  },
  {
    group: '自动化与导出',
    items: [
      { id: 'auto-annotate', label: '自动标注', icon: 'cpu' },
      { id: 'export', label: '导出中心', icon: 'download' },
      { id: 'export-records', label: '导出记录', icon: 'archive' }
    ]
  }
]
