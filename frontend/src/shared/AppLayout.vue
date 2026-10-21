<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from './Icon.vue'
import Toasts from './Toasts.vue'
import { NAV } from './nav.config'
import { toast } from './toast'
import { useUserStore } from '@/stores/user'

const route = useRoute(), router = useRouter()
const user = useUserStore()
const userName = computed(() => user.name || '未登录')
const avatarText = computed(() => user.name ? user.name.slice(0, 2) : '—')
const roleName = computed(() => ({ admin: '管理员', creator: '任务创建者', annotator: '标注员', arbiter: '裁定者', expert: '专家', teacher: '教师', student: '学生' }[user.roles[0] || ''] || '未登录'))

const navigation = computed(() => {
  const allowed: Record<string, string[]> = {
 'teaching-home':['teacher','student','creator'],'teaching-assignment':['teacher'],'teaching-standard':['teacher'],'teaching-practice':['student'],'teaching-results':['teacher','student'],
    'admin-home':['admin'], 'research-results':['admin','creator','arbiter'],
    'audit-events':['admin','creator','arbiter'], 'guide-management':['admin','creator','annotator','arbiter','teacher','student','expert'],
    'document-library':['admin','creator','annotator','arbiter'], 'task-list':['admin','creator','annotator','arbiter'],
    'task-create':['creator'], 'task-assign':['creator'], 'segmentation':['annotator','arbiter'],
    'conflict-resolve':['arbiter'], 'final-review':['arbiter'], 'expert-review':['expert'],
    'expert-issues':['expert'], 'auto-annotate':['creator'],
    'export':['creator','arbiter'], 'export-records':['creator','arbiter']
  }
  return NAV.map(group => ({ ...group, items: group.items.filter(item => !allowed[item.id] || user.roles.some(role => allowed[item.id].includes(role))) })).filter(group => group.items.length)
})

const titleMap: Record<string, string> = {
 '/teaching':'教学工作台','/assignment-create':'创建教学作业','/teaching-standard':'标准标注样例','/student-results':'学生结果总览','/student-compare':'作答比对与反馈',
  '/': '系统总览',
  '/admin-home': '管理工作台',
  '/audit': '入库与审计记录',
  '/segmentation': '独立切割与统一关口',
  '/results': '已入库成果',
  '/auto-annotate': '自动标注',
  '/export': '导出中心',
  '/export-records': '导出记录',
  '/guide': '指南管理',
  '/adjudication/conflict': '冲突解决',
  '/adjudication/annotate': '裁定者自定义标注',
  '/expert': '专家工作台',
  '/expert/issues': '疑难问题说明文档'
}
const here = computed(() => {
  if (route.path.startsWith('/student-annotate')) return '学生标注练习'
  if (route.path.startsWith('/final-review')) return '最终版本审查'
  if (route.path.startsWith('/annotations/')) return '提交标注'
  if (route.path.startsWith('/versions/')) return '完整标注版本'
  if (route.path.startsWith('/documents/')) return '文书详情'
  if (route.path === '/documents') return '文书总库'
  if (route.path === '/tasks/create') return '创建任务'
  if (route.path.endsWith('/assign')) return '任务分配'
  if (route.path.startsWith('/tasks/')&&route.path.endsWith('/annotate')) return '本人标注工作台'
  if (route.path.endsWith('/segmentation')) return '独立切割与统一关口'
  if (route.path.startsWith('/tasks/')) return route.path.endsWith('/versions') ? '标注版本' : '任务详情'
  if (route.path === '/tasks') return '任务列表'
  if (route.path.startsWith('/guide/')) return '版本详情'
  return titleMap[route.path] || '系统总览'
})

async function logout() { await user.logout(); await router.replace('/login') }

function isActive(path?: string): boolean {
  if (!path) return false
  if (path === '/') return route.path === '/'
  if (path === '/segmentation') return route.path === path || route.path.endsWith('/segmentation')
  if (path === '/tasks' && route.path.endsWith('/segmentation')) return false
  return route.path === path || route.path.startsWith(path + '/')
}

function notReady(label: string) {
  toast(`「${label}」功能将在后续开放`, 'err')
}

function openMobileNav() {
  document.body.classList.toggle('nav-open')
}
</script>

<template>
  <div class="app">
    <aside class="sidebar" id="appSidebar">
      <div class="brand">
        <span class="brand-mark">律</span>
        <span class="brand-text">
          <b>法律论证标注系统</b>
          <span>LEGAL ARG · v1.2</span>
        </span>
      </div>
      <nav class="nav">
        <div v-for="g in navigation" :key="g.group" class="nav-group">
          <div class="nav-group-title">{{ g.group }}</div>
          <router-link
            v-for="item in g.items"
            :key="item.id"
            v-slot="{ navigate }"
            :to="item.path || '#'"
            custom
          >
            <a
              class="nav-item"
              :class="{ 'is-active': isActive(item.path) }"
              @click="item.path ? navigate() : notReady(item.label)"
            >
              <Icon :name="item.icon" cls="nav-ico" />
              <span>{{ item.label }}</span>
            </a>
          </router-link>
        </div>
      </nav>
      <div class="sidebar-foot">
        <span class="avatar">{{ avatarText }}</span>
        <span>
          <b style="color: #fff; font-size: 12.5px">{{ userName }}</b><br />
          <span>{{ roleName }}</span>
        </span>
      </div>
    </aside>

    <div class="app-main">
      <header class="topbar" id="appTopbar">
        <button class="btn btn-icon menu-btn" @click="openMobileNav"><Icon name="menu" /></button>
        <div class="crumb">
          <a>法律论证标注</a>
          <span class="sep">/</span>
          <span class="here">{{ here }}</span>
        </div>
        <span style="flex: 1"></span>
        <div class="topbar-search">
          <Icon name="search" />
          <input type="search" placeholder="检索文书 / 任务 / 案号" aria-label="全局检索" />
        </div>
        <div class="role-switch">
          <Icon name="user" />
          <span class="muted t-xs">角色</span>
          <select aria-label="切换角色" disabled>
            <option>{{ roleName }}</option>
          </select>
        </div>
        <button class="btn btn-icon btn-ghost" title="帮助" @click="notReady('帮助')"><Icon name="help" /></button>
        <button class="btn btn-icon btn-ghost" title="通知" @click="notReady('通知')"><Icon name="bell" /></button>
        <button class="btn btn-sm btn-ghost" @click="logout">退出登录</button>
        <span class="avatar" :title="userName">{{ avatarText }}</span>
      </header>

      <main class="content">
        <slot />
      </main>
    </div>

    <Toasts />
  </div>
</template>
