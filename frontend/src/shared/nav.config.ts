// 侧边导航。path 为空表示该模块由其他成员负责、当前未接入。
// AppLayout 按当前账号角色过滤可见入口，服务端独立执行对象授权。
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
 {group:'实践教学',items:[
 {id:'teaching-home',label:'教学工作台',icon:'book',path:'/teaching'},
 {id:'teaching-assignment',label:'创建教学作业',icon:'plus',path:'/assignment-create'},
 {id:'teaching-standard',label:'标准标注样例',icon:'doc',path:'/teaching-standard'},
 {id:'teaching-practice',label:'学生标注练习',icon:'doc',path:'/student-annotate'},
 {id:'teaching-results',label:'学生结果总览',icon:'list',path:'/student-results'}]},
  {
    group: '总览',
    items: [{ id: 'index', label: '系统总览', icon: 'grid', path: '/' }]
  },
  {
    group: '指南与文书',
    items: [
      { id: 'admin-home', label: '管理工作台', icon: 'dash', path: '/admin-home' },
      { id: 'research-results', label: '已入库成果', icon: 'archive', path: '/results' },
      { id: 'audit-events', label: '入库与审计', icon: 'archive', path: '/audit' },
      { id: 'guide-management', label: '指南管理', icon: 'book', path: '/guide' },
      { id: 'document-library', label: '文书总库', icon: 'doc', path: '/documents' }
    ]
  },
  {
    group: '标注任务',
    items: [
      { id: 'task-list', label: '任务列表', icon: 'list', path: '/tasks' },
      { id: 'task-create', label: '创建任务', icon: 'plus', path: '/tasks/create' },
      { id: 'task-assign', label: '任务分配', icon: 'share' }
    ]
  },
  {
    group: '标注与裁定',
    items: [
      { id: 'segmentation', label: '独立切割与关口', icon: 'merge', path: '/segmentation' },
      { id: 'conflict-resolve', label: '冲突解决', icon: 'merge', path: '/adjudication/conflict' },
      { id: 'final-review', label: '最终版本审查', icon: 'checkc', path: '/final-review' }
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
      { id: 'auto-annotate', label: '自动标注', icon: 'cpu', path: '/auto-annotate' },
      { id: 'export', label: '导出中心', icon: 'download', path: '/export' },
      { id: 'export-records', label: '导出记录', icon: 'archive', path: '/export-records' }
    ]
  }
]
