import { beforeEach, describe, expect, it, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
vi.mock('../lib/httpClient.js', () => ({ httpRequest: vi.fn() }))
import { httpRequest } from '../lib/httpClient.js'
import { reviewApi } from '../lib/reviewApi.js'

describe('审核页面 API', () => {
  beforeEach(() => vi.clearAllMocks())
  it('领取使用 Idempotency-Key，驳回提交原因多选与说明', async () => {
    httpRequest.mockResolvedValue({})
	await reviewApi.claim('task-1', 'claim-1')
    await reviewApi.reject('item-1', 4, ['空音频'], '请重新录制', 'reject-1')
	expect(httpRequest).toHaveBeenNthCalledWith(1, '/api/reviews/tasks/task-1/claim', { method: 'POST', idempotencyKey: 'claim-1' })
    expect(httpRequest).toHaveBeenNthCalledWith(2, '/api/reviews/item-1/reject', {
      method: 'POST', json: { operationId: 'reject-1', expectedRevision: 4, reasons: ['空音频'], note: '请重新录制' }
    })
  })

  it('按后台角色区分审核池与工作台操作，管理员不会再看到审核员专属按钮', () => {
    const queue = readFileSync(join(process.cwd(), 'src/pages/admin/review/ReviewQueuePage.vue'), 'utf8')
    const workbench = readFileSync(join(process.cwd(), 'src/pages/admin/review/ReviewWorkbenchPage.vue'), 'utf8')

    expect(queue).toContain("const isAdmin=computed(()=>session.user.value?.role==='ADMIN')")
	expect(queue).toContain("isReviewer=computed(()=>session.user.value?.role==='REVIEWER')")
    expect(queue).toContain('v-if="isReviewer" class="button-primary" @click="claim"')
	expect(queue).toContain('v-if="isAdmin&&!r.reviewerId" class="button-link"')
		expect(workbench).toContain("const isReviewer = computed(() => session.user.value?.role === 'REVIEWER')")
		expect(workbench).toContain("const canDecide = computed(() => session.user.value?.role === 'ADMIN' || isOwnReviewerAssignment.value)")
    expect(workbench).toContain('v-if="isOwnReviewerAssignment" class="button-secondary" @click="release"')
    expect(workbench).toContain('v-if="canDecide" class="business-actions"')
  })
})
