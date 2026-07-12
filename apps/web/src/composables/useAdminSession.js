import { readonly, ref } from 'vue'
import { authApi } from '../lib/authApi.js'
import { configureSessionReplacedHandler, markWebSessionEstablished } from '../lib/httpClient.js'

const user = ref(null)
const initialized = ref(false)
const loading = ref(false)
let initializeRequest = null
let replacementCallback = null

configureSessionReplacedHandler(() => {
  user.value = null
  initialized.value = true
  replacementCallback?.()
})

async function initialize() {
  if (initialized.value) return user.value
  if (initializeRequest) return initializeRequest

  loading.value = true
  initializeRequest = authApi.me()
    .then((value) => {
      user.value = value
      markWebSessionEstablished()
      initialized.value = true
      return value
    })
    .catch((error) => {
      if (error?.status === 401) {
        user.value = null
        initialized.value = true
        return null
      }
      throw error
    })
    .finally(() => {
      loading.value = false
      initializeRequest = null
    })
  return initializeRequest
}

async function login(username, password) {
  loading.value = true
  try {
    user.value = await authApi.login(username, password)
    markWebSessionEstablished()
    initialized.value = true
    return user.value
  } finally {
    loading.value = false
  }
}

async function takeover(token) {
  loading.value = true
  try {
    user.value = await authApi.takeover(token)
    markWebSessionEstablished()
    initialized.value = true
    return user.value
  } finally {
    loading.value = false
  }
}

async function logout() {
  try {
    if (user.value) await authApi.logout()
  } finally {
    user.value = null
    initialized.value = true
  }
}

async function changePassword(currentPassword, newPassword) {
  const result = await authApi.changePassword(currentPassword, newPassword)
  user.value = null
  initialized.value = true
  return result
}

export function useAdminSession() {
  return {
    user: readonly(user), initialized: readonly(initialized), loading: readonly(loading),
    initialize, login, takeover, logout, changePassword
  }
}

export function configureSessionReplacementNavigation(callback) {
  replacementCallback = callback
}

export function resetAdminSessionForTests() {
  user.value = null
  initialized.value = false
  loading.value = false
  initializeRequest = null
  replacementCallback = null
}
