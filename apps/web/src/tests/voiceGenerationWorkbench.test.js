import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { describe, it } from 'node:test'

const source = readFileSync(new URL('../pages/admin/voice-generation/VoiceGenerationWorkbenchPage.vue', import.meta.url), 'utf8')

describe('VoiceGenerationWorkbenchPage', () => {
  it('keeps synthesis tuning controls out of paid clone mode', () => {
    assert.match(source, /<aside\s+v-if="activeMode !== 'clone'"/)
    assert.match(source, /v-if="activeMode === 'clone'"[\s\S]*执行付费克隆/)
  })
})
