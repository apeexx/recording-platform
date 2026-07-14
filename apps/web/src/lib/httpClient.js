let csrfState = null
let csrfRequest = null
let sessionReplacedHandler = null
let sessionReplacementNotified = false

const MUTATING_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE'])

export class ApiError extends Error {
  constructor({ code, message, status, requestId = null, details = null }) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
    this.requestId = requestId
    this.details = details
  }
}

export function configureSessionReplacedHandler(handler) {
  sessionReplacedHandler = typeof handler === 'function' ? handler : null
}

export function markWebSessionEstablished() {
  sessionReplacementNotified = false
  csrfState = null
  csrfRequest = null
}

async function parseResponse(response) {
  const raw = await response.text()
  if (!raw) return null

  const contentType = response.headers.get('content-type') || ''
  if (!contentType.toLowerCase().includes('json')) return raw

  try {
    return JSON.parse(raw)
  } catch {
    return raw
  }
}

function toApiError(response, payload) {
  const structured = payload && typeof payload === 'object' && !Array.isArray(payload)
  return new ApiError({
    code: structured && payload.code ? payload.code : `HTTP_${response.status}`,
    message: structured && payload.message ? payload.message : `请求失败（HTTP ${response.status}）`,
    status: response.status,
    requestId: structured ? payload.requestId ?? null : null,
    details: structured ? payload.details ?? null : null
  })
}

async function obtainCsrf() {
  if (csrfState) return csrfState
  if (!csrfRequest) {
    csrfRequest = httpRequest('/api/auth/web/csrf', { csrf: false })
      .then((result) => {
        if (!result?.headerName || !result?.token) {
          throw new ApiError({
            code: 'CSRF_TOKEN_INVALID',
            message: '服务器未返回有效的安全令牌',
            status: 500
          })
        }
        csrfState = { headerName: result.headerName, token: result.token }
        return csrfState
      })
      .finally(() => {
        csrfRequest = null
      })
  }
  return csrfRequest
}

export async function httpRequest(url, options = {}) {
  const method = (options.method || 'GET').toUpperCase()
  const csrfProtectedMutation = options.csrf !== false && MUTATING_METHODS.has(method)
  const headers = { ...(options.headers || {}) }
  let body = options.body

  if (Object.prototype.hasOwnProperty.call(options, 'json')) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(options.json)
  }
  if (options.idempotencyKey) headers['Idempotency-Key'] = options.idempotencyKey

  if (csrfProtectedMutation) {
    const csrf = await obtainCsrf()
    headers[csrf.headerName] = csrf.token
  }

  let response
  try {
    response = await fetch(url, {
      method,
      credentials: 'include',
      headers,
      body
    })
  } finally {
    if (csrfProtectedMutation) {
      csrfState = null
      csrfRequest = null
    }
  }
  const payload = await parseResponse(response)

  if (!response.ok) {
    const error = toApiError(response, payload)
    if (error.code === 'SESSION_REPLACED' && !sessionReplacementNotified) {
      sessionReplacementNotified = true
      csrfState = null
      sessionReplacedHandler?.(error)
    }
    throw error
  }

  return payload
}

export function resetHttpClientForTests() {
  csrfState = null
  csrfRequest = null
  sessionReplacedHandler = null
  sessionReplacementNotified = false
}
