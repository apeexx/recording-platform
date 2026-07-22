import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import PaginationControls from '../components/admin/PaginationControls.vue'

describe('数字分页组件', () => {
  it('围绕当前页展示首尾页、省略号和五个连续页码', async () => {
    const wrapper = mount(PaginationControls, {
      props: { numbered: true, page: 11, size: 10, total: 200, pageSizes: [5, 10, 20] }
    })

    const labels = wrapper.findAll('.pagination-page, .pagination-ellipsis').map((item) => item.text())
    expect(labels).toEqual(['1', '…', '10', '11', '12', '13', '14', '…', '20'])
    expect(wrapper.get('.pagination-page.is-active').text()).toBe('12')

    await wrapper.get('[data-page="13"]').trigger('click')
    expect(wrapper.emitted('change')).toEqual([[13]])

    await wrapper.get('.pagination-size .base-select-trigger').trigger('click')
    await wrapper.findAll('.pagination-size .base-select-option').find(option => option.text() === '20 条/页').trigger('click')
    expect(wrapper.emitted('size-change')).toEqual([[20]])
  })

  it('在边界页禁用对应箭头并保留默认简洁模式', () => {
    const first = mount(PaginationControls, {
      props: { numbered: true, page: 0, size: 10, total: 75, pageSizes: [5, 10, 20] }
    })
    expect(first.get('[aria-label="上一页"]').attributes('disabled')).toBeDefined()
    expect(first.get('[aria-label="下一页"]').attributes('disabled')).toBeUndefined()

    const legacy = mount(PaginationControls, { props: { page: 0, size: 20, total: 75 } })
    expect(legacy.text()).toContain('第 1 / 4 页，共 75 条')
    expect(legacy.find('.pagination-size').exists()).toBe(false)
  })
})
