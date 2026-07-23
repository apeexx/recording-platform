import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../lib/httpClient.js', () => ({
  httpRequest: vi.fn(),
  configureSessionReplacedHandler: vi.fn(),
  markWebSessionEstablished: vi.fn()
}))

import { httpRequest } from '../lib/httpClient.js'
import { authApi } from '../lib/authApi.js'
import { resetAdminSessionForTests, useAdminSession } from '../composables/useAdminSession.js'
import { synthesizeVoice } from '../lib/voiceGenerationApi.js'
import fs from 'node:fs'
import path from 'node:path'

const admin = {
  userId: 'admin-1', username: 'admin', name: '管理员', role: 'ADMIN',
  firstPasswordChangeRequired: false
}

describe('后台身份流程', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    resetAdminSessionForTests()
  })

  it('登录和接管不请求 csrf，并保留接管凭证', async () => {
    httpRequest.mockResolvedValueOnce(admin).mockResolvedValueOnce(admin)

    await authApi.login('admin', 'password')
    await authApi.takeover('takeover-token')

    expect(httpRequest).toHaveBeenNthCalledWith(1, '/api/auth/web/login', {
      method: 'POST', json: { username: 'admin', password: 'password' }, csrf: false
    })
    expect(httpRequest).toHaveBeenNthCalledWith(2, '/api/auth/web/takeover', {
      method: 'POST', json: { takeoverToken: 'takeover-token' }, csrf: false
    })
  })

  it('登录成功保存用户，首次改密后清空会话并要求重新登录', async () => {
    const session = useAdminSession()
    httpRequest.mockResolvedValueOnce({ ...admin, firstPasswordChangeRequired: true })
      .mockResolvedValueOnce({ success: true, reloginRequired: true })

    await session.login('admin', 'password')
    expect(session.user.value.firstPasswordChangeRequired).toBe(true)

    const result = await session.changePassword('password', 'new-password')
    expect(result.reloginRequired).toBe(true)
    expect(session.user.value).toBeNull()
  })

  it('首次改密使用专用无原密码接口，跳过后保留会话并清除标记', async () => {
    const session = useAdminSession()
    httpRequest.mockResolvedValueOnce({ ...admin, firstPasswordChangeRequired: true })
      .mockResolvedValueOnce({ success: true })
      .mockResolvedValueOnce({ success: true, reloginRequired: true })

    await session.login('admin', 'password')
    await session.skipInitialPasswordChange()
    expect(session.user.value.firstPasswordChangeRequired).toBe(false)
    expect(httpRequest).toHaveBeenNthCalledWith(2, '/api/auth/web/initial-password/skip', {
      method: 'POST'
    })

    await session.changeInitialPassword('new-password')
    expect(httpRequest).toHaveBeenNthCalledWith(3, '/api/auth/web/initial-password', {
      method: 'PUT', json: { newPassword: 'new-password' }
    })
    expect(session.user.value).toBeNull()
  })

  it('登录页提供密码可见按钮且首次改密页不再要求原密码', () => {
    const login = fs.readFileSync(path.resolve('src/pages/auth/AdminLoginPage.vue'), 'utf8')
    const initialPassword = fs.readFileSync(path.resolve('src/pages/auth/FirstPasswordPage.vue'), 'utf8')

    expect(login).toContain("'显示密码'")
    expect(login).toContain("'隐藏密码'")
    expect(login).toContain('initialPasswordPrompt')
    expect(initialPassword).not.toContain('currentPassword')
    expect(initialPassword).toContain('session.changeInitialPassword(newPassword.value)')
  })

  it('初始化遇到未登录只记录完成状态，其他错误继续抛出', async () => {
    const session = useAdminSession()
    httpRequest.mockRejectedValueOnce({ status: 401, code: 'UNAUTHENTICATED' })
    await session.initialize()
    expect(session.initialized.value).toBe(true)
    expect(session.user.value).toBeNull()

    resetAdminSessionForTests()
    httpRequest.mockRejectedValueOnce({ status: 503, code: 'DATABASE_UNAVAILABLE' })
    await expect(useAdminSession().initialize()).rejects.toMatchObject({ status: 503 })
  })

  it('语音生成写请求复用带 csrf 的统一请求客户端', async () => {
    httpRequest.mockResolvedValueOnce({ status: 'SUCCEEDED' })
    await synthesizeVoice({ voiceId: 'voice-1', text: '测试', speed: '1', volume: '1', pitch: '0' })
    expect(httpRequest).toHaveBeenCalledWith('/api/voice-generation/synthesize', {
      method: 'POST',
      json: { voiceId: 'voice-1', text: '测试', speed: 1, volume: 1, pitch: 0 }
    })
  })
})
