import assert from 'node:assert/strict'
import { describe, it } from 'node:test'

import * as adminSidebarConfig from '../config/adminSidebar.js'

const { adminSidebar } = adminSidebarConfig

describe('adminSidebar', () => {
  it('contains every planned admin module path exactly once', () => {
    const paths = adminSidebar.flatMap((item) => {
      if (item.children) {
        return item.children.map((child) => child.path)
      }

      return [item.path]
    })

    assert.deepEqual(paths, [
      '/admin/dashboard',
      '/admin/basic/language-types',
      '/admin/basic/split-rules',
      '/admin/basic/announcements',
      '/admin/text/import',
      '/admin/text/list',
      '/admin/text/batches',
      '/admin/tasks/batches',
      '/admin/tasks/publish',
      '/admin/tasks/claims',
      '/admin/tasks/recycle',
      '/admin/review/overview',
      '/admin/review/first',
      '/admin/review/second',
      '/admin/review/rejected',
      '/admin/results/approved',
      '/admin/results/files',
      '/admin/results/export',
      '/admin/reports/projects',
      '/admin/reports/recorders',
      '/admin/reports/reviewers',
      '/admin/voice-generation/workbench',
      '/admin/voice-generation/config',
      '/admin/voice-generation/records',
      '/admin/system/users',
      '/admin/system/roles',
      '/admin/system/logs',
      '/admin/system/settings'
    ])
    assert.equal(new Set(paths).size, paths.length)
  })

  it('marks voice generation as collaborator placeholder scope', () => {
    const voiceGeneration = adminSidebar.find((item) => item.key === 'voice-generation')

    assert.equal(voiceGeneration.owner, 'collaborator')
    assert.equal(voiceGeneration.status, 'placeholder')
    assert.equal(voiceGeneration.children.length, 3)
  })

  it('finds the parent group key for child route paths', () => {
    assert.equal(typeof adminSidebarConfig.findAdminSidebarGroupKeyByPath, 'function')
    assert.equal(adminSidebarConfig.findAdminSidebarGroupKeyByPath('/admin/tasks/batches'), 'tasks')
    assert.equal(adminSidebarConfig.findAdminSidebarGroupKeyByPath('/admin/review/first'), 'review')
    assert.equal(adminSidebarConfig.findAdminSidebarGroupKeyByPath('/admin/dashboard'), null)
  })
})
