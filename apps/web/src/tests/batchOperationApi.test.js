import { beforeEach, describe, expect, it, vi } from 'vitest'
vi.mock('../lib/httpClient.js', () => ({ httpRequest: vi.fn() }))
import { httpRequest } from '../lib/httpClient.js'
import { batchOperationApi } from '../lib/batchOperationApi.js'

describe('跨页批处理 API', () => {
  beforeEach(() => vi.clearAllMocks())

  it('预览、创建和查询任务使用统一接口', async () => {
    httpRequest.mockResolvedValue({})
    const selection = { taskId: 't1', source: 'TASK_POOL', excludedItemIds: ['i2'] }
    await batchOperationApi.preview(selection)
    await batchOperationApi.create({
      operationId: 'batch-1', action: 'DISCARD', selection,
    })
    await batchOperationApi.get('job-1')

    expect(httpRequest).toHaveBeenNthCalledWith(1, '/api/batch-operation-jobs/preview', {
      method: 'POST', json: selection,
    })
    expect(httpRequest).toHaveBeenNthCalledWith(2, '/api/batch-operation-jobs', {
      method: 'POST',
      json: { operationId: 'batch-1', action: 'DISCARD', selection },
    })
    expect(httpRequest).toHaveBeenNthCalledWith(3, '/api/batch-operation-jobs/job-1')
  })
})
