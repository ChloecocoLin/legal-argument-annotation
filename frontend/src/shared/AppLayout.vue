<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from './Icon.vue'
import Toasts from './Toasts.vue'
import { NAV } from './nav.config'
import { toast } from './toast'
import { helpFor } from './pageHelp'
import { useUserStore } from '@/stores/user'
import { ROLE_LABEL } from '@/types/auth'

const route = useRoute(), router = useRouter()
const user = useUserStore()
const userName = computed(() => user.name || user.username || '未登录')
const avatarText = computed(() => {
  const n = user.name || user.username
  if (!n) return '—'
  return n.slice(0, 2)
})
const displayName = computed(() => user.name || user.username || '未登录')
const initials = avatarText
const roleText = computed(() =>
  user.roles.length ? user.roles.map((r) => ROLE_LABEL[r] || r).join(' / ') : '未分配角色'
)

const navigation = computed(() => {
  const allowed: Record<string, string[]> = {
 'teaching-home':['teacher','student','creator'],'teaching-assignment':['teacher'],'teaching-standard':['teacher'],'teaching-practice':['student'],'teaching-results':['teacher','student'],
    'admin-home':['admin'], 'research-results':['admin','creator','arbiter'],
    'audit-events':['admin','creator','arbiter'], 'guide-management':['admin','creator','annotator','arbiter','teacher','student','expert'],
    'document-library':['admin','creator','annotator','arbiter'], 'task-list':['admin','creator','annotator','arbiter'],
    'task-create':['admin','creator'], 'segmentation':['annotator','arbiter'],
    'conflict-resolve':['arbiter'], 'final-review':['arbiter'], 'expert-review':['expert'],
    'expert-issues':['expert'], 'auto-annotate':['creator'],
    'export':['creator','arbiter'], 'export-records':['creator','arbiter']
  }
  return NAV.map(group => ({ ...group, items: group.items.filter(item => !allowed[item.id] || user.roles.some(role => allowed[item.id].includes(role))) })).filter(group => group.items.length)
})

const here = computed(() => (route.meta.title as string) || '系统总览')
const helpLines = computed(() => helpFor(route.name))

async function signOut() {
  closeMenus()
  await user.logout()
  toast('已退出登录')
  await router.replace({ path: '/login' })
}

async function switchAccount() {
  closeMenus()
  await user.logout()
  toast('已退出，请使用另一个账号登录')
  await router.replace({ path: '/login' })
}

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

type TopMenu = 'help' | 'bell' | 'account'
const openMenu = ref<TopMenu | null>(null)

function toggleMenu(name: TopMenu) {
  openMenu.value = openMenu.value === name ? null : name
}

function closeMenus() {
  openMenu.value = null
}

function onDocumentClick() {
  closeMenus()
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') closeMenus()
}

onMounted(() => {
  document.addEventListener('click', onDocumentClick)
  document.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', onDocumentClick)
  document.removeEventListener('keydown', onKeydown)
})
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
          <span>{{ roleText }}</span>
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

        <div class="topbar-menu">
          <button
            class="btn btn-icon btn-ghost"
            title="帮助"
            :aria-expanded="openMenu === 'help'"
            @click.stop="toggleMenu('help')"
          >
            <Icon name="help" />
          </button>
          <div v-if="openMenu === 'help'" class="topbar-pop help-pop" @click.stop>
            <div class="pop-title">{{ here }}</div>
            <ul class="pop-help">
              <li v-for="line in helpLines" :key="line">{{ line }}</li>
            </ul>
          </div>
        </div>

        <div class="topbar-menu">
          <button
            class="btn btn-icon btn-ghost"
            title="通知"
            :aria-expanded="openMenu === 'bell'"
            @click.stop="toggleMenu('bell')"
          >
            <Icon name="bell" />
          </button>
          <div v-if="openMenu === 'bell'" class="topbar-pop bell-pop" @click.stop>
            <div class="pop-title">通知</div>
            <div class="pop-empty">暂无通知</div>
            <p class="t-xs muted" style="margin-top: 8px">
              任务提醒、裁定退回与专家复核结果会出现在这里。
            </p>
          </div>
        </div>

        <div class="topbar-menu">
          <button
            class="account-btn"
            :title="`${displayName} · ${roleText}`"
            :aria-expanded="openMenu === 'account'"
            @click.stop="toggleMenu('account')"
          >
            <span class="avatar">{{ initials }}</span>
            <span class="account-name">
              <b>{{ displayName }}</b>
              <small>{{ roleText }}</small>
            </span>
            <Icon name="chev" cls="account-chev" />
          </button>
          <div v-if="openMenu === 'account'" class="topbar-pop account-pop" @click.stop>
            <div class="pop-account-head">
              <span class="avatar">{{ initials }}</span>
              <span>
                <b>{{ displayName }}</b>
                <small>{{ user.username || '—' }}</small>
              </span>
            </div>
            <p class="t-xs muted" style="margin: 6px 0 10px">
              当前角色：{{ roleText }}。角色由账号决定，不能在页面上切换。
            </p>
            <button class="pop-item" @click="switchAccount">
              <Icon name="logout" />
              <span>
                <b>退出后换账号</b>
                <small>回到登录页，用另一个账号登录</small>
              </span>
            </button>
            <button class="pop-item" @click="signOut">
              <Icon name="logout" />
              <span>
                <b>退出登录</b>
              </span>
            </button>
          </div>
        </div>
      </header>

      <main class="content">
        <slot />
      </main>
    </div>

    <Toasts />
  </div>
</template>
