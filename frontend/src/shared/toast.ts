import { reactive } from 'vue'

export interface ToastItem {
  id: number
  kind: 'ok' | 'err'
  message: string
}

let seq = 0
export const toasts = reactive<ToastItem[]>([])

export function toast(message: string, kind: 'ok' | 'err' = 'ok'): void {
  const id = ++seq
  toasts.push({ id, kind, message })
  window.setTimeout(() => {
    const idx = toasts.findIndex((t) => t.id === id)
    if (idx > -1) toasts.splice(idx, 1)
  }, 2600)
}
