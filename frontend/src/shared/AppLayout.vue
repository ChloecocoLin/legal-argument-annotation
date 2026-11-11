<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from './Icon.vue'
import Toasts from './Toasts.vue'
import { NAV } from './nav.config'
import { toast } from './toast'
import { helpFor } from './pageHelp'
import { canEnter } from './roleAccess'
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
  closeMenus()
  await user.logout()
  toast('已退出登录')
  // 不带 redirect：退出是主动行为，用户要的是回到登录页，不是回来接着看刚才那一页。
  await router.replace({ name: 'login' })
}

/**
 * 换账号。角色是账号的属性，前端没有「切换角色」这回事：
 * 唯一诚实的做法是退出当前会话，回到登录页用另一个账号登录。
 */
async function switchAccount() {
  closeMenus()
  await user.logout()
  toast('已退出，请使用另一个账号登录')
  await router.replace({ name: 'login' })
}

/**
 * 导航按角色过滤。角色只读**路由的 meta.roles**（router/index.ts），不在这里另写一份。
 * 未声明 roles 的路由对所有登录用户可见；一个分组里的项全被滤掉时，整个分组也不显示。
 */
const navGroups = computed(() =>
  NAV.map((g) => ({
    ...g,
    items: g.items.filter(
      (it) => !it.path || canEnter(user.roles, router.resolve(it.path).meta.roles)
    )
  })).filter((g) => g.items.length > 0)
)

const here = computed(() => (route.meta.title as string) || '系统总览')

const helpLines = computed(() => helpFor(route.name))

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

// ── 顶栏浮层（帮助 / 通知 / 账号）──────────────────────────────────
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

        <!-- 帮助：显示当前页面的 3–5 行说明，文案在 shared/pageHelp.ts -->
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

        <!-- 通知：没有数据时如实显示「暂无通知」，不弹「模块未接入」的错误 -->
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

        <!-- 账号菜单：角色由账号决定，不做「切换角色」，只提供退出后换账号 -->
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
