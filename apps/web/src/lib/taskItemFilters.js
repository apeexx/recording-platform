const clean = (values = []) => [...new Set(values.filter(Boolean))]

export const defaultTaskItemFilters = () => ({
  itemCodes: [],
  groups: [],
  collectorIds: [],
  includeUnassigned: false,
  results: [],
})

export function selectionFilters(filters = {}) {
  const legacyGroups = filters.group && filters.group !== 'ALL' ? [filters.group] : []
  const legacyResults = filters.result && filters.result !== 'ALL' ? [filters.result] : []
  return {
    itemCodes: clean(filters.itemCodes),
    groups: clean(filters.groups?.length ? filters.groups : legacyGroups),
    collectorIds: clean(filters.collectorIds),
    includeUnassigned: Boolean(filters.includeUnassigned),
    results: clean(filters.results?.length ? filters.results : legacyResults),
  }
}

export function buildTaskItemQuery(page, size, filters = {}) {
  const query = new URLSearchParams({ page: String(page), size: String(size) })
  const normalized = selectionFilters(filters)
  normalized.itemCodes.forEach((code) => query.append('itemCode', code))
  if (filters.itemCodeQuery?.trim()) query.set('itemCodeQuery', filters.itemCodeQuery.trim())
  normalized.groups.forEach((group) => query.append('group', group))
  normalized.collectorIds.forEach((id) => query.append('collectorId', id))
  if (normalized.includeUnassigned) query.set('includeUnassigned', 'true')
  normalized.results.forEach((result) => query.append('result', result))
  return `?${query}`
}
