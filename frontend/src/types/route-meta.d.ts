// 路由元信息的类型扩展。归成员C（公共基础工作：RBAC）。
//
// 原来 meta 在路由表里是自由对象，`to.meta.roles` 只能靠 as any 才写得下去；
// 这里把它钉成类型，写错角色码（例如 'judge'）在 vue-tsc 阶段就会报出来。
import 'vue-router'
import type { UserRole } from './auth'

declare module 'vue-router' {
  interface RouteMeta {
    /** 浏览器标题（afterEach 用）。 */
    title?: string
    /** 未登录可进。目前只有登录页。 */
    public?: boolean
    /** 不套工作台外壳（登录页）。 */
    blank?: boolean
    /**
     * 允许进入该页的角色。**不填 = 所有已登录用户都能进。**
     *
     * 与后端控制器上的 `@PreAuthorize` 是两份声明：这里只负责「不让人点进一个注定 403 的页面」，
     * 真正的权限判定始终在后端。改一处时想着另一处。
     */
    roles?: UserRole[]
  }
}
