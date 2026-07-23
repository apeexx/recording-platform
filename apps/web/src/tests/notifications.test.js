import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import AppToastHost from '../components/feedback/AppToastHost.vue'
import { useNotifications } from '../composables/useNotifications.js'

function clearNotifications() {
  const notifications = useNotifications()
  for (const item of [...notifications.items]) notifications.dismiss(item.id)
}

describe('全局 Toast', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    clearNotifications()
  })

  afterEach(() => {
    clearNotifications()
    vi.useRealTimers()
  })

  it('错误提示默认显示 4.5 秒并自动去重相同消息', () => {
    const notifications = useNotifications()

    notifications.error('操作失败')
    notifications.error('操作失败')

    expect(notifications.items).toHaveLength(1)
    vi.advanceTimersByTime(4499)
    expect(notifications.items).toHaveLength(1)
    vi.advanceTimersByTime(1)
    expect(notifications.items).toHaveLength(0)
  })

  it('成功提示保持 2.6 秒且点击可以关闭', async () => {
    const notifications = useNotifications()
    const wrapper = mount(AppToastHost, { attachTo: document.body })

    notifications.success('保存成功')
    await wrapper.vm.$nextTick()
    expect(document.body.textContent).toContain('保存成功')

    vi.advanceTimersByTime(2599)
    expect(notifications.items).toHaveLength(1)
    await document.body.querySelector('.app-toast').click()
    expect(notifications.items).toHaveLength(0)
    wrapper.unmount()
  })

  it('错误提示使用即时播报，普通提示使用礼貌播报', async () => {
    const notifications = useNotifications()
    const wrapper = mount(AppToastHost, { attachTo: document.body })

    notifications.error('请求失败')
    notifications.info('正在处理')
    await wrapper.vm.$nextTick()

    expect(document.body.querySelector('.app-toast.is-error').getAttribute('aria-live')).toBe('assertive')
    expect(document.body.querySelector('.app-toast.is-info').getAttribute('aria-live')).toBe('polite')
    wrapper.unmount()
  })

  it('错误提示使用不依赖字体的 SVG 图标', async () => {
    const notifications = useNotifications()
    const wrapper = mount(AppToastHost, { attachTo: document.body })

    notifications.error('请求失败')
    await wrapper.vm.$nextTick()

    expect(document.body.querySelector('[data-toast-icon="error"]')).not.toBeNull()
    expect(document.body.querySelector('.app-toast.is-error')?.textContent).not.toContain('!')
    wrapper.unmount()
  })
})
