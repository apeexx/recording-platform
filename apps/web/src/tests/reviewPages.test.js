import { beforeEach, describe, expect, it, vi } from 'vitest'
vi.mock('../lib/httpClient.js', () => ({ httpRequest: vi.fn() }))
import { httpRequest } from '../lib/httpClient.js'
import { reviewApi } from '../lib/reviewApi.js'

describe('审核页面 API', () => {
  beforeEach(() => vi.clearAllMocks())
  it('领取使用 Idempotency-Key，驳回提交原因多选与说明', async () => {
    httpRequest.mockResolvedValue({})
    await reviewApi.claim('claim-1')
    await reviewApi.reject('item-1', 4, ['空音频'], '请重新录制', 'reject-1')
    expect(httpRequest).toHaveBeenNthCalledWith(1, '/api/reviews/claim', { method: 'POST', idempotencyKey: 'claim-1' })
    expect(httpRequest).toHaveBeenNthCalledWith(2, '/api/reviews/item-1/reject', {
      method: 'POST', json: { operationId: 'reject-1', expectedRevision: 4, reasons: ['空音频'], note: '请重新录制' }
    })
  })
})
