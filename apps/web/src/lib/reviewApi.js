import { httpRequest } from './httpClient.js'
import { queryString } from './apiUtils.js'
const e = encodeURIComponent
export const reviewApi = {
  tasks() { return httpRequest('/api/reviews/tasks') },
  pool(taskId, page = 0, size = 20) { return httpRequest(`/api/reviews/tasks/${e(taskId)}/pool${queryString({ page, size })}`) },
  claim(taskId, key) { return httpRequest(`/api/reviews/tasks/${e(taskId)}/claim`, { method: 'POST', idempotencyKey: key }) },
	claimItem(itemId, expectedRevision, operationId) { return httpRequest(`/api/reviews/${e(itemId)}/claim`, { method: 'POST', json: { operationId, expectedRevision } }) },
  claimBatch(taskId, count, operationId) { return httpRequest(`/api/reviews/tasks/${e(taskId)}/claim-batch`, { method: 'POST', json: { count, operationId } }) },
  assign(itemId, reviewerId, expectedRevision, operationId) { return httpRequest('/api/reviews/assign', { method: 'POST', json: { itemId, reviewerId, expectedRevision, operationId } }) },
  release(itemId, expectedRevision, operationId) { return httpRequest(`/api/reviews/${e(itemId)}/release`, { method: 'POST', json: { operationId, expectedRevision } }) },
  approve(itemId, expectedRevision, text, operationId) { return httpRequest(`/api/reviews/${e(itemId)}/approve`, { method: 'POST', json: { operationId, expectedRevision, text } }) },
  reject(itemId, expectedRevision, reasons, note, operationId) { return httpRequest(`/api/reviews/${e(itemId)}/reject`, { method: 'POST', json: { operationId, expectedRevision, reasons, note } }) },
  batchApprove(items, operationId) { return httpRequest('/api/reviews/batch/approve', { method: 'POST', json: { operationId, items } }) }
}
