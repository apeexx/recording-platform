import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const route = { query: {}, params: {} }
const router = { replace: vi.fn(), push: vi.fn() }
vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => router,
}))
vi.mock('../lib/taskApi.js', () => ({
  taskApi: {
    list: vi.fn(),
    prepareExport: vi.fn(),
    exportItems: vi.fn(),
  },
}))
vi.mock('../lib/reportApi.js', () => ({
  reportApi: {
    tasks: vi.fn(),
    taskCollectors: vi.fn(),
  },
}))
vi.mock('../composables/useNotifications.js', () => ({
  useNotifications: () => ({ error: vi.fn(), success: vi.fn() }),
}))

import { taskApi } from '../lib/taskApi.js'
import { reportApi } from '../lib/reportApi.js'
import DateRangePicker from '../components/form/DateRangePicker.vue'
import TaskSearchSelect from '../components/form/TaskSearchSelect.vue'
import CollectorStatisticsPage from '../pages/admin/reports/CollectorStatisticsPage.vue'

describe('采集员统计筛选控件', () => {
  beforeEach(() => {
    route.query = {}
    router.replace.mockReset().mockResolvedValue()
    router.push.mockReset()
    taskApi.list.mockReset().mockResolvedValue({
      items: [
        { id: 'task-1', taskCode: 'T000001', name: '台州正式数据' },
        { id: 'task-2', taskCode: 'T000002', name: '普通话数据' },
      ],
      total: 2,
    })
    reportApi.tasks.mockReset().mockResolvedValue({
      submissions: {}, completions: {}, submissionHourDistribution: [],
    })
    reportApi.taskCollectors.mockReset().mockResolvedValue({ items: [], total: 0 })
  })

  it('任务搜索与选择合并为一个组合框并回传选中任务', async () => {
    const wrapper = mount(TaskSearchSelect, { attachTo: document.body })
    await flushPromises()

    expect(wrapper.find('select').exists()).toBe(false)
    const input = wrapper.get('[role="combobox"]')
    await input.trigger('focus')
    await input.setValue('台州')
    expect(wrapper.findAll('[role="option"]')).toHaveLength(1)
    await wrapper.get('[role="option"]').trigger('click')

    expect(wrapper.emitted('update:modelValue')).toEqual([['task-1']])
    expect(input.element.value).toContain('T000001')
    wrapper.unmount()
  })

  it('日期范围第二次选择自动排序起止日期并提交', async () => {
    const wrapper = mount(DateRangePicker, {
      props: { fromDate: '2026-07-30', toDate: '2026-07-30', today: '2026-07-30' },
      attachTo: document.body,
    })
    await wrapper.get('.date-range-trigger').trigger('click')
    await wrapper.get('[data-date="2026-07-20"]').trigger('click')
    await wrapper.get('[data-date="2026-07-12"]').trigger('click')

    expect(wrapper.emitted('change')).toEqual([[
      { fromDate: '2026-07-12', toDate: '2026-07-20' },
    ]])
    expect(wrapper.find('.date-range-popover').exists()).toBe(false)
    wrapper.unmount()
  })

  it('选择任务立即查询且表头重复点击切换升降序', async () => {
    const wrapper = mount(CollectorStatisticsPage, {
      global: {
        stubs: {
          PageActions: { template: '<div><slot /></div>' },
          AsyncState: { template: '<div><slot /></div>' },
          StageSummaryPanel: true,
          SubmissionHourDistribution: true,
          PaginationControls: true,
          DateRangePicker: true,
          TaskSearchSelect: true,
        },
      },
    })

    wrapper.getComponent({ name: 'TaskSearchSelect' }).vm.$emit('update:modelValue', 'task-1')
    await flushPromises()
    expect(reportApi.tasks).toHaveBeenCalledTimes(1)
    expect(reportApi.taskCollectors).toHaveBeenLastCalledWith('task-1', expect.objectContaining({
      sortBy: 'completionCount',
      sortDirection: 'desc',
    }))

    await wrapper.findAll('button').find(button => button.text() === '昨天').trigger('click')
    await flushPromises()
    expect(reportApi.taskCollectors).toHaveBeenCalledTimes(2)
    expect(reportApi.taskCollectors).toHaveBeenLastCalledWith('task-1', expect.objectContaining({
      fromDate: expect.any(String),
      toDate: expect.any(String),
    }))

    await wrapper.get('[data-sort="submissionCount"]').trigger('click')
    await flushPromises()
    expect(reportApi.taskCollectors).toHaveBeenLastCalledWith('task-1', expect.objectContaining({
      sortBy: 'submissionCount',
      sortDirection: 'desc',
      page: 0,
    }))
    await wrapper.get('[data-sort="submissionCount"]').trigger('click')
    await flushPromises()
    expect(reportApi.taskCollectors).toHaveBeenLastCalledWith('task-1', expect.objectContaining({
      sortBy: 'submissionCount',
      sortDirection: 'asc',
    }))
    expect(wrapper.text()).not.toContain('查询统计')
    wrapper.unmount()
  })
})
