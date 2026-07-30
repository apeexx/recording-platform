import { httpRequest } from './httpClient.js'
import { queryString } from './apiUtils.js'
import { buildReviewPoolQuery } from './reviewFilters.js'
const e = encodeURIComponent
export const reviewApi = {
  tasks() { return httpRequest('/api/reviews/tasks') },
  taskSummary(taskId) { return httpRequest(`/api/reviews/tasks/${e(taskId)}/summary`) },
  pool(taskId, page = 0, size = 20, filters = {}) { return httpRequest(`/api/reviews/tasks/${e(taskId)}/pool${buildReviewPoolQuery(page, size, filters)}`) },
  filterUsers(taskId, role, query = '') { return httpRequest(`/api/reviews/tasks/${e(taskId)}/filter-users${queryString({ role, query })}`) },
  claim(taskId, key) { return httpRequest(`/api/reviews/tasks/${e(taskId)}/claim`, { method: 'POST', idempotencyKey: key }) },
	claimItem(itemId, expectedRevision, operationId) { return httpRequest(`/api/reviews/${e(itemId)}/claim`, { method: 'POST', json: { operationId, expectedRevision } }) },
  claimBatch(taskId, count, operationId) { return httpRequest(`/api/reviews/tasks/${e(taskId)}/claim-batch`, { method: 'POST', json: { count, operationId } }) },
  assign(itemId, reviewerId, expectedRevision, operationId) { return httpRequest('/api/reviews/assign', { method: 'POST', json: { itemId, reviewerId, expectedRevision, operationId } }) },
  release(itemId, expectedRevision, operationId) { return httpRequest(`/api/reviews/${e(itemId)}/release`, { method: 'POST', json: { operationId, expectedRevision } }) },
  approve(itemId, expectedRevision, text, operationId) { return httpRequest(`/api/reviews/${e(itemId)}/approve`, { method: 'POST', json: { operationId, expectedRevision, text } }) },
  reject(itemId, expectedRevision, reasons, note, operationId) { return httpRequest(`/api/reviews/${e(itemId)}/reject`, { method: 'POST', json: { operationId, expectedRevision, reasons, note } }) },
  batchApprove(items, operationId) { return httpRequest('/api/reviews/batch/approve', { method: 'POST', json: { operationId, items } }) },
  batchClaim(items, operationId) { return httpRequest('/api/reviews/batch/claim', { method: 'POST', json: { operationId, items } }) },
  batchAssign(items, reviewerId, operationId) { return httpRequest('/api/reviews/batch/assign', { method: 'POST', json: { operationId, reviewerId, items } }) },
  aiConfig(taskId) { return httpRequest(`/api/reviews/tasks/${e(taskId)}/ai-config`) },
  updateAiConfig(taskId, data, key) { return httpRequest(`/api/reviews/tasks/${e(taskId)}/ai-config`, { method: 'PUT', json: data, idempotencyKey: key }) },
  createAiJob(itemId, data) { return httpRequest(`/api/reviews/${e(itemId)}/ai-jobs`, { method: 'POST', json: data }) },
  aiJob(jobId) { return httpRequest(`/api/reviews/ai-jobs/${e(jobId)}`) },
}
