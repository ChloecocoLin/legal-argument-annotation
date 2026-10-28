// 从 axios 错误里取出信封里的业务错误码 / 文案。
// 响应拦截器已经弹过通用提示，这里用于按错误码做更具体的界面反馈（如高亮出错的参与项）。
export interface ApiErrorBox {
  message?: string
  response?: {
    status?: number
    data?: { error?: { code?: string; message?: string } | null }
  }
}

export function errorCodeOf(error: unknown): string {
  const boxed = error as ApiErrorBox | null
  return boxed?.response?.data?.error?.code || boxed?.message || 'UNKNOWN'
}

export function errorMessageOf(error: unknown): string {
  const boxed = error as ApiErrorBox | null
  return boxed?.response?.data?.error?.message || boxed?.message || '请求失败'
}
