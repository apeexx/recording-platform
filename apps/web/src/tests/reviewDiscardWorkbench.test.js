import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { ref } from 'vue'

const sessionUser = ref({ id: 'reviewer-1', role: 'REVIEWER', name: '审核员一' })

vi.mock('../composables/useAdminSession.js', () => ({
  useAdminSession: () => ({ user: sessionUser }),
}))
vi.mock('../lib/httpClient.js', () => ({
  httpRequest: vi.fn(),
  configureSessionReplacedHandler: vi.fn(),
  markWebSessionEstablished: vi.fn(),
}))

import { httpRequest } from '../lib/httpClient.js'
import ReviewWorkbenchPage from '../pages/admin/review/ReviewWorkbenchPage.vue'

const item = {
  id: 'item-1', taskId: 'task-1', itemCode: 'T000001-0000001',
  status: 'REVIEW_PENDING', revision: 7,
  collectorId: 'collector-1', assignmentId: 'collector-assignment-1',
  reviewerId: 'reviewer-1', reviewAssignmentId: 'review-assignment-1',
  currentResult: { text: '原始文本', audio: null },
}

describe('审核工作台标记无效', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    window.sessionStorage.clear()
    sessionUser.value = { id: 'reviewer-1', role: 'REVIEWER', name: '审核员一' }
    httpRequest.mockImplementation(url => {
      if (url === '/api/task-items/item-1') return Promise.resolve(item)
      if (url === '/api/tasks/task-1') return Promise.resolve({
        id: 'task-1', configuration: { resultType: 'TEXT', rejectionReasons: [] },
      })
      if (url === '/api/reviews/tasks/task-1/ai-config') return Promise.resolve({})
      if (url === '/api/reviews/item-1/discard') return Promise.resolve({ ...item, status: 'DISCARDED' })
      throw new Error(`unexpected request: ${url}`)
    })
  })

  async function mountWorkbench() {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/admin/review/:itemId', component: ReviewWorkbenchPage },
        { path: '/admin/review/tasks/:taskId', component: { template: '<div>审核池</div>' } },
      ],
    })
    await router.push('/admin/review/item-1')
    await router.isReady()
    const wrapper = mount({ template: '<router-view />' }, {
      global: {
        plugins: [router],
        stubs: {
          PageActions: { template: '<header><slot /></header>' },
          HelpPopover: true,
        },
      },
    })
    await flushPromises()
    return { router, wrapper }
  }

  it('当前审核员确认后标记无效并返回任务审核池', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const { router, wrapper } = await mountWorkbench()

    const button = wrapper.findAll('button').find(entry => entry.text() === '标记为无效')
    expect(button).toBeTruthy()
    await button.trigger('click')
    await flushPromises()

    expect(window.confirm).toHaveBeenCalledWith(
      '确认将该条数据标记为无效？系统会保留采集结果和操作记录，管理员可在废弃数据中恢复。'
    )
    expect(httpRequest).toHaveBeenCalledWith('/api/reviews/item-1/discard', {
      method: 'POST',
      json: { operationId: expect.any(String), expectedRevision: 7 },
    })
    expect(router.currentRoute.value.fullPath).toBe('/admin/review/tasks/task-1')
    wrapper.unmount()
  })

  it('非当前审核员看不到标记无效按钮', async () => {
    sessionUser.value = { id: 'reviewer-2', role: 'REVIEWER', name: '审核员二' }
    const { wrapper } = await mountWorkbench()
    expect(wrapper.text()).not.toContain('标记为无效')
    wrapper.unmount()
  })
})
