import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import fs from 'node:fs'
import path from 'node:path'

import * as adminSidebarConfig from '../config/adminSidebar.js'

const { adminSidebar } = adminSidebarConfig

describe('adminSidebar', () => {
  it('contains only production business paths exactly once', () => {
    const paths = adminSidebar.flatMap((item) => {
      if (item.children) {
        return item.children.map((child) => child.path)
      }

      return [item.path]
    })

    assert.deepEqual(paths, [
      '/admin/dashboard',
      '/admin/tasks',
      '/admin/pool',
      '/admin/permissions',
	  '/admin/review',
      '/admin/reports/tasks',
      '/admin/reports/collectors',
      '/admin/reports/reviewers',
      '/admin/voice-generation/workbench',
      '/admin/voice-generation/config',
      '/admin/voice-generation/records',
      '/admin/system/users',
      '/admin/system/logs',
      '/admin/account'
    ])
    assert.equal(new Set(paths).size, paths.length)
  })

  it('keeps voice generation in the administrator production scope', () => {
    const voiceGeneration = adminSidebar.find((item) => item.key === 'voice-generation')

    assert.deepEqual(voiceGeneration.roles, ['ADMIN'])
    assert.equal(voiceGeneration.children.length, 3)
  })

  it('finds the parent group key for child route paths', () => {
    assert.equal(typeof adminSidebarConfig.findAdminSidebarGroupKeyByPath, 'function')
    assert.equal(adminSidebarConfig.findAdminSidebarGroupKeyByPath('/admin/tasks'), 'tasks')
	assert.equal(adminSidebarConfig.findAdminSidebarGroupKeyByPath('/admin/review'), 'review')
    assert.equal(adminSidebarConfig.findAdminSidebarGroupKeyByPath('/admin/dashboard'), null)
  })

  it('maps every top-level entry to a semantic local Iconfont SVG', () => {
    const icons = Object.fromEntries(adminSidebar.map((item) => [item.key, item.icon]))

    assert.deepEqual(icons, {
      dashboard: 'dashboard',
      tasks: 'task',
      review: 'review',
      reports: 'report',
      'voice-generation': 'voice',
      system: 'system',
      account: 'account'
    })

    for (const icon of new Set(Object.values(icons))) {
      assert.equal(
        fs.existsSync(path.resolve(`public/assets/icons/admin-sidebar/${icon}.svg`)),
        true,
        `${icon}.svg 不存在`
      )
    }
  })

  it('uses one sidebar icon component instead of title initials', () => {
    const group = fs.readFileSync(path.resolve('src/components/admin/AdminSidebarGroup.vue'), 'utf8')
    const item = fs.readFileSync(path.resolve('src/components/admin/AdminSidebarItem.vue'), 'utf8')
    const icon = fs.readFileSync(path.resolve('src/components/admin/AdminSidebarIcon.vue'), 'utf8')

    assert.match(group, /import AdminSidebarIcon/)
    assert.match(group, /<AdminSidebarIcon :name="item\.icon"/)
    assert.match(item, /import AdminSidebarIcon/)
    assert.match(item, /<AdminSidebarIcon v-else :name="item\.icon"/)
    assert.match(icon, /\/assets\/icons\/admin-sidebar\/\$\{props\.name\}\.svg/)
    assert.doesNotMatch(group, /title\.slice/)
    assert.doesNotMatch(item, /title\.slice/)
  })

  it('keeps the Web brand SVG byte-identical to the mini-program asset', () => {
    const sidebar = fs.readFileSync(path.resolve('src/components/admin/AdminSidebar.vue'), 'utf8')
    const webBrand = fs.readFileSync(path.resolve('public/assets/branding/yanshu-avatar.svg'))
    const miniProgramBrand = fs.readFileSync(path.resolve('../miniprogram/assets/branding/yanshu-avatar.svg'))

    assert.match(sidebar, /const brandIconPath = '\/assets\/branding\/yanshu-avatar\.svg'/)
    assert.match(sidebar, /<img class="admin-sidebar__brand-mark" :src="brandIconPath"/)
    assert.equal(webBrand.equals(miniProgramBrand), true)
    assert.doesNotMatch(sidebar, /admin-sidebar__brand-mark">录/)
  })
})
