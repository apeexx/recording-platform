import { httpRequest } from './httpClient.js'
import { queryString } from './apiUtils.js'

export const platformApi = {
  list(page = 0, size = 20) { return httpRequest(`/api/platforms${queryString({ page, size })}`) },
  get(id) { return httpRequest(`/api/platforms/${encodeURIComponent(id)}`) },
  create(data, key) { return httpRequest('/api/platforms', { method: 'POST', json: data, idempotencyKey: key }) },
  update(id, data, key) { return httpRequest(`/api/platforms/${encodeURIComponent(id)}`, { method: 'PUT', json: data, idempotencyKey: key }) },
  remove(id, key) { return httpRequest(`/api/platforms/${encodeURIComponent(id)}`, { method: 'DELETE', idempotencyKey: key }) }
}
