import { afterEach, describe, expect, it } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import HelpPopover from '../components/form/HelpPopover.vue'

describe('HelpPopover', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('悬浮和键盘聚焦临时展示说明，离开后隐藏', async () => {
    const wrapper = mount(HelpPopover, {
      props: { label: '任务完成度说明', content: '已完成条目除以非废弃条目。' },
      attachTo: document.body,
    })
    const trigger = wrapper.get('button')

    await trigger.trigger('pointerenter')
    await flushPromises()
    expect(document.body.querySelector('[role="tooltip"]')?.textContent).toContain('已完成条目')
    expect(trigger.attributes('aria-expanded')).toBe('true')

    await trigger.trigger('pointerleave')
    await flushPromises()
    expect(document.body.querySelector('[role="tooltip"]')).toBeNull()

    await trigger.trigger('focus')
    await flushPromises()
    expect(document.body.querySelector('[role="tooltip"]')).not.toBeNull()
    await trigger.trigger('blur')
    await flushPromises()
    expect(document.body.querySelector('[role="tooltip"]')).toBeNull()
    wrapper.unmount()
  })

  it('点击固定说明并支持再次点击、外部点击和 Esc 关闭', async () => {
    const wrapper = mount(HelpPopover, {
      props: { label: '审核处理进度说明', content: '返修中和已完成计入已处理。' },
      attachTo: document.body,
    })
    const trigger = wrapper.get('button')

    await trigger.trigger('click')
    await trigger.trigger('pointerleave')
    await flushPromises()
    expect(document.body.querySelector('[role="tooltip"]')).not.toBeNull()

    await trigger.trigger('click')
    await flushPromises()
    expect(document.body.querySelector('[role="tooltip"]')).toBeNull()

    await trigger.trigger('click')
    document.body.dispatchEvent(new MouseEvent('pointerdown', { bubbles: true }))
    await flushPromises()
    expect(document.body.querySelector('[role="tooltip"]')).toBeNull()

    await trigger.trigger('click')
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await flushPromises()
    expect(document.body.querySelector('[role="tooltip"]')).toBeNull()
    wrapper.unmount()
  })

  it('通过 Teleport 固定定位且阻止问号触发轮播拖动', async () => {
    const wrapper = mount(HelpPopover, {
      props: { label: '说明', content: '说明内容' },
      attachTo: document.body,
    })
    const trigger = wrapper.get('button')
    Object.defineProperty(trigger.element, 'getBoundingClientRect', {
      value: () => ({ left: 100, right: 124, top: 80, bottom: 104, width: 24, height: 24 }),
    })

    await trigger.trigger('click')
    await flushPromises()
    const tooltip = document.body.querySelector('[role="tooltip"]')
    expect(tooltip).not.toBeNull()
    expect(tooltip.style.position).toBe('fixed')
    expect(tooltip.style.left).not.toBe('')
    expect(tooltip.style.top).not.toBe('')

    let bubbled = false
    wrapper.element.addEventListener('pointerdown', () => { bubbled = true })
    await trigger.trigger('pointerdown')
    expect(bubbled).toBe(false)
    wrapper.unmount()
  })

  it('多个实例使用不同说明 ID 且点击新说明会关闭旧说明', async () => {
    const first = mount(HelpPopover, {
      props: { label: '第一项说明', content: '第一项内容' },
      attachTo: document.body,
    })
    const second = mount(HelpPopover, {
      props: { label: '第二项说明', content: '第二项内容' },
      attachTo: document.body,
    })
    await first.get('button').trigger('click')
    await flushPromises()
    const firstId = first.get('button').attributes('aria-describedby')
    await second.get('button').trigger('click')
    await flushPromises()
    const secondId = second.get('button').attributes('aria-describedby')
    expect(firstId).not.toBe(secondId)
    expect(document.body.querySelectorAll('[role="tooltip"]')).toHaveLength(1)
    expect(document.body.querySelector('[role="tooltip"]').textContent).toContain('第二项内容')
    first.unmount()
    second.unmount()
  })
})
