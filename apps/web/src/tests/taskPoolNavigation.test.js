import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('../lib/httpClient.js', () => ({
  httpRequest: vi.fn(),
  configureSessionReplacedHandler: vi.fn(),
  markWebSessionEstablished: vi.fn(),
}))

import { httpRequest } from '../lib/httpClient.js'
import TaskPoolPage from '../pages/admin/tasks/TaskPoolPage.vue'
import { TASK_POOL_TASK_STORAGE_KEY } from '../lib/taskPoolState.js'

const task = {
  id: 'task-1', taskCode: 'T000001', name: '测试任务',
  configuration: { humanReviewEnabled: true },
}

function responseFor(url) {
  if (url === '/api/tasks?page=0&size=100') return { items: [task] }
  if (url.startsWith('/api/tasks/task-1/items')) return {
    items: [{ id: 'item-1', itemCode: 'T000001-0000001', status: 'SUBMITTED', revision: 2 }],
    total: 120,
  }
  if (url.startsWith('/api/batch-operation-jobs')) return []
  throw new Error(`unexpected request: ${url}`)
}

async function mountPool(initialPath) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/admin/pool', component: TaskPoolPage },
      { path: '/admin/items/:itemId', component: { template: '<div>详情</div>' } },
      { path: '/admin/dashboard', component: { template: '<div>大屏</div>' } },
    ],
  })
  await router.push(initialPath)
  await router.isReady()
  const wrapper = mount({ template: '<router-view />' }, {
    global: {
      plugins: [router],
      stubs: {
        PageActions: { template: '<header><slot /></header>' },
        HelpPopover: true,
        TaskBatchActionMenu: true,
        TaskItemFilters: { props: ['modelValue'], template: '<span class="filters-state">{{ JSON.stringify(modelValue) }}</span>' },
        PaginationControls: { props: ['page', 'size'], template: '<span class="page-state">{{ page }}-{{ size }}</span>' },
        BaseSelect: {
          props: ['modelValue'],
          emits: ['update:modelValue'],
          template: '<button class="choose-task" @click="$emit(\'update:modelValue\', \'task-1\')">选择任务</button>',
        },
      },
    },
  })
  await flushPromises()
  return { router, wrapper }
}

describe('任务数据池导航状态', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    window.localStorage.clear()
    httpRequest.mockImplementation(url => Promise.resolve(responseFor(url)))
  })

  it('从详情历史返回时恢复缓存任务、筛选和分页', async () => {
    window.localStorage.setItem(TASK_POOL_TASK_STORAGE_KEY, 'task-1')
    const path = '/admin/pool?page=2&size=50&group=SUBMITTED&collectorId=MINI-1&includeUnassigned=true&result=AUDIO_ONLY&sourceItemIdQuery=source-8'
    const { router, wrapper } = await mountPool(path)

    expect(httpRequest.mock.calls.map(([url]) => url)).toContain(
      '/api/tasks/task-1/items?page=2&size=50&group=SUBMITTED&collectorId=MINI-1&includeUnassigned=true&result=AUDIO_ONLY&sourceItemIdQuery=source-8'
    )
    expect(wrapper.get('.page-state').text()).toBe('2-50')
    expect(wrapper.get('.filters-state').text()).toContain('"groups":["SUBMITTED"]')

    await router.push('/admin/items/item-1')
    router.back()
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe(path)
    expect(wrapper.get('.page-state').text()).toBe('2-50')
    wrapper.unmount()
  })

  it('从其他页面重新进入时重置临时状态但继续恢复任务', async () => {
    window.localStorage.setItem(TASK_POOL_TASK_STORAGE_KEY, 'task-1')
    const { router, wrapper } = await mountPool('/admin/pool?page=4&size=50&group=DISCARDED')

    await router.push('/admin/dashboard')
    await router.push('/admin/pool')
    await flushPromises()

    const itemUrls = httpRequest.mock.calls.map(([url]) => url).filter(url => url.startsWith('/api/tasks/task-1/items'))
    expect(itemUrls.at(-1)).toBe('/api/tasks/task-1/items?page=0&size=20')
    expect(wrapper.get('.page-state').text()).toBe('0-20')
    expect(wrapper.get('.filters-state').text()).toContain('"groups":[]')
    wrapper.unmount()
  })

  it('成功加载任务列表后清理已不存在的缓存任务', async () => {
    window.localStorage.setItem(TASK_POOL_TASK_STORAGE_KEY, 'missing-task')
    httpRequest.mockImplementation(url => {
      if (url === '/api/tasks?page=0&size=100') return Promise.resolve({ items: [task] })
      if (url === '/api/tasks/missing-task') return Promise.reject({ status: 404, message: '任务不存在' })
      throw new Error(`unexpected request: ${url}`)
    })

    const { wrapper } = await mountPool('/admin/pool')
    expect(window.localStorage.getItem(TASK_POOL_TASK_STORAGE_KEY)).toBeNull()
    expect(httpRequest).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  it('缓存任务不在首批列表时通过详情确认并恢复', async () => {
    window.localStorage.setItem(TASK_POOL_TASK_STORAGE_KEY, 'task-101')
    const cachedTask = { ...task, id: 'task-101', taskCode: 'T000101' }
    httpRequest.mockImplementation(url => {
      if (url === '/api/tasks?page=0&size=100') return Promise.resolve({ items: [task] })
      if (url === '/api/tasks/task-101') return Promise.resolve(cachedTask)
      if (url.startsWith('/api/tasks/task-101/items')) return Promise.resolve({ items: [], total: 0 })
      if (url.startsWith('/api/batch-operation-jobs')) return Promise.resolve([])
      throw new Error(`unexpected request: ${url}`)
    })

    const { wrapper } = await mountPool('/admin/pool')
    expect(httpRequest.mock.calls.map(([url]) => url)).toContain('/api/tasks/task-101/items?page=0&size=20')
    expect(window.localStorage.getItem(TASK_POOL_TASK_STORAGE_KEY)).toBe('task-101')
    wrapper.unmount()
  })

  it('数据减少后将越界页夹取到最后一页并规范化 URL', async () => {
    window.localStorage.setItem(TASK_POOL_TASK_STORAGE_KEY, 'task-1')
    httpRequest.mockImplementation(url => {
      if (url === '/api/tasks?page=0&size=100') return Promise.resolve({ items: [task] })
      if (url === '/api/tasks/task-1/items?page=9&size=20') return Promise.resolve({ items: [], total: 21 })
      if (url === '/api/tasks/task-1/items?page=1&size=20') return Promise.resolve({ items: [], total: 21 })
      if (url.startsWith('/api/batch-operation-jobs')) return Promise.resolve([])
      throw new Error(`unexpected request: ${url}`)
    })

    const { router, wrapper } = await mountPool('/admin/pool?page=9')
    expect(router.currentRoute.value.fullPath).toBe('/admin/pool?page=1')
    expect(httpRequest.mock.calls.map(([url]) => url)).toContain('/api/tasks/task-1/items?page=1&size=20')
    wrapper.unmount()
  })

  it('进入页面时从 URL 移除非法分页值', async () => {
    const { router, wrapper } = await mountPool('/admin/pool?page=2abc&size=100')
    expect(router.currentRoute.value.fullPath).toBe('/admin/pool')
    wrapper.unmount()
  })

  it('用户选择任务后写入长期缓存', async () => {
    const { wrapper } = await mountPool('/admin/pool')
    await wrapper.get('.choose-task').trigger('click')
    await flushPromises()

    expect(window.localStorage.getItem(TASK_POOL_TASK_STORAGE_KEY)).toBe('task-1')
    wrapper.unmount()
  })
})
