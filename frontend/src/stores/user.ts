import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import * as authApi from '@/api/auth'
import type { AuthUser, UserRole } from '@/types/auth'

// 角色码定义已移到 types/auth.ts（避免类型层与 store 层的循环依赖）。
// 这里 re-export，原有的 `import { type UserRole } from '@/stores/user'` 写法继续有效。
export type { UserRole }

/**
 * 令牌在 localStorage 里的键。
 *
 * 用 localStorage 而不是 sessionStorage / 内存：刷新页面不该被踢回登录页，
 * 而令牌有 8 小时过期时间兜底（过期后 401 → 拦截器清掉并跳登录）。
 *
 * ⚠️ 这个键名是 `api/request.ts` 的请求拦截器**直接读**的（那是 B 的文件，
 * 本轮只做了「401 时清掉」这一处最小改动）。改键名必须同步改那里。
 */
const TOKEN_KEY = 'token'

/** 兼容入口：外部若只想塞一个令牌（例如脚本/调试），仍然能用。 */
export function readStoredToken(): string {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export const useUserStore = defineStore('user', () => {
  const id = ref<string>('')
  const username = ref<string>('')
  const name = ref<string>('')
  const roles = ref<UserRole[]>([])
  const token = ref<string>(readStoredToken())

  /** 是否已登录。判定依据是**本地有没有令牌**，不是「已知用户信息」——
   *  刷新页面时用户信息还没换回来，但令牌在，就不该跳登录页。 */
  const isLoggedIn = computed(() => !!token.value)

  /** 主角色：取第一个。仅用于界面展示，**不是权限依据**。 */
  const primaryRole = computed<UserRole | undefined>(() => roles.value[0])

  function setToken(value: string) {
    token.value = value
    if (value) localStorage.setItem(TOKEN_KEY, value)
    else localStorage.removeItem(TOKEN_KEY)
  }

  function applyUser(user: AuthUser) {
    id.value = user.id
    username.value = user.username
    name.value = user.displayName
    roles.value = user.roles
  }

  /** 清空本地登录态。**只动本地**，不调后端——令牌失效时也要能执行。 */
  function reset() {
    id.value = ''
    username.value = ''
    name.value = ''
    roles.value = []
    setToken('')
  }

  /** 登录：成功后同时写入令牌与用户信息。失败原样抛出，由调用方决定怎么提示。 */
  async function login(loginUsername: string, password: string): Promise<void> {
    const res = await authApi.login({ username: loginUsername, password })
    setToken(res.token)
    applyUser(res.user)
  }

  /**
   * 用本地令牌换回用户信息。**已有用户信息就不重复请求**。
   *
   * 失败时清空本地登录态并返回 false（而不是抛出）：令牌过期是正常的 8 小时后现象，
   * 调用方（路由守卫）只需要知道「现在没登录」。
   */
  async function loadMe(): Promise<boolean> {
    if (!token.value) return false
    if (id.value) return true
    try {
      applyUser(await authApi.fetchMe())
      return true
    } catch {
      reset()
      return false
    }
  }

  /**
   * 登出。先尽力通知后端作废令牌，**无论成功与否**都清本地。
   * 「后端暂时不通」不该变成「登不出去」——本地令牌清掉，这个设备就已经用不了了。
   */
  async function logout(): Promise<void> {
    try {
      if (token.value) await authApi.logout()
    } catch {
      // 故意吞掉：见上
    } finally {
      reset()
    }
  }

  return {
    id,
    username,
    name,
    roles,
    token,
    isLoggedIn,
    primaryRole,
    setToken,
    applyUser,
    login,
    loadMe,
    logout,
    reset
  }
})
