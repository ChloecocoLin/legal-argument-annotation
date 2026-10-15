<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import Icon from './Icon.vue'
import Toasts from './Toasts.vue'
import { NAV } from './nav.config'
import { toast } from './toast'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const user = useUserStore()
const userName = computed(() => user.name || '未登录')
const avatarText = computed(() => user.name ? user.name.slice(0, 2) : '—')
const roleName = computed(() => ({ admin: '管理员', creator: '任务创建者', annotator: '标注员', arbiter: '裁定者', expert: '专家', teacher: '教师', student: '学生' }[user.roles[0] || ''] || '未登录'))

const titleMap: Record<string, string> = {
  '/': '系统总览',
  '/admin-home': '管理工作台',
  '/audit': '入库与审计记录',
  '/export': '导出中心',
  '/export-records': '导出记录',
  '/guide': '指南管理',
  '/adjudication/conflict': '冲突解决',
  '/adjudication/annotate': '裁定者自定义标注',
  '/expert': '专家工作台',
  '/expert/issues': '疑难问题说明文档'
}
const here = computed(() => {
  if (route.path.startsWith('/final-review')) return '最终版本审查'
  if (route.path.startsWith('/annotations/')) return '提交标注'
  if (route.path.startsWith('/versions/')) return '完整标注版本'
  if (route.path.startsWith('/tasks/')) return route.path.endsWith('/versions') ? '标注版本' : '任务详情'
  if (route.path === '/tasks') return '任务进度'
  if (route.path.startsWith('/guide/')) return '版本详情'
  return titleMap[route.path] || '系统总览'
})

function isActive(path?: string): boolean {
  if (!path) return false
  if (path === '/') return route.path === '/'
  return route.path === path || route.path.startsWith(path + '/')
}

function notReady(label: string) {
  toast(`「${label}」模块由其他成员负责，当前未接入`, 'err')
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
        <div v-for="g in NAV" :key="g.group" class="nav-group">
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
        <span class="avatar" :title="userName">{{ avatarText }}</span>
      </header>

      <main class="content">
        <slot />
      </main>
    </div>

    <Toasts />
  </div>
</template>
