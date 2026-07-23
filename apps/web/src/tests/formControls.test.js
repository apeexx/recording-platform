import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import fs from 'node:fs'
import path from 'node:path'
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
    expect(wrapper.get('[data-handle="min"]').attributes('aria-valuetext')).toBe('10 秒')
    expect(wrapper.get('[data-handle="max"]').attributes('aria-valuetext')).toBe('100 秒')
  })

  it('双端时长滑块数值跟随圆点并处理边缘与相邻状态', async () => {
    const wrapper = mount(DurationRangeSlider, { props: { minValue: 1, maxValue: 600 } })
    expect(wrapper.get('.duration-range-value.is-min').attributes('style')).toContain('left: 0%')
    expect(wrapper.get('.duration-range-value.is-max').attributes('style')).toContain('left: 100%')
    expect(wrapper.get('.duration-range-value.is-min').classes()).toContain('is-start')
    expect(wrapper.get('.duration-range-value.is-max').classes()).toContain('is-end')

    await wrapper.setProps({ minValue: 300, maxValue: 301 })
    expect(wrapper.get('.duration-range-value.is-min').classes()).toContain('is-close')
    expect(wrapper.get('.duration-range-value.is-max').classes()).toContain('is-close')
  })

  it('双端时长滑块使用细轨道和白色小圆点且不保留嵌套卡片', () => {
    const styles = fs.readFileSync(path.resolve('src/styles/form-controls.css'), 'utf8')
    const source = fs.readFileSync(path.resolve('src/components/form/DurationRangeSlider.vue'), 'utf8')
    expect(styles).toMatch(/--duration-thumb-size:\s*18px/)
    expect(styles).toMatch(/left:\s*calc\(var\(--duration-thumb-size\)\/2\)/)
    expect(styles).toMatch(/width:\s*calc\(100% - var\(--duration-thumb-size\)\)/)
    expect(styles).toMatch(/\.duration-range-track\s*\{[^}]*height:\s*6px/)
    expect(styles).toMatch(/::-webkit-slider-thumb\s*\{[^}]*background:\s*var\(--card\)/)
    expect(styles).toContain('::-webkit-slider-runnable-track')
    expect(styles).toContain('::-moz-range-track')
    expect(styles).toMatch(/background:\s*transparent/)
    expect(source).not.toContain('duration-range-card')
  })
})
