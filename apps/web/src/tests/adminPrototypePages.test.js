import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { describe, it } from 'node:test'

import { adminPrototypePages } from '../data/adminStaticData.js'

const pageFiles = [
  'src/pages/admin/basic/LanguageTypesPage.vue',
  'src/pages/admin/basic/SplitRulesPage.vue',
  'src/pages/admin/basic/AnnouncementsPage.vue',
  'src/pages/admin/text/TextImportPage.vue',
  'src/pages/admin/text/TextListPage.vue',
  'src/pages/admin/text/TextBatchesPage.vue',
  'src/pages/admin/tasks/TaskBatchesPage.vue',
  'src/pages/admin/tasks/TaskPublishPage.vue',
  'src/pages/admin/tasks/TaskClaimsPage.vue',
  'src/pages/admin/tasks/TaskRecyclePage.vue',
  'src/pages/admin/review/ReviewOverviewPage.vue',
  'src/pages/admin/review/FirstReviewPage.vue',
  'src/pages/admin/review/SecondReviewPage.vue',
  'src/pages/admin/review/RejectedRecordsPage.vue',
  'src/pages/admin/results/ApprovedResultsPage.vue',
  'src/pages/admin/results/AudioFilesPage.vue',
  'src/pages/admin/results/ResultExportPage.vue',
  'src/pages/admin/reports/ProjectStatisticsPage.vue',
  'src/pages/admin/reports/RecorderStatisticsPage.vue',
  'src/pages/admin/reports/ReviewerStatisticsPage.vue',
  'src/pages/admin/system/UsersPage.vue',
  'src/pages/admin/system/RolesPage.vue',
  'src/pages/admin/system/OperationLogsPage.vue',
  'src/pages/admin/system/SystemSettingsPage.vue'
]

function readPage(relativePath) {
  return readFileSync(join(process.cwd(), relativePath), 'utf8')
}

describe('admin prototype pages', () => {
  it('replaces non-voice placeholder pages with the static prototype renderer', () => {
    for (const pageFile of pageFiles) {
      const pageSource = readPage(pageFile)

      assert.match(pageSource, /AdminPrototypePage/, `${pageFile} should render a prototype page`)
      assert.doesNotMatch(
        pageSource,
        /AdminPlaceholderPage/,
        `${pageFile} should not use the placeholder renderer`
      )
    }
  })

  it('keeps voice-generation as the real integration workbench', () => {
    const voiceWorkbench = readPage(
      'src/pages/admin/voice-generation/VoiceGenerationWorkbenchPage.vue'
    )

    assert.match(voiceWorkbench, /voiceGenerationApi/)
    assert.match(voiceWorkbench, /submitGeneration/)
    assert.match(voiceWorkbench, /fetchVoices/)
    assert.doesNotMatch(voiceWorkbench, /AdminPlaceholderPage/)
    assert.doesNotMatch(voiceWorkbench, /AdminPrototypePage/)
  })

  it('defines static data for every non-dashboard prototype page', () => {
    const expectedKeys = [
      'language-types',
      'split-rules',
      'announcements',
      'text-import',
      'text-list',
      'text-batches',
      'task-batches',
      'task-publish',
      'task-claims',
      'task-recycle',
      'review-overview',
      'first-review',
      'second-review',
      'rejected-records',
      'approved-results',
      'audio-files',
      'result-export',
      'project-statistics',
      'recorder-statistics',
      'reviewer-statistics',
      'users',
      'roles',
      'operation-logs',
      'system-settings'
    ]

    assert.deepEqual(Object.keys(adminPrototypePages).sort(), expectedKeys.sort())

    for (const key of expectedKeys) {
      const page = adminPrototypePages[key]

      assert.equal(typeof page.title, 'string')
      assert.equal(page.metrics.length, 4)
      assert.ok(page.rows.length >= 3)
      assert.ok(page.tabs.length >= 3)
      assert.ok(page.timeline.length >= 3)
      assert.ok(page.checklist.length >= 3)
      assert.doesNotMatch(JSON.stringify(page), /token|cookie|authorization|签名 URL|真实音频 URL/i)
    }
  })

  it('implements local interaction surfaces in the shared prototype renderer', () => {
    const source = readPage('src/components/admin/AdminPrototypePage.vue')

    assert.match(source, /selectFilter/)
    assert.match(source, /selectTab/)
    assert.match(source, /selectRow/)
    assert.match(source, /toggleRowStatus/)
    assert.match(source, /admin-detail-panel/)
    assert.match(source, /admin-toast/)
  })
})
