import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

vi.mock('../lib/userApi.js', () => ({
  userApi: {
    list: vi.fn(),
    search: vi.fn(),
    create: vi.fn(),
    disable: vi.fn(),
    resetPassword: vi.fn(),
    updateCollectorAccount: vi.fn()
  }
}))

import { userApi } from '../lib/userApi.js'
import UsersPage from '../pages/admin/system/UsersPage.vue'

describe('用户管理类型页签', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    userApi.list.mockResolvedValue({ content: [] })
    userApi.search.mockResolvedValue({ content: [] })
  })

  it('默认加载 Web 用户并可切换到小程序用户', async () => {
    const wrapper = mount(UsersPage)
    await flushPromises()

    expect(wrapper.text()).toContain('Web 端账号')
    expect(wrapper.text()).toContain('小程序端账号')
    expect(userApi.search).toHaveBeenLastCalledWith({ query: '', userType: 'WEB', page: 0, size: 100 })

    await wrapper.get('[data-user-type="MINIPROGRAM"]').trigger('click')
    await flushPromises()

    expect(userApi.search).toHaveBeenLastCalledWith({ query: '', userType: 'MINIPROGRAM', page: 0, size: 100 })
    expect(wrapper.text()).not.toContain('创建后台账号')
  })
})
