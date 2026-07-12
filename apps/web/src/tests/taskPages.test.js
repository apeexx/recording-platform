import { beforeEach, describe, expect, it, vi } from 'vitest'
vi.mock('../lib/httpClient.js', () => ({ httpRequest: vi.fn() }))
import { httpRequest } from '../lib/httpClient.js'
import { platformApi } from '../lib/platformApi.js'
import { taskApi } from '../lib/taskApi.js'
import { flushPromises, mount } from '@vue/test-utils'
import PlatformListPage from '../pages/admin/platforms/PlatformListPage.vue'

describe('平台与任务页面 API', () => {
  beforeEach(() => vi.clearAllMocks())
  it('平台写操作携带幂等键', async () => {
    httpRequest.mockResolvedValue({ id: 'p1' })
    await platformApi.create({ code: 'DOUYIN', name: '抖音' }, 'op-1')
    expect(httpRequest).toHaveBeenCalledWith('/api/platforms', {
      method: 'POST', json: { code: 'DOUYIN', name: '抖音' }, idempotencyKey: 'op-1'
    })
  })
  it('任务状态和数据池请求使用后端真实路径', async () => {
    httpRequest.mockResolvedValue({})
    await taskApi.transition('t1', 'publish', 'op-2')
    await taskApi.items('t1', 2, 30)
    await taskApi.versions('t1')
    expect(httpRequest).toHaveBeenNthCalledWith(1, '/api/tasks/t1/publish', { method: 'POST', idempotencyKey: 'op-2' })
    expect(httpRequest).toHaveBeenNthCalledWith(2, '/api/tasks/t1/items?page=2&size=30')
    expect(httpRequest).toHaveBeenNthCalledWith(3, '/api/tasks/t1/versions')
  })
  it('导入使用 multipart 且不手写内容类型', async () => {
    httpRequest.mockResolvedValue({ importJobId: 'j1' })
    const file = new File(['a'], 'items.csv')
    await taskApi.importItems('t1', file, 'op-3')
    const options = httpRequest.mock.calls[0][1]
    expect(httpRequest.mock.calls[0][0]).toBe('/api/import-jobs')
    expect(options.body).toBeInstanceOf(FormData)
    expect(options.idempotencyKey).toBe('op-3')
  })

  it('批量废弃按条目携带 revision 并返回逐条结果', async () => {
    httpRequest.mockResolvedValue([{ itemId: 'i1', success: true }])
    const result = await taskApi.batchAction('discard', [{ itemId: 'i1', expectedRevision: 3 }], 'batch-1')
    expect(result[0].success).toBe(true)
    expect(httpRequest).toHaveBeenCalledWith('/api/task-items/batch/discard', {
      method: 'POST', json: { operationId: 'batch-1', items: [{ itemId: 'i1', expectedRevision: 3 }] }
    })
  })

  it('平台页面呈现空状态和可重试失败态', async () => {
    httpRequest.mockResolvedValueOnce({ items: [] })
    const wrapper = mount(PlatformListPage)
    await flushPromises()
    expect(wrapper.text()).toContain('暂无数据')
    wrapper.unmount()

    httpRequest.mockRejectedValueOnce(new Error('平台加载失败'))
    const failed = mount(PlatformListPage)
    await flushPromises()
    expect(failed.text()).toContain('平台加载失败')
    expect(failed.text()).toContain('重试')
  })
})
