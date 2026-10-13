import { defineStore } from 'pinia'
import { ref } from 'vue'

export type UserRole = 'admin' | 'annotator' | 'arbiter' | 'expert' | 'student' | 'teacher'

export const useUserStore = defineStore('user', () => {
  const id = ref<string>('')
  const name = ref<string>('')
  const roles = ref<UserRole[]>([])
  const token = ref<string>('')

  function setToken(value: string) {
    token.value = value
  }

  function reset() {
    id.value = ''
    name.value = ''
    roles.value = []
    token.value = ''
  }

  return { id, name, roles, token, setToken, reset }
})
