// 从 axios 错误里取出信封里的业务错误码 / 文案。
// 响应拦截器已经弹过通用提示，这里用于按错误码做更具体的界面反馈（如高亮出错的参与项）。
export interface ApiErrorBox {
  message?: string
  /** request.ts 拦截器附加的规范化字段（HTTP 错误与业务失败都有） */
  apiCode?: string
  apiStatus?: number
  apiMessage?: string
  apiDetails?: { field: string; rule: string }[]
  response?: {
    status?: number
    data?: { error?: { code?: string; message?: string } | null }
  }
}

export function errorCodeOf(error: unknown): string {
  const boxed = error as ApiErrorBox | null
  return (
    boxed?.apiCode || boxed?.response?.data?.error?.code || boxed?.message || 'UNKNOWN'
  )
}

/**
 * 页面里显示的错误文案。
 * 优先用拦截器规范化后的 apiMessage：业务失败（HTTP 200 + success:false）原本只留了
 * 一个错误码，页面再取 message 只能拿到 UNAUTHENTICATED 这种英文码。
 */
export function errorMessageOf(error: unknown): string {
  const boxed = error as ApiErrorBox | null
  return (
    boxed?.apiMessage ||
    boxed?.response?.data?.error?.message ||
    boxed?.message ||
    '请求失败'
  )
}

/** 403：身份有效，只是没有这个功能/这份数据的权限；页面可据此显示「无权限」而不是「加载失败」。 */
export function isForbidden(error: unknown): boolean {
  const boxed = error as ApiErrorBox | null
  return (
    boxed?.apiStatus === 403 ||
    boxed?.apiCode === 'FORBIDDEN' ||
    boxed?.response?.status === 403 ||
    boxed?.response?.data?.error?.code === 'FORBIDDEN'
  )
}
