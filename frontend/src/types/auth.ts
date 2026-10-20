// 登录与身份类型。归成员C（公共基础工作：登录 / Spring Security / RBAC，分工.md §六）。
//
// 字段与后端 com.argument.auth.dto 的三个 record 一一对应，改一处必须改另一处。
//
// UserRole 原本定义在 stores/user.ts，现挪到这里：它描述的是**后端的角色码**（app_role.code），
// 与「前端怎么存它」无关，放在类型层才能让 api/ 与 types/ 都不必反向依赖 store
// （否则 stores/user.ts 要 import types/auth.ts，types/auth.ts 又 import stores/user.ts，绕成环）。
// stores/user.ts 仍 re-export 这个名字，既有引用不会断。

/** 角色码。与后端种子 app_role.code 一一对应，缺一个就会有人登录后什么都点不开。 */
export type UserRole = 'admin' | 'creator' | 'annotator' | 'arbiter' | 'expert' | 'student' | 'teacher'

/** 角色码 → 中文名。用于顶栏与登录页的展示，不做权限判断（权限在后端）。 */
export const ROLE_LABEL: Record<UserRole, string> = {
  admin: '管理员',
  creator: '任务创建者',
  annotator: '标注员',
  arbiter: '裁定员',
  expert: '专家',
  student: '学生',
  teacher: '教师'
}

/** 角色码 → `.role-bar.<code>` 的颜色键（样式在 styles/app.css 的「角色色条」一节）。 */
export function roleLabel(role: UserRole | undefined): string {
  return role ? ROLE_LABEL[role] || role : '未分配角色'
}

/** 当前登录用户。**不含口令哈希**——后端刻意不下发。 */
export interface AuthUser {
  id: string
  username: string
  displayName: string
  /** 账号拥有的角色。顺序由后端按 role.code 升序给出。 */
  roles: UserRole[]
}

/** 登录请求体。两个字段都不可为空（后端 @NotBlank）。 */
export interface LoginRequest {
  username: string
  password: string
}

/**
 * 登录成功响应。
 *
 * <p>令牌是**不透明串**（32 字节随机数的 Base64URL），不是 JWT：前端不解析它，
 * 只当成一个字符串存起来、放进 `Authorization: Bearer` 头。所以这里也**没有** payload 类型。
 */
export interface LoginResponse {
  token: string
  /** 有效期（秒）。后端当前是 8 小时。 */
  expiresInSeconds: number
  user: AuthUser
}

/** 登出响应：`revoked` 为 false 表示这个令牌本来就已经失效（不是错误）。 */
export interface LogoutResponse {
  revoked: boolean
}
