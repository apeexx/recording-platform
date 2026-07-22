import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import BaseSelect from '../components/form/BaseSelect.vue'
import ToggleSwitch from '../components/form/ToggleSwitch.vue'
import DurationRangeSlider from '../components/form/DurationRangeSlider.vue'

describe('统一表单控件', () => {
  it('下拉框支持键盘选择并在选择后关闭', async () => {
    const wrapper = mount(BaseSelect, {
      props: {
        modelValue: 'WAV',
        options: [{ value: 'WAV', label: 'WAV' }, { value: 'MP3', label: 'MP3' }]
      },
      attachTo: document.body,
    })
    await wrapper.get('.base-select-trigger').trigger('keydown', { key: 'ArrowDown' })
    expect(wrapper.get('[role="listbox"]').isVisible()).toBe(true)
    await wrapper.get('.base-select-trigger').trigger('keydown', { key: 'ArrowDown' })
    await wrapper.get('.base-select-trigger').trigger('keydown', { key: 'Enter' })
    expect(wrapper.emitted('update:modelValue')).toEqual([['MP3']])
    expect(wrapper.find('[role="listbox"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('胶囊开关回传布尔值', async () => {
    const wrapper = mount(ToggleSwitch, { props: { modelValue: false, label: '人工审核' } })
    await wrapper.get('button').trigger('click')
    expect(wrapper.emitted('update:modelValue')).toEqual([[true]])
    expect(wrapper.get('button').attributes('role')).toBe('switch')
  })

  it('双端时长滑块将边界限制为 1 到 600 秒且不交叉', async () => {
    const wrapper = mount(DurationRangeSlider, { props: { minValue: 10, maxValue: 100 } })
    await wrapper.get('[data-handle="min"]').setValue(120)
    await wrapper.get('[data-handle="max"]').setValue(5)
    expect(wrapper.emitted('update:minValue')).toEqual([[99]])
    expect(wrapper.emitted('update:maxValue')).toEqual([[11]])
    expect(wrapper.text()).toContain('10 秒')
    expect(wrapper.text()).toContain('100 秒')
  })
})
