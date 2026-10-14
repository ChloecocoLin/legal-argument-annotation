import axios, {
  type AxiosInstance,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig
} from 'axios'
import { ElMessage } from 'element-plus'
import type { PageResult } from '@/types/common'

// 扩展配置：_withMeta 让拦截器返回 { data, meta }，用于分页接口
declare module 'axios' {
  export interface AxiosRequestConfig {
    _withMeta?: boolean
  }
}

// 统一信封（依据 openapi-ready.yaml）：
// 成功 { success:true, data, meta, error:null, requestId, timestamp }
// 失败 { success:false, data:null, meta:null, error:{ code, message, details } }
interface ApiEnvelope<T> {
  success: boolean
  data: T
  meta: unknown
  error: { code: string; message: string; details: { field: string; rule: string }[] } | null
  requestId: string
  timestamp: string
}

const instance: AxiosInstance = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// ── 登录失效的出口（分工.md §六，成员C） ────────────────────────────────────
// 这一层**不知道路由、也不知道 pinia**：在这里 import router 会绕成
// request → router → stores/user → api/auth → request 的循环依赖。
// 所以改成由 main.ts 注册一个回调。默认值只在 main.ts 跑起来之前生效
// （那时也不可能有请求）。
let onUnauthenticated: (() => void) | null = null

/** 由 main.ts 调用，注册「需要重新登录」时要做的事（清本地登录态 + 跳登录页）。 */
export function setUnauthenticatedHandler(handler: () => void): void {
  onUnauthenticated = handler
}

/**
 * 通知登录失效：清掉本地令牌，再让注册方去跳登录页。
 *
 * 一个页面常常并发好几个请求，令牌过期时它们会**同时** 401，于是这里会被连着调用几次。
 * 重复调用是安全的、也是**故意**的：清令牌是幂等的，跳转由注册方自己判断「已经在登录页就什么都不做」。
 * 这里不额外记一个「已经通知过」的标志位——那种标志位要么漏掉「重新登录后再过期」的情形，
 * 要么就得再维护一套失效时间戳，为了省几次无副作用的函数调用不值得。
 *
 * 顺带把令牌清掉：已经在飞的请求赶不上了，但**之后再发起的**请求不会继续带着这个作废令牌
 * 去打后端（否则每转一页都白挨一次 401）。清掉它同时让 `stores/user.ts` 的 isLoggedIn 在刷新后也是 false。
 */
function notifyUnauthenticated(): void {
  localStorage.removeItem('token')
  onUnauthenticated?.()
}

// 请求拦截：附带 token
instance.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem('token')
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
    }

    // POST 默认补充幂等键
    const method = (config.method || '').toLowerCase()
    if (method === 'post' && !config.headers['Idempotency-Key']) {
      config.headers['Idempotency-Key'] = crypto.randomUUID()
    }
    return config
  },
  (error) => Promise.reject(error)
)

function isEnvelope(value: unknown): value is ApiEnvelope<unknown> {
  return value !== null && typeof value === 'object' && 'success' in value
}

// 响应拦截：解包统一信封
instance.interceptors.response.use(
  (response) => {
    const body = response.data

    if (!isEnvelope(body)) {
      // 非标准结构（如文件流）直接返回
      return body
    }

    if (body.success) {
      if (response.config._withMeta) {
        return { data: body.data, meta: body.meta }
      }
      return body.data
    }

    ElMessage.error(body.error?.message || '请求失败')
    return Promise.reject(new Error(body.error?.code || 'Business error'))
  },
  (error) => {
    const body = error.response?.data
    if (isEnvelope(body) && !body.success) {
      const code = body.error?.code
      if (code === 'UNAUTHENTICATED' || error.response.status === 401) {
        ElMessage.error('登录已过期，请重新登录')
        notifyUnauthenticated()
      } else if (code === 'FORBIDDEN' || error.response.status === 403) {
        // 403 不跳登录页：身份是有效的，只是这个角色不能做这件事。
        // 跳走反而让人以为「我又没登录了」，还得重新登一遍。
        ElMessage.error(body.error?.message || '没有操作权限')
      } else {
        ElMessage.error(body.error?.message || '请求失败')
      }
    } else if (error.message?.includes('timeout')) {
      ElMessage.error('请求超时，请检查网络')
    } else {
      ElMessage.error('网络异常，请检查连接')
    }
    return Promise.reject(error)
  }
)

// 便捷方法。拦截器已解包，因此用泛型直接返回 data 类型。
export function get<T>(url: string, params?: object, config?: AxiosRequestConfig): Promise<T> {
  return instance.get(url, { params, ...config }) as unknown as Promise<T>
}

// 分页查询：合并 data.items 与 meta
export async function getPage<T>(
  url: string,
  params?: object
): Promise<PageResult<T>> {
  const r = (await instance.get(url, {
    params,
    _withMeta: true
  })) as unknown as {
    data: { items: T[] }
    meta: { page: number; size: number; total: number } | null
  }
  const meta = r.meta ?? { page: 1, size: r.data.items.length, total: r.data.items.length }
  return { items: r.data.items, total: meta.total, page: meta.page, size: meta.size }
}

export function post<T>(url: string, data?: object, config?: AxiosRequestConfig): Promise<T> {
  return instance.post(url, data, config) as unknown as Promise<T>
}

export function put<T>(url: string, data?: object, config?: AxiosRequestConfig): Promise<T> {
  return instance.put(url, data, config) as unknown as Promise<T>
}

export function patch<T>(url: string, data?: object, config?: AxiosRequestConfig): Promise<T> {
  return instance.patch(url, data, config) as unknown as Promise<T>
}

export function del<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  return instance.delete(url, config) as unknown as Promise<T>
}

export default instance
