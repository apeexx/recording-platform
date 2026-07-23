import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: 'task-1' } }),
  useRouter: () => ({ push: vi.fn() }),
}))

vi.mock('../lib/taskApi.js', () => ({
  taskApi: {
    get: vi.fn(),
    items: vi.fn(),
    addItem: vi.fn(),
    importItems: vi.fn(),
    importJob: vi.fn(),
    retryImport: vi.fn(),
    deleteTask: vi.fn(),
    updateItem: vi.fn(),
    deleteItem: vi.fn(),
    discard: vi.fn(),
    restore: vi.fn(),
    batchAction: vi.fn(),
    batchStatus: vi.fn(),
  },
}))

vi.mock('../lib/apiUtils.js', () => ({
  operationId: vi.fn((prefix) => `${prefix}-test`),
}))

vi.mock('../composables/useNotifications.js', () => ({
  useNotifications: () => ({
    success: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
  }),
}))

import { taskApi } from '../lib/taskApi.js'
import TaskDetailPage from '../pages/admin/tasks/TaskDetailPage.vue'

function mountPage() {
  return mount(TaskDetailPage, {
    global: {
      stubs: {
        RouterLink: { template: '<a><slot /></a>' },
        PageActions: { template: '<header><slot /></header>' },
        PaginationControls: true,
        BaseSelect: true,
        TaskItemEditModal: true,
      },
    },
  })
}

function selectCsv(wrapper, name = 'audio.csv') {
  const input = wrapper.get('input[type="file"]')
  const file = new File([
    'referenceText,referenceAudioUrl,referenceVideoUrl\r\n,https://cdn.example.com/audio.wav,\r\n',
  ], name, { type: 'text/csv' })
  Object.defineProperty(input.element, 'files', { configurable: true, value: [file] })
  return input.trigger('change')
}

function startImportButton(wrapper) {
  return wrapper.findAll('button').find((button) => button.text() === '开始导入')
}

describe('任务详情 CSV 导入轮询', () => {
  let wrapper

  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
    taskApi.get.mockResolvedValue({
      id: 'task-1',
      taskCode: 'T000001',
      name: '音频任务',
      lifecycle: 'RUNNING',
      configuration: { referenceTypes: ['AUDIO'], humanReviewEnabled: true },
    })
    taskApi.items.mockResolvedValue({ items: [], total: 0 })
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    vi.useRealTimers()
  })

  it('完整状态对象只含 id 时仍持续查询同一任务直到完成', async () => {
    taskApi.importItems.mockResolvedValue({ importJobId: 'job-1', status: 'PENDING' })
    taskApi.importJob
      .mockResolvedValueOnce({ id: 'job-1', status: 'PROCESSING', totalRows: 10, successRows: 0, failureRows: 0 })
      .mockResolvedValueOnce({ id: 'job-1', status: 'PROCESSING', totalRows: 10, successRows: 4, failureRows: 0 })
      .mockResolvedValueOnce({ id: 'job-1', status: 'COMPLETED', totalRows: 10, successRows: 10, failureRows: 0 })

    wrapper = mountPage()
    await flushPromises()
    await selectCsv(wrapper)
    await startImportButton(wrapper).trigger('click')
    await flushPromises()

    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()
    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()
    expect(wrapper.text()).toContain('已处理 4')
    expect(wrapper.text()).toContain('成功 4')
    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()

    expect(taskApi.importJob).toHaveBeenCalledTimes(3)
    expect(taskApi.importJob.mock.calls).toEqual([['job-1'], ['job-1'], ['job-1']])
    expect(taskApi.items).toHaveBeenCalledTimes(2)

    await vi.advanceTimersByTimeAsync(3000)
    expect(taskApi.importJob).toHaveBeenCalledTimes(3)
  })

  it('部分成功后使用完整状态对象的原任务 id 重试失败行', async () => {
    taskApi.importItems.mockResolvedValue({ importJobId: 'job-partial', status: 'PENDING' })
    taskApi.importJob
      .mockResolvedValueOnce({
        id: 'job-partial',
        status: 'PARTIAL_SUCCESS',
        totalRows: 3,
        successRows: 2,
        failureRows: 1,
        rowErrors: [{ message: '参考内容为空' }],
      })
      .mockResolvedValueOnce({
        id: 'job-partial',
        status: 'COMPLETED',
        totalRows: 1,
        successRows: 1,
        failureRows: 0,
      })
    taskApi.retryImport.mockResolvedValue({ importJobId: 'job-partial', status: 'PENDING' })

    wrapper = mountPage()
    await flushPromises()
    await selectCsv(wrapper, 'partial.csv')
    await startImportButton(wrapper).trigger('click')
    await flushPromises()
    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()

    const retryButton = wrapper.findAll('button').find((button) => button.text() === '重试失败行')
    await retryButton.trigger('click')
    await flushPromises()

    expect(taskApi.retryImport).toHaveBeenCalledWith('job-partial', 'import-retry-test')

    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()
    expect(taskApi.importJob).toHaveBeenLastCalledWith('job-partial')
  })

  it('选择新文件会停止旧任务轮询', async () => {
    taskApi.importItems.mockResolvedValue({ importJobId: 'job-old', status: 'PENDING' })

    wrapper = mountPage()
    await flushPromises()
    await selectCsv(wrapper, 'old.csv')
    await startImportButton(wrapper).trigger('click')
    await flushPromises()
    await selectCsv(wrapper, 'new.csv')

    await vi.advanceTimersByTimeAsync(3000)
    expect(taskApi.importJob).not.toHaveBeenCalled()
  })

  it('组件卸载会停止旧任务轮询', async () => {
    taskApi.importItems.mockResolvedValue({ importJobId: 'job-old', status: 'PENDING' })

    wrapper = mountPage()
    await flushPromises()
    await selectCsv(wrapper)
    await startImportButton(wrapper).trigger('click')
    await flushPromises()
    wrapper.unmount()
    wrapper = null

    await vi.advanceTimersByTimeAsync(3000)
    expect(taskApi.importJob).not.toHaveBeenCalled()
  })
})
