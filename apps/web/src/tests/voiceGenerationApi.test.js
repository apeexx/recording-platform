import assert from 'node:assert/strict'
import { describe, it } from 'node:test'

import { buildSynthesisPayload } from '../lib/voiceGenerationApi.js'

describe('voiceGenerationApi', () => {
  it('builds synthesis payload without API key fields', () => {
    const payload = buildSynthesisPayload({
      voiceId: 'sichuan-voice-01',
      text: '测试一句四川话',
      speed: '0.9',
      volume: '1.2',
      pitch: '0'
    })

    assert.deepEqual(payload, {
      voiceId: 'sichuan-voice-01',
      text: '测试一句四川话',
      speed: 0.9,
      volume: 1.2,
      pitch: 0
    })
    assert.equal('apiKey' in payload, false)
    assert.equal('authorization' in payload, false)
  })
})
