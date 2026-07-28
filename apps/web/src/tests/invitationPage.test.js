import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

vi.mock('../lib/invitationApi.js', () => ({
  invitationApi: {
    list: vi.fn(),
    create: vi.fn(),
    disable: vi.fn()
  }
}))

import { invitationApi } from '../lib/invitationApi.js'
import InvitationCodesPage from '../pages/admin/system/InvitationCodesPage.vue'

describe('邀请码管理页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    invitationApi.list.mockResolvedValue({
      items: [{
        id: 'invite-1',
        name: '审核体验',
        note: '提审使用',
        codeSuffix: 'JKMN',
        maxUses: 5,
        usedCount: 1,
        remainingUses: 4,
        status: 'ACTIVE',
        createdByName: '管理员',
        createdAt: '2026-07-28T08:00:00Z'
      }],
      page: 0,
      size: 20,
      total: 1
    })
  })

  afterEach(() => {
    document.body.innerHTML = ''
    vi.unstubAllGlobals()
  })

  it('只展示邀请码尾号、用量和状态，不展示完整邀请码', async () => {
    const wrapper = mount(InvitationCodesPage)
    await flushPromises()

    expect(wrapper.text()).toContain('****-****-JKMN')
    expect(wrapper.text()).toContain('1 / 5')
    expect(wrapper.text()).toContain('剩余 4 次')
    expect(wrapper.text()).not.toContain('ABCD-EFGH-JKMN')
    wrapper.unmount()
  })

  it('创建后单独展示完整邀请码且关闭后不能从列表再次看到', async () => {
    invitationApi.create.mockResolvedValue({
      id: 'invite-2',
      name: '项目组',
      codeSuffix: 'WXYZ',
      maxUses: 10,
      usedCount: 0,
      remainingUses: 10,
      status: 'ACTIVE',
      invitationCode: 'ABCD-EFGH-WXYZ'
    })
    const writeText = vi.fn().mockResolvedValue()
    vi.stubGlobal('navigator', { clipboard: { writeText } })
    const wrapper = mount(InvitationCodesPage, { attachTo: document.body })
    await flushPromises()

    await wrapper.get('[data-testid="open-create-invitation"]').trigger('click')
    const modal = document.querySelector('[data-testid="create-invitation-modal"]')
    const [name, note, maxUses] = modal.querySelectorAll('input')
    name.value = '项目组'; name.dispatchEvent(new Event('input'))
    note.value = '内部使用'; note.dispatchEvent(new Event('input'))
    maxUses.value = '10'; maxUses.dispatchEvent(new Event('input'))
    modal.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
    await flushPromises()

    const reveal = document.querySelector('[data-testid="invitation-reveal-modal"]')
    expect(reveal.textContent).toContain('ABCD-EFGH-WXYZ')
    reveal.querySelector('[data-testid="copy-invitation"]').click()
    await flushPromises()
    expect(writeText).toHaveBeenCalledWith('ABCD-EFGH-WXYZ')
    reveal.querySelector('[data-testid="close-invitation-reveal"]').click()
    await flushPromises()
    expect(document.body.textContent).not.toContain('ABCD-EFGH-WXYZ')
    wrapper.unmount()
  })

  it('停用前确认并使用新的操作键刷新列表', async () => {
    vi.stubGlobal('confirm', vi.fn(() => true))
    invitationApi.disable.mockResolvedValue({ id: 'invite-1', status: 'DISABLED' })
    const wrapper = mount(InvitationCodesPage)
    await flushPromises()

    await wrapper.get('[data-testid="disable-invitation"]').trigger('click')
    await flushPromises()

    expect(invitationApi.disable).toHaveBeenCalledOnce()
    expect(invitationApi.disable.mock.calls[0][0]).toBe('invite-1')
    expect(invitationApi.disable.mock.calls[0][1]).toMatch(/^invitation-disable-/)
    expect(invitationApi.list).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })
})
