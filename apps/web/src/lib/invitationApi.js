import { httpRequest } from './httpClient.js'
import { queryString, operationId } from './apiUtils.js'

export const invitationApi = {
  list(page = 0, size = 20) {
    return httpRequest(`/api/admin/miniprogram-invitations${queryString({ page, size })}`)
  },
  create(data) {
    return httpRequest('/api/admin/miniprogram-invitations', { method: 'POST', json: data })
  },
  disable(invitationId, idempotencyKey = operationId('invitation-disable')) {
    return httpRequest(`/api/admin/miniprogram-invitations/${encodeURIComponent(invitationId)}/disable`, {
      method: 'POST',
      idempotencyKey
    })
  }
}
