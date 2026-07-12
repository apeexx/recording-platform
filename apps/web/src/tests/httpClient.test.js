import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  ApiError,
  configureSessionReplacedHandler,
  httpRequest,
  markWebSessionEstablished,
  resetHttpClientForTests
} from '../lib/httpClient.js'

function response(status, body, contentType = 'application/json') {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: (name) => (name.toLowerCase() === 'content-type' ? contentType : null) },
    text: vi.fn().mockResolvedValue(body == null ? '' : JSON.stringify(body))
  }
}

describe('httpClient', () => {
  beforeEach(() => {
    resetHttpClientForTests()
    global.fetch = vi.fn()
  })

  it('uses cookies and obtains csrf before a web mutation', async () => {
    fetch
      .mockResolvedValueOnce(response(200, { headerName: 'X-XSRF-TOKEN', token: 'csrf-token' }))
      .mockResolvedValueOnce(response(200, { success: true }))

    await httpRequest('/api/tasks', {
      method: 'POST',
      json: { name: '任务' },
      idempotencyKey: 'operation-1'
    })

    expect(fetch).toHaveBeenNthCalledWith(1, '/api/auth/web/csrf', expect.objectContaining({
      credentials: 'include'
    }))
    expect(fetch).toHaveBeenNthCalledWith(2, '/api/tasks', expect.objectContaining({
      credentials: 'include',
      headers: expect.objectContaining({
        'Content-Type': 'application/json',
        'Idempotency-Key': 'operation-1',
        'X-XSRF-TOKEN': 'csrf-token'
      }),
      body: JSON.stringify({ name: '任务' })
    }))
  })

  it('does not set multipart content type and preserves structured api errors', async () => {
    fetch
      .mockResolvedValueOnce(response(200, { headerName: 'X-XSRF-TOKEN', token: 'csrf-token' }))
      .mockResolvedValueOnce(response(422, {
        code: 'INVALID_AUDIO_DURATION', message: '录音时长不合法', requestId: 'request-1', details: { min: 1000 }
      }))
    const form = new FormData()
    form.append('text', '内容')

    await expect(httpRequest('/api/task-items/one/submit', { method: 'POST', body: form }))
      .rejects.toEqual(expect.objectContaining({
        code: 'INVALID_AUDIO_DURATION', status: 422, requestId: 'request-1', details: { min: 1000 }
      }))
    expect(fetch.mock.calls[1][1].headers['Content-Type']).toBeUndefined()
  })

  it('handles non-json failures and notifies session replacement only once', async () => {
    const replaced = vi.fn()
    configureSessionReplacedHandler(replaced)
    fetch
      .mockResolvedValueOnce(response(401, { code: 'SESSION_REPLACED', message: '账号已在其他设备登录' }))
      .mockResolvedValueOnce(response(401, { code: 'SESSION_REPLACED', message: '账号已在其他设备登录' }))

    await expect(httpRequest('/api/auth/web/me')).rejects.toBeInstanceOf(ApiError)
    await expect(httpRequest('/api/auth/web/me')).rejects.toBeInstanceOf(ApiError)
    expect(replaced).toHaveBeenCalledTimes(1)

    markWebSessionEstablished()
    fetch.mockResolvedValueOnce(response(401, { code: 'SESSION_REPLACED', message: '账号再次被接管' }))
    await expect(httpRequest('/api/auth/web/me')).rejects.toBeInstanceOf(ApiError)
    expect(replaced).toHaveBeenCalledTimes(2)

    fetch.mockResolvedValueOnce(response(503, 'gateway down', 'text/plain'))
    await expect(httpRequest('/api/health')).rejects.toMatchObject({
      code: 'HTTP_503', status: 503
    })
  })
})
