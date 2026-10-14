<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from './Icon.vue'
import Toasts from './Toasts.vue'
import { NAV } from './nav.config'
import { toast } from './toast'
import { useUserStore } from '@/stores/user'
import { ROLE_LABEL } from '@/types/auth'

const route = useRoute()
const router = useRouter()
const user = useUserStore()

// 顶栏与侧栏底部的身份区。原来写死「王慧 / WH / 管理员」——那是原型的固定值，
// 现在真的有登录了，写死就会让人以为「我用别人的账号登进来还是王慧」。
const displayName = computed(() => user.name || user.username || '未登录')
const initials = computed(() => {
  const n = user.name || user.username
  if (!n) return '?'
  // 中文名取姓 + 名首字（王慧 → 王慧，欧阳锋 → 欧阳）；英文名/账号取首字母。
  if (/^[一-龥]+$/.test(n)) return n.length <= 2 ? n : n.slice(0, 2)
  return n.slice(0, 2).toUpperCase()
})
const roleText = computed(() =>
  user.roles.length ? user.roles.map((r) => ROLE_LABEL[r] || r).join(' / ') : '未分配角色'
)

async function signOut() {
  await user.logout()
  toast('已退出登录')
  // 不带 redirect：退出是主动行为，用户要的是回到登录页，不是回来接着看刚才那一页。
  await router.replace({ name: 'login' })
}

/**
 * 导航按角色过滤。**只过滤填了 roles 的项**，其余原样显示——
 * B/D 的模块还没声明各自的角色矩阵，凭空猜一个只会把他们的入口藏掉。
 * 一个分组里的项全被滤掉时，整个分组也不显示（免得留一个孤零零的标题）。
 */
const navGroups = computed(() =>
  NAV.map((g) => ({
    ...g,
    items: g.items.filter((it) => !it.roles || it.roles.some((r) => user.roles.includes(r)))
  })).filter((g) => g.items.length > 0)
)

const titleMap: Record<string, string> = {
  '/': '系统总览',
  '/guide': '指南管理',
  // 成员C的标注工作台（关系标注与论证图），与 B 的 /adjudication/annotate 相互独立
  '/annotation/workbench': '标注工作台',
  // 成员C的图示审查页（分工.md §四.6）
  '/annotation/graph-audit': '图示审查',
  // 成员C的教学练习页（分工.md §四-7）
  '/teaching/exercises': '关系练习',
  '/adjudication/conflict': '冲突解决',
  // 成员C的关系裁定页（冲突解决第二阶段）
  '/adjudication/relation': '关系裁定',
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
        <div v-for="g in navGroups" :key="g.group" class="nav-group">
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
        <span class="avatar">{{ initials }}</span>
        <span>
          <b style="color: #fff; font-size: 12.5px">{{ displayName }}</b><br />
          <span>{{ roleText }}</span>
        </span>
        <button class="btn btn-icon btn-ghost sidebar-foot-out" title="退出登录" @click="signOut">
          <Icon name="logout" />
        </button>
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
        <!-- 角色下拉保留但**不可切换**：角色是账号的属性，由后端按令牌判定。
             做成可切换等于把 RBAC 变成摆设（前端选了也没用，后端不认）。 -->
        <div class="role-switch" :title="`当前角色：${roleText}（由账号决定，不可切换）`">
          <Icon name="user" />
          <span class="muted t-xs">角色</span>
          <select aria-label="当前角色" disabled>
            <option>{{ roleText }}</option>
          </select>
        </div>
        <button class="btn btn-icon btn-ghost" title="帮助" @click="notReady('帮助')"><Icon name="help" /></button>
        <button class="btn btn-icon btn-ghost" title="通知" @click="notReady('通知')"><Icon name="bell" /></button>
        <span class="avatar" :title="`${displayName} · ${roleText}`">{{ initials }}</span>
      </header>

      <main class="content">
        <slot />
      </main>
    </div>

    <Toasts />
  </div>
</template>
