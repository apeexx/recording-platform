import { httpRequest } from './httpClient.js'
import { queryString } from './apiUtils.js'

const encoded = (value) => encodeURIComponent(value)
export const taskApi = {
  list(page = 0, size = 20) { return httpRequest(`/api/tasks${queryString({ page, size })}`) },
  get(id) { return httpRequest(`/api/tasks/${encoded(id)}`) },
  versions(id) { return httpRequest(`/api/tasks/${encoded(id)}/versions`) },
  create(data, key) { return httpRequest('/api/tasks', { method: 'POST', json: data, idempotencyKey: key }) },
  update(id, data, key) { return httpRequest(`/api/tasks/${encoded(id)}`, { method: 'PUT', json: data, idempotencyKey: key }) },
  transition(id, action, key) { return httpRequest(`/api/tasks/${encoded(id)}/${action}`, { method: 'POST', idempotencyKey: key }) },
  items(id, page = 0, size = 20) { return httpRequest(`/api/tasks/${encoded(id)}/items${queryString({ page, size })}`) },
  item(itemId) { return httpRequest(`/api/task-items/${encoded(itemId)}`) },
  addItem(id, data, key) { return httpRequest(`/api/tasks/${encoded(id)}/items`, { method: 'POST', json: data, idempotencyKey: key }) },
  importItems(taskId, file, key) { const body = new FormData(); body.append('taskId', taskId); body.append('file', file); return httpRequest('/api/import-jobs', { method: 'POST', body, idempotencyKey: key }) },
  importJob(id) { return httpRequest(`/api/import-jobs/${encoded(id)}`) },
  retryImport(id, key) { return httpRequest(`/api/import-jobs/${encoded(id)}/retry`, { method: 'POST', idempotencyKey: key }) },
  grants(id, page = 0, size = 20) { return httpRequest(`/api/tasks/${encoded(id)}/grants${queryString({ page, size })}`) },
  grant(id, userId, key) { return httpRequest(`/api/tasks/${encoded(id)}/grants`, { method: 'POST', json: { userId }, idempotencyKey: key }) },
  revokeGrant(id, userId, key) { return httpRequest(`/api/tasks/${encoded(id)}/grants/${encoded(userId)}`, { method: 'DELETE', idempotencyKey: key }) },
  accessRequests(id, page = 0, size = 20) { return httpRequest(`/api/tasks/${encoded(id)}/access-requests${queryString({ page, size })}`) },
  decideAccess(id, requestId, action, reason, key) { return httpRequest(`/api/tasks/${encoded(id)}/access-requests/${encoded(requestId)}/${action}`, { method: 'POST', json: { reason }, idempotencyKey: key }) },
  setStatus(itemId, data) { return httpRequest(`/api/task-items/${encoded(itemId)}/status`, { method: 'POST', json: data }) },
  discard(itemId, operationId, expectedRevision) { return httpRequest(`/api/task-items/${encoded(itemId)}/discard`, { method: 'POST', json: { operationId, expectedRevision } }) },
  restore(itemId, operationId, expectedRevision) { return httpRequest(`/api/task-items/${encoded(itemId)}/restore`, { method: 'POST', json: { operationId, expectedRevision } }) },
  batchAction(action, items, operationId) { return httpRequest(`/api/task-items/batch/${action}`, { method: 'POST', json: { operationId, items } }) },
  batchStatus(status, items, operationId) { return httpRequest('/api/task-items/batch/status', { method: 'POST', json: { operationId, status, items } }) }
}
