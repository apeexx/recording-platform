import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

vi.mock('../lib/httpClient.js', () => ({ httpRequest: vi.fn() }))

import { httpRequest } from '../lib/httpClient.js'
import TaskGrantSearchSelect from '../components/form/TaskGrantSearchSelect.vue'

describe('TaskGrantSearchSelect', () => {
  beforeEach(() => vi.clearAllMocks())

  it('only exposes active users from active task grants', async () => {
    httpRequest.mockResolvedValue({
      items: [
        { userId: 'MINI-1', userName: '有效采集员', userLoginName: '100001', userStatus: 'ACTIVE', status: 'ACTIVE' },
        { userId: 'MINI-2', userName: '停用采集员', userLoginName: '100002', userStatus: 'DISABLED', status: 'ACTIVE' },
      ],
      total: 2,
    })
    const wrapper = mount(TaskGrantSearchSelect, {
      props: { taskId: 'task-1', modelValue: '' },
    })
    await flushPromises()

    expect(httpRequest).toHaveBeenCalledWith(
      '/api/tasks/task-1/grants?page=0&size=50&status=ACTIVE'
    )
    expect(wrapper.find('select').text()).toContain('有效采集员')
    expect(wrapper.find('select').text()).not.toContain('停用采集员')

    await wrapper.find('select').setValue('MINI-1')
    expect(wrapper.emitted('update:modelValue')).toEqual([['MINI-1']])
  })
})
