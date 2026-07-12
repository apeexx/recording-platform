import assert from 'node:assert/strict'
import { describe, it } from 'node:test'

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
      '/admin/platforms',
      '/admin/tasks',
      '/admin/permissions',
      '/admin/review/queue',
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
    assert.equal(adminSidebarConfig.findAdminSidebarGroupKeyByPath('/admin/review/queue'), 'review')
    assert.equal(adminSidebarConfig.findAdminSidebarGroupKeyByPath('/admin/dashboard'), null)
  })
})
