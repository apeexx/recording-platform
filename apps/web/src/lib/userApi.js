import { httpRequest } from './httpClient.js'
import { queryString } from './apiUtils.js'
export const userApi = {
  list(page = 0, size = 20) { return httpRequest(`/api/admin/users${queryString({ page, size })}`) },
  search(params = {}) { return httpRequest(`/api/admin/users/search${queryString(params)}`) },
  create(data) { return httpRequest('/api/admin/users', { method: 'POST', json: data }) },
  disable(userId) { return httpRequest(`/api/admin/users/${encodeURIComponent(userId)}/disable`, { method: 'POST' }) },
  resetPassword(userId, newPassword) { return httpRequest(`/api/admin/users/${encodeURIComponent(userId)}/reset-password`, { method: 'POST', json: { newPassword } }) }
}
