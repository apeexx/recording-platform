import { describe, expect, it } from 'vitest'
import { sidebarForRole } from '../config/adminSidebar.js'
import { createAdminRouteGuard } from '../router/guards.js'
import { adminRoutes } from '../router/adminRoutes.js'

function paths(items) {
  return items.flatMap((item) => item.children?.map((child) => child.path) || [item.path])
}

describe('后台角色导航', () => {
  it('管理员显示核心业务和语音生成，审核员只显示审核、统计和账号', () => {
    const adminPaths = paths(sidebarForRole('ADMIN'))
    const reviewerPaths = paths(sidebarForRole('REVIEWER'))

    expect(adminPaths).toContain('/admin/platforms')
    expect(adminPaths).toContain('/admin/tasks')
    expect(adminPaths).toContain('/admin/pool')
    expect(adminPaths).toContain('/admin/voice-generation/workbench')
    expect(reviewerPaths).toEqual([
      '/admin/review/queue', '/admin/reports/reviewers', '/admin/account'
    ])
    expect(adminPaths).not.toContain('/admin/basic/language-types')
    expect(adminPaths).not.toContain('/admin/system/roles')
  })

  it('生产路由只保留真实业务路径，不暴露旧原型入口', () => {
    const routePaths = adminRoutes.children.map(route => `/admin/${route.path}`)
    expect(routePaths).toContain('/admin/tasks/:id')
    expect(routePaths).toContain('/admin/permissions')
    expect(routePaths).toContain('/admin/review/:itemId')
    expect(routePaths).not.toContain('/admin/text/import')
    expect(routePaths).not.toContain('/admin/review/overview')
    expect(routePaths).not.toContain('/admin/system/roles')
  })

  it('采集权限侧边栏入口打开任务选择页而不是跳回任务列表', () => {
    const route = adminRoutes.children.find(item => item.path === 'permissions')

    expect(route.redirect).toBeUndefined()
    expect(route.component).toEqual(expect.any(Function))
    expect(route.meta.title).toBe('采集权限')
  })

  it('未登录去登录页，首次改密被限制，角色越权去各自首页', async () => {
    const session = { initialize: async () => {}, user: { value: null } }
    const guard = createAdminRouteGuard(session)
    expect(await guard({ path: '/admin/tasks', meta: { requiresAuth: true } })).toEqual({ name: 'login' })

    session.user.value = { role: 'ADMIN', firstPasswordChangeRequired: true }
    expect(await guard({ path: '/admin/tasks', meta: { requiresAuth: true } })).toEqual({ name: 'first-password' })
    expect(await guard({ path: '/first-password', name: 'first-password', meta: { requiresAuth: true } })).toBe(true)

    session.user.value = { role: 'REVIEWER', firstPasswordChangeRequired: false }
    expect(await guard({ path: '/admin/tasks', meta: { requiresAuth: true, roles: ['ADMIN'] } }))
      .toEqual('/admin/review/queue')
  })

  it('身份服务暂不可用时公开登录页仍可显示，受保护页回登录页', async () => {
    const session = { initialize: async () => { throw { status: 503 } }, user: { value: null } }
    const guard = createAdminRouteGuard(session)
    expect(await guard({ name: 'login', path: '/login', meta: { public: true } })).toBe(true)
    expect(await guard({ path: '/admin/tasks', meta: { requiresAuth: true } })).toEqual({
      name: 'login', query: { reason: 'service-unavailable' }
    })
  })
})
