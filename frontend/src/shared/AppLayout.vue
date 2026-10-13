<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import Icon from './Icon.vue'
import Toasts from './Toasts.vue'
import { NAV } from './nav.config'
import { toast } from './toast'

const route = useRoute()

const titleMap: Record<string, string> = {
  '/': '系统总览',
  '/guide': '指南管理',
  '/adjudication/conflict': '冲突解决',
  '/adjudication/annotate': '标注工作台',
  '/expert': '专家工作台',
  '/expert/issues': '疑难问题说明文档'
}
const here = computed(() => {
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
        <span class="avatar">WH</span>
        <span>
          <b style="color: #fff; font-size: 12.5px">王慧</b><br />
          <span>管理员</span>
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
            <option>管理员</option>
          </select>
        </div>
        <button class="btn btn-icon btn-ghost" title="帮助" @click="notReady('帮助')"><Icon name="help" /></button>
        <button class="btn btn-icon btn-ghost" title="通知" @click="notReady('通知')"><Icon name="bell" /></button>
        <span class="avatar" title="平台管理员">WH</span>
      </header>

      <main class="content">
        <slot />
      </main>
    </div>

    <Toasts />
  </div>
</template>
