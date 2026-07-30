const clean = (values = []) => [...new Set(values.filter(Boolean))]

export const defaultTaskItemFilters = () => ({
  itemCodes: [],
  groups: [],
  collectorIds: [],
  includeUnassigned: false,
  results: [],
  sourceItemIdQuery: '',
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
    sourceItemIdQuery: String(filters.sourceItemIdQuery || '').trim(),
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
  if (normalized.sourceItemIdQuery) query.set('sourceItemIdQuery', normalized.sourceItemIdQuery)
  return `?${query}`
}

export function buildTaskItemExportQuery(filters = {}, dates = {}) {
  const query = new URLSearchParams(buildTaskItemQuery(0, 1, filters).slice(1))
  query.delete('page')
  query.delete('size')
  if (dates.fromDate) query.set('fromDate', dates.fromDate)
  if (dates.toDate) query.set('toDate', dates.toDate)
  return query.size ? `?${query}` : ''
}
