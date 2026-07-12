import { beforeEach, describe, expect, it, vi } from 'vitest'
vi.mock('../lib/httpClient.js', () => ({ httpRequest: vi.fn() }))
import { httpRequest } from '../lib/httpClient.js'
import { reportApi } from '../lib/reportApi.js'

describe('统计与操作记录 API', () => {
  beforeEach(() => vi.clearAllMocks())
  it('保留分页、用户和 UTC 时间过滤参数', async () => {
    httpRequest.mockResolvedValue({ items: [] })
    await reportApi.collectors({ userId: 'u1', from: '2026-07-01T00:00:00Z', to: '2026-07-12T00:00:00Z' })
    await reportApi.operations({ page: 1, size: 50 })
    expect(httpRequest.mock.calls[0][0]).toContain('/api/reports/collectors?')
    expect(httpRequest.mock.calls[0][0]).toContain('userId=u1')
    expect(httpRequest).toHaveBeenNthCalledWith(2, '/api/operations?page=1&size=50')
  })
})
