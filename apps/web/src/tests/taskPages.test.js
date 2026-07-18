import { beforeEach, describe, expect, it, vi } from 'vitest'
vi.mock('../lib/httpClient.js', () => ({ httpRequest: vi.fn() }))
import { httpRequest } from '../lib/httpClient.js'
import { taskApi } from '../lib/taskApi.js'
import fs from 'node:fs'
import path from 'node:path'

describe('任务页面 API', () => {
  beforeEach(() => vi.clearAllMocks())
	it('创建任务不发送平台或手填编号', async () => {
	  httpRequest.mockResolvedValue({ id: 't1', taskCode: 'T000001' })
	  const data = { name: '朗读任务', version: { referenceTypes: ['TEXT'], resultType: 'TEXT' } }
	  await taskApi.create(data, 'op-1')
	  expect(httpRequest).toHaveBeenCalledWith('/api/tasks', { method: 'POST', json: data, idempotencyKey: 'op-1' })
	})
	it('文本成果仍保留录音配置，关闭审核时隐藏驳回原因', () => {
	  const source = fs.readFileSync(path.resolve('src/pages/admin/tasks/TaskEditorPage.vue'), 'utf8')
	  expect(source).not.toContain("v-if=\"form.resultType==='AUDIO'\"")
	  expect(source).toContain('v-if="form.humanReviewEnabled"')
	  expect(source).toContain("rejectionReasons: form.humanReviewEnabled")
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

})
