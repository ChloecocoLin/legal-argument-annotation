import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

export const DEV_IDENTITIES = [
  { token: 'dev-admin-wang', label: '王慧 · 管理员', role: 'ADMIN' },
  { token: 'dev-creator-li', label: '李承志 · 任务创建者', role: 'TASK_CREATOR' },
  { token: 'dev-annotator-chen', label: '陈晓 · 标注员', role: 'ANNOTATOR' },
  { token: 'dev-annotator-liu', label: '刘一鸣 · 标注员', role: 'ANNOTATOR' },
  { token: 'dev-arbiter-zhao', label: '赵明远 · 裁定者', role: 'ARBITER' },
  { token: 'dev-teacher-sun', label: '孙立 · 教师', role: 'TEACHER' },
  { token: 'dev-student-zhou', label: '周雨桐 · 学生', role: 'STUDENT' },
  { token: 'dev-expert-qian', label: '钱学礼 · 专家', role: 'EXPERT' }
] as const

const TOKEN_KEY = 'token'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) || 'dev-creator-li')

  function ensureToken() {
    if (!localStorage.getItem(TOKEN_KEY)) {
      localStorage.setItem(TOKEN_KEY, token.value)
    }
  }

  function setToken(next: string) {
    token.value = next
    localStorage.setItem(TOKEN_KEY, next)
  }

  const current = computed(() => DEV_IDENTITIES.find((item) => item.token === token.value) ?? DEV_IDENTITIES[1])

  ensureToken()

  return { token, current, setToken }
})
