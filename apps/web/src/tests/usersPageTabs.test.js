import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
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

  afterEach(() => {
    document.body.innerHTML = ''
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
    wrapper.unmount()
  })

  it('将创建入口放在搜索栏右侧并通过弹窗展示表单', async () => {
    const wrapper = mount(UsersPage, { attachTo: document.body })
    await flushPromises()

    expect(wrapper.find('.panel.create').exists()).toBe(false)
    expect(wrapper.get('[data-testid="open-create-user"]').classes()).toContain('create-trigger')
    await wrapper.get('[data-testid="open-create-user"]').trigger('click')

    expect(document.querySelector('[data-testid="create-user-modal"]')).not.toBeNull()
    expect(document.querySelectorAll('[data-testid="create-user-modal"] input')).toHaveLength(3)
    expect(document.querySelector('[data-testid="create-user-modal"] [role="combobox"]')).not.toBeNull()
    wrapper.unmount()
  })

  it('只在确认创建时提交一次并在切换小程序页签时关闭弹窗', async () => {
    userApi.create.mockResolvedValue({ id: 'WEB-test' })
    const wrapper = mount(UsersPage, { attachTo: document.body })
    await flushPromises()
    await wrapper.get('[data-testid="open-create-user"]').trigger('click')

    const modal = document.querySelector('[data-testid="create-user-modal"]')
    const [username, name, password] = modal.querySelectorAll('input')
    username.value = 'reviewer1'; username.dispatchEvent(new Event('input'))
    name.value = '审核员一'; name.dispatchEvent(new Event('input'))
    password.value = 'Password123'; password.dispatchEvent(new Event('input'))
    modal.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
    await flushPromises()

    expect(userApi.create).toHaveBeenCalledTimes(1)
    expect(userApi.create).toHaveBeenCalledWith({ username: 'reviewer1', name: '审核员一', role: 'REVIEWER', initialPassword: 'Password123' })
    expect(document.querySelector('[data-testid="create-user-modal"]')).toBeNull()

    await wrapper.get('[data-testid="open-create-user"]').trigger('click')
    await wrapper.get('[data-user-type="MINIPROGRAM"]').trigger('click')
    expect(wrapper.find('[data-testid="open-create-user"]').exists()).toBe(false)
    expect(document.querySelector('[data-testid="create-user-modal"]')).toBeNull()
    wrapper.unmount()
  })

  it('创建失败时保留弹窗和输入并允许取消', async () => {
    userApi.create.mockRejectedValue(new Error('创建失败'))
    const wrapper = mount(UsersPage, { attachTo: document.body })
    await flushPromises()
    await wrapper.get('[data-testid="open-create-user"]').trigger('click')

    const modal = document.querySelector('[data-testid="create-user-modal"]')
    const username = modal.querySelector('input')
    username.value = 'reviewer2'; username.dispatchEvent(new Event('input'))
    modal.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
    await flushPromises()

    expect(document.querySelector('[data-testid="create-user-modal"]')).not.toBeNull()
    expect(document.querySelector('[data-testid="create-user-modal"] input').value).toBe('reviewer2')
    const cancel = document.querySelector('[data-testid="create-user-cancel"]')
    expect(cancel).not.toBeNull()
    cancel.click()
    await flushPromises()
    expect(document.querySelector('[data-testid="create-user-modal"]')).toBeNull()
    wrapper.unmount()
  })
})
