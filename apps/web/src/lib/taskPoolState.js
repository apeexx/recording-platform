import { defaultTaskItemFilters, selectionFilters } from './taskItemFilters.js'

export const TASK_POOL_TASK_STORAGE_KEY = 'recording-platform:task-pool:last-task-id'

const PAGE_SIZES = new Set([10, 20, 50])
const MAX_PAGE = 2_147_483_647

function values(value) {
  const entries = Array.isArray(value) ? value : value == null ? [] : [value]
  return [...new Set(entries.map(entry => String(entry).trim()).filter(Boolean))]
}

function browserStorage() {
  try {
    return typeof window === 'undefined' ? null : window.localStorage
  } catch {
    return null
  }
}

function pageNumber(value) {
  if (Array.isArray(value)) return 0
  const text = String(value ?? '')
  if (!/^(0|[1-9]\d*)$/.test(text)) return 0
  const number = Number(text)
  return Number.isSafeInteger(number) && number <= MAX_PAGE ? number : 0
}

function pageSize(value) {
  if (Array.isArray(value)) return 20
  const number = Number(String(value ?? ''))
  return PAGE_SIZES.has(number) ? number : 20
}

export function parseTaskPoolRouteState(query = {}) {
  return {
    page: pageNumber(query.page),
    size: pageSize(query.size),
    filters: {
      ...defaultTaskItemFilters(),
      itemCodes: values(query.itemCode),
      groups: values(query.group),
      collectorIds: values(query.collectorId),
      includeUnassigned: values(query.includeUnassigned).includes('true'),
      results: values(query.result),
      sourceItemIdQuery: values(query.sourceItemIdQuery)[0] || '',
    },
  }
}

export function buildTaskPoolRouteQuery({ page = 0, size = 20, filters = {} } = {}) {
  const normalized = selectionFilters(filters)
  const query = {}
  if (page > 0) query.page = String(page)
  if (size !== 20 && PAGE_SIZES.has(size)) query.size = String(size)
  if (normalized.itemCodes.length) query.itemCode = normalized.itemCodes
  if (normalized.groups.length) query.group = normalized.groups
  if (normalized.collectorIds.length) query.collectorId = normalized.collectorIds
  if (normalized.includeUnassigned) query.includeUnassigned = 'true'
  if (normalized.results.length) query.result = normalized.results
  if (normalized.sourceItemIdQuery) query.sourceItemIdQuery = normalized.sourceItemIdQuery
  return query
}

export function readLastTaskId(storage = browserStorage()) {
  try {
    return String(storage?.getItem(TASK_POOL_TASK_STORAGE_KEY) || '').trim()
  } catch {
    return ''
  }
}

export function writeLastTaskId(taskId, storage = browserStorage()) {
  try {
    const value = String(taskId || '').trim()
    if (value) storage?.setItem(TASK_POOL_TASK_STORAGE_KEY, value)
    else storage?.removeItem(TASK_POOL_TASK_STORAGE_KEY)
  } catch {
    // Storage may be unavailable in privacy-restricted browser contexts.
  }
}

export function clearLastTaskId(storage = browserStorage()) {
  try {
    storage?.removeItem(TASK_POOL_TASK_STORAGE_KEY)
  } catch {
    // Storage may be unavailable in privacy-restricted browser contexts.
  }
}
