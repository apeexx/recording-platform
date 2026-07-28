import { computed, ref } from 'vue'

export function useBatchSelection(rows, total) {
  const mode = ref('PAGE')
  const selectedIds = ref(new Set())
  const excludedIds = ref(new Set())

  function isSelected(row) {
    return mode.value === 'ALL'
      ? !excludedIds.value.has(row.id)
      : selectedIds.value.has(row.id)
  }

  function toggle(row) {
    if (mode.value === 'ALL') {
      const next = new Set(excludedIds.value)
      next.has(row.id) ? next.delete(row.id) : next.add(row.id)
      excludedIds.value = next
      return
    }
    const next = new Set(selectedIds.value)
    next.has(row.id) ? next.delete(row.id) : next.add(row.id)
    selectedIds.value = next
  }

  const pageAllSelected = computed(() =>
    rows.value.length > 0 && rows.value.every(isSelected)
  )

  const selectedCount = computed(() => mode.value === 'ALL'
    ? Math.max(Number(total.value || 0) - excludedIds.value.size, 0)
    : selectedIds.value.size
  )

  function togglePage() {
    if (mode.value === 'ALL') {
      const next = new Set(excludedIds.value)
      const shouldExclude = pageAllSelected.value
      rows.value.forEach(row => shouldExclude ? next.add(row.id) : next.delete(row.id))
      excludedIds.value = next
      return
    }
    selectedIds.value = pageAllSelected.value
      ? new Set()
      : new Set(rows.value.map(row => row.id))
  }

  function selectAllMatching() {
    mode.value = 'ALL'
    selectedIds.value = new Set()
    excludedIds.value = new Set()
  }

  function clear() {
    mode.value = 'PAGE'
    selectedIds.value = new Set()
    excludedIds.value = new Set()
  }

  function clearPageMode() {
    if (mode.value === 'PAGE') clear()
  }

  function pageCommands() {
    return rows.value.filter(isSelected).map(row => ({
      itemId: row.id,
      expectedRevision: row.revision,
      collectorId: row.collectorId,
    }))
  }

  function selectionPayload(taskId, source, filters = {}) {
    return {
      taskId,
      source,
      excludedItemIds: [...excludedIds.value],
      ...filters,
    }
  }

  return {
    mode,
    selectedIds,
    excludedIds,
    selectedCount,
    pageAllSelected,
    isSelected,
    toggle,
    togglePage,
    selectAllMatching,
    clear,
    clearPageMode,
    pageCommands,
    selectionPayload,
  }
}
