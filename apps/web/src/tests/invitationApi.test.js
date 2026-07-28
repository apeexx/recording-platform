import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../lib/httpClient.js', () => ({ httpRequest: vi.fn() }))

import { httpRequest } from '../lib/httpClient.js'
import { invitationApi } from '../lib/invitationApi.js'

describe('小程序邀请码 API', () => {
  beforeEach(() => vi.clearAllMocks())

  it('创建不发送幂等快照参数，停用发送独立 Idempotency-Key', async () => {
    httpRequest.mockResolvedValue({})

    await invitationApi.create({ name: '审核体验', note: '提审使用', maxUses: 5 })
    await invitationApi.disable('invite-1', 'disable-operation')

    expect(httpRequest).toHaveBeenNthCalledWith(1, '/api/admin/miniprogram-invitations', {
      method: 'POST',
      json: { name: '审核体验', note: '提审使用', maxUses: 5 }
    })
    expect(httpRequest).toHaveBeenNthCalledWith(
      2,
      '/api/admin/miniprogram-invitations/invite-1/disable',
      { method: 'POST', idempotencyKey: 'disable-operation' }
    )
  })

  it('分页读取邀请码脱敏列表', async () => {
    httpRequest.mockResolvedValue({ items: [], page: 1, size: 20, total: 21 })

    await invitationApi.list(1, 20)

    expect(httpRequest).toHaveBeenCalledWith('/api/admin/miniprogram-invitations?page=1&size=20')
  })
})
