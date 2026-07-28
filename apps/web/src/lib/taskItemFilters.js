export const defaultTaskItemFilters = () => ({
  group: 'ALL',
  collectorIds: [],
  includeUnassigned: false,
  result: 'ALL',
})

export function selectionFilters(filters = {}) {
  return {
    group: filters.group || 'ALL',
    collectorIds: [...(filters.collectorIds || [])],
    includeUnassigned: Boolean(filters.includeUnassigned),
    result: filters.result || 'ALL',
  }
}

export function buildTaskItemQuery(page, size, filters = {}) {
  const query = new URLSearchParams({ page: String(page), size: String(size) })
  const normalized = selectionFilters(filters)
  if (normalized.group !== 'ALL') query.set('group', normalized.group)
  normalized.collectorIds.forEach((id) => query.append('collectorId', id))
  if (normalized.includeUnassigned) query.set('includeUnassigned', 'true')
  if (normalized.result !== 'ALL') query.set('result', normalized.result)
  return `?${query}`
}
