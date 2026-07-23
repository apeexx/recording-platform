import { httpRequest } from './httpClient.js'

export const authApi = {
  login(username, password) {
    return httpRequest('/api/auth/web/login', {
      method: 'POST', json: { username, password }, csrf: false
    })
  },
  takeover(takeoverToken) {
    return httpRequest('/api/auth/web/takeover', {
      method: 'POST', json: { takeoverToken }, csrf: false
    })
  },
  me() {
    return httpRequest('/api/auth/web/me')
  },
  logout() {
    return httpRequest('/api/auth/web/logout', { method: 'POST' })
  },
  changePassword(currentPassword, newPassword) {
    return httpRequest('/api/auth/web/password', {
      method: 'PUT', json: { currentPassword, newPassword }
    })
  },
  changeInitialPassword(newPassword) {
    return httpRequest('/api/auth/web/initial-password', {
      method: 'PUT', json: { newPassword }
    })
  },
  skipInitialPasswordChange() {
    return httpRequest('/api/auth/web/initial-password/skip', { method: 'POST' })
  }
}
