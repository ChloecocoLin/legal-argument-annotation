// 角色与页面归属的唯一一份映射。归成员C（分工.md §六）。
//
// 三处都从这里取，避免各写一份后互相漂移：
//   1. 路由守卫（router/index.ts）——判断能不能进；
//   2. 侧栏（AppLayout.vue）——判断显不显示；
//   3. 403 页内提示——告诉用户「需要哪些角色」。
//
// 注意：这里**不是安全边界**。后端控制器的 @PreAuthorize 才是。
// 前端这一层的目标是「别让人点进一个注定失败的页面」。
import type { UserRole } from '@/types/auth'

/**
 * 每种角色的默认落点。被拒后回到这里，而不是统一回首页：
 * 学生被挡在关系裁定外，回到他的练习页比回到一个和自己无关的总览页更有用。
 */
export const ROLE_HOME: Record<UserRole, string> = {
  admin: '/',
  annotator: '/annotation/workbench',
  arbiter: '/adjudication/relation',
  expert: '/expert',
  teacher: '/teaching/exercises',
  student: '/teaching/exercises'
}

/** 多角色账号取**第一个有落点**的角色；后端按 role.code 升序下发，顺序是稳定的。 */
export function homeForRoles(roles: readonly UserRole[] | undefined): string {
  for (const role of roles ?? []) {
    const home = ROLE_HOME[role]
    if (home) return home
  }
  return '/'
}

/** 角色集合与页面允许角色是否有交集；页面没声明 roles 时视为对所有登录用户开放。 */
export function canEnter(
  roles: readonly UserRole[] | undefined,
  allowed: readonly UserRole[] | undefined
): boolean {
  if (!allowed || allowed.length === 0) return true
  return (roles ?? []).some((role) => allowed.includes(role))
}

/** 把角色码翻成「管理员 / 裁定员」，用于 403 提示。 */
export function roleListText(roles: readonly UserRole[] | undefined): string {
  const labels: Record<UserRole, string> = {
    admin: '管理员',
    annotator: '标注员',
    arbiter: '裁定员',
    expert: '专家',
    teacher: '教师',
    student: '学生'
  }
  return (roles ?? []).map((role) => labels[role] ?? role).join(' / ')
}
