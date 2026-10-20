// 登录 API（分工.md §六，归成员C）。
//
// 三个路径都不在 `接口契约/openapi-ready.yaml` 的就绪接口里：契约没有登录这一节，
// 鉴权是 C 按 §六 自己补的公共基础工作。若要并进公共契约，需 A/B/C/D 共同确认。
//
// 拦截器已解包信封，因此这里直接返回 data 类型，调用方**绝不写 `.data`**。
//
// 注意：登录请求**不会**自动带上旧令牌（请求拦截器会读 localStorage，但这三条里
// login 是无令牌时才调用的，logout 需要令牌、由拦截器自动带上，见下）。
import { get, post } from './request'
import type { AuthUser, LoginRequest, LoginResponse, LogoutResponse } from '@/types/auth'

/** 登录。失败（口令错 / 账号停用 / 无角色）统一是 401 或 403，message 已是中文。 */
export function login(payload: LoginRequest): Promise<LoginResponse> {
  return post<LoginResponse>('/auth/login', payload)
}

/**
 * 登出：让服务端作废这个令牌。
 *
 * 后端把 `/auth/logout` 放进了白名单（允许匿名），所以**令牌已过期时调用也不会 401**，
 * 返回 `{revoked:false}`。这正是我们要的：登出动作永远能把本地登录态清干净。
 */
export function logout(): Promise<LogoutResponse> {
  return post<LogoutResponse>('/auth/logout')
}

/**
 * 取当前登录用户。用途是**刷新页面后**用本地令牌换取用户信息
 * （displayName / roles 只在响应里，令牌本身不含这些内容）。
 * 令牌失效时返回 401，由拦截器负责清本地登录态。
 */
export function fetchMe(): Promise<AuthUser> {
  return get<AuthUser>('/auth/me')
}
