// 侧边导航。path 为空表示该模块由其他成员负责、当前未接入。
// 固定以 admin 角色视角展示（M1）。
export interface NavItem {
  id: string
  label: string
  icon: string
  path?: string
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
      { id: 'task-list', label: '任务进度', icon: 'list', path: '/tasks' },
      { id: 'task-create', label: '创建任务', icon: 'plus' },
      { id: 'task-assign', label: '任务分配', icon: 'share' }
    ]
  },
  {
    group: '标注与裁定',
    items: [
      { id: 'conflict-resolve', label: '冲突解决', icon: 'merge', path: '/adjudication/conflict' },
      { id: 'final-review', label: '最终版本审查', icon: 'checkc' }
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
