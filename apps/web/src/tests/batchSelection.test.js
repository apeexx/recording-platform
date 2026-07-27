import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import { useBatchSelection } from '../composables/useBatchSelection.js'

describe('跨页批量选择', () => {
  it('支持单条、本页全选和切页清空本页模式', () => {
    const rows = ref([{ id: 'i1' }, { id: 'i2' }])
    const total = ref(5)
    const selection = useBatchSelection(rows, total)

    selection.toggle(rows.value[0])
    expect(selection.selectedCount.value).toBe(1)
    selection.togglePage()
    expect(selection.selectedCount.value).toBe(2)
    expect(selection.pageAllSelected.value).toBe(true)
    selection.clearPageMode()
    expect(selection.selectedCount.value).toBe(0)
  })

  it('跨页模式按总数选中并允许排除单条', () => {
    const rows = ref([{ id: 'i1' }, { id: 'i2' }])
    const total = ref(20)
    const selection = useBatchSelection(rows, total)

    selection.selectAllMatching()
    expect(selection.selectedCount.value).toBe(20)
    selection.toggle(rows.value[0])
    expect(selection.isSelected(rows.value[0])).toBe(false)
    expect(selection.selectedCount.value).toBe(19)
    expect(selection.excludedIds.value).toEqual(new Set(['i1']))
  })
})
