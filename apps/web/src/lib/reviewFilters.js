const clean = (values = []) => [...new Set(values.filter(Boolean))]

export const defaultReviewFilters = () => ({
  itemCodes: [],
  itemCodeQuery: '',
  statuses: [],
  collectorIds: [],
  reviewerIds: [],
  includeUnassignedReviewer: false,
  results: [],
})

export function normalizeReviewFilters(filters = {}) {
  return {
    itemCodes: clean(filters.itemCodes),
    itemCodeQuery: filters.itemCodeQuery?.trim() || '',
    statuses: clean(filters.statuses),
    collectorIds: clean(filters.collectorIds),
    reviewerIds: clean(filters.reviewerIds),
    includeUnassignedReviewer: Boolean(filters.includeUnassignedReviewer),
    results: clean(filters.results),
  }
}

export function buildReviewPoolQuery(page, size, filters = {}) {
  const query = new URLSearchParams({ page: String(page), size: String(size) })
  const normalized = normalizeReviewFilters(filters)
  normalized.itemCodes.forEach((value) => query.append('itemCode', value))
  if (normalized.itemCodeQuery) query.set('itemCodeQuery', normalized.itemCodeQuery)
  normalized.statuses.forEach((value) => query.append('status', value))
  normalized.collectorIds.forEach((value) => query.append('collectorId', value))
  normalized.reviewerIds.forEach((value) => query.append('reviewerId', value))
  if (normalized.includeUnassignedReviewer) query.set('includeUnassignedReviewer', 'true')
  normalized.results.forEach((value) => query.append('result', value))
  return `?${query}`
}

export function reviewSelectionFilters(filters = {}) {
  return normalizeReviewFilters(filters)
}
