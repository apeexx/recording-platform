import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import fs from 'node:fs'
import path from 'node:path'
import BaseSelect from '../components/form/BaseSelect.vue'
import ToggleSwitch from '../components/form/ToggleSwitch.vue'
import DurationRangeSlider from '../components/form/DurationRangeSlider.vue'

describe('统一表单控件', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

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

  it('双端时长滑块使用统一像素坐标绘制极值圆点和选区', async () => {
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(function () {
      return this.classList?.contains('duration-range-track')
        ? { x: 0, y: 0, left: 0, top: 0, right: 520, bottom: 22, width: 520, height: 22, toJSON() {} }
        : { x: 0, y: 0, left: 0, top: 0, right: 20, bottom: 20, width: 20, height: 20, toJSON() {} }
    })

    const wrapper = mount(DurationRangeSlider, { props: { minValue: 1, maxValue: 600 } })
    await wrapper.vm.$nextTick()

    expect(wrapper.findAll('.duration-range-thumb')).toHaveLength(2)
    expect(wrapper.get('.duration-range-thumb.is-min').attributes('style')).toContain('left: 10px')
    expect(wrapper.get('.duration-range-thumb.is-max').attributes('style')).toContain('left: 510px')
    expect(wrapper.get('.duration-range-selection').attributes('style')).toContain('left: 10px')
    expect(wrapper.get('.duration-range-selection').attributes('style')).toContain('width: 500px')
  })

  it('双端时长滑块支持点击轨道移动最近圆点和显式键盘微调', async () => {
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(function () {
      return this.classList?.contains('duration-range-track')
        ? { x: 0, y: 0, left: 0, top: 0, right: 520, bottom: 22, width: 520, height: 22, toJSON() {} }
        : { x: 0, y: 0, left: 0, top: 0, right: 20, bottom: 20, width: 20, height: 20, toJSON() {} }
    })

    const wrapper = mount(DurationRangeSlider, { props: { minValue: 120, maxValue: 420 } })
    await wrapper.vm.$nextTick()
    wrapper.get('.duration-range-track').element.dispatchEvent(new MouseEvent('pointerdown', {
      bubbles: true,
      button: 0,
      clientX: 410,
    }))
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted('update:maxValue')?.at(-1)).toEqual([480])

    await wrapper.get('[data-handle="min"]').trigger('keydown', { key: 'ArrowRight' })
    expect(wrapper.emitted('update:minValue')?.at(-1)).toEqual([121])
  })

  it('双端时长滑块复刻已验收原型的胶囊轨道与白色圆点', () => {
    const styles = fs.readFileSync(path.resolve('src/styles/form-controls.css'), 'utf8')
    const source = fs.readFileSync(path.resolve('src/components/form/DurationRangeSlider.vue'), 'utf8')
    expect(styles).toMatch(/--duration-thumb-size:\s*20px/)
    expect(styles).toMatch(/--duration-track-height:\s*22px/)
    expect(styles).toMatch(/--duration-selection-height:\s*16px/)
    expect(styles).toMatch(/\.duration-range-thumb\s*\{[^}]*background:\s*var\(--card\)/)
    expect(styles).toMatch(/\.duration-range-native\s*\{[^}]*opacity:\s*0/)
    expect(source).toContain('valueToX')
    expect(source).toContain('clientXToValue')
    expect(source).toContain('duration-range-selection')
  })
})
