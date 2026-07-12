import { httpRequest } from './httpClient.js'

const API_BASE = '/api/voice-generation'

export function buildSynthesisPayload(form) {
  return {
    voiceId: form.voiceId,
    text: form.text,
    speed: Number(form.speed),
    volume: Number(form.volume),
    pitch: Number(form.pitch)
  }
}

export async function synthesizeVoice(form) {
  return httpRequest(`${API_BASE}/synthesize`, {
    method: 'POST',
    json: buildSynthesisPayload(form)
  })
}

export async function previewVoice({ audio, text, speed, volume, pitch }) {
  const formData = new FormData()
  formData.append('audio', audio)
  formData.append('text', text)
  formData.append('speed', String(speed))
  formData.append('volume', String(volume))
  formData.append('pitch', String(pitch))
  return httpRequest(`${API_BASE}/preview`, {
    method: 'POST',
    body: formData
  })
}

export async function cloneVoice({ audio, voiceId }) {
  const formData = new FormData()
  formData.append('audio', audio)
  formData.append('voiceId', voiceId)
  return httpRequest(`${API_BASE}/voices/clone`, {
    method: 'POST',
    body: formData
  })
}

export async function fetchVoices({ excludeSystem = true } = {}) {
  return httpRequest(`${API_BASE}/voices?excludeSystem=${excludeSystem}`)
}

export async function deleteVoice(voiceId) {
  return httpRequest(`${API_BASE}/voices/${encodeURIComponent(voiceId)}`, {
    method: 'DELETE'
  })
}

export async function fetchRecords({ page = 0, size = 20 } = {}) {
  return httpRequest(`${API_BASE}/records?page=${page}&size=${size}`)
}

export async function fetchDefaultVoiceConfig() {
  return httpRequest(`${API_BASE}/config/default`)
}

export async function saveDefaultVoiceConfig(config) {
  return httpRequest(`${API_BASE}/config/default`, {
    method: 'PUT',
    json: {
      voiceId: config.voiceId,
      speed: Number(config.speed),
      volume: Number(config.volume),
      pitch: Number(config.pitch)
    }
  })
}

export function audioUrl(recordId) {
  return `${API_BASE}/audio/${encodeURIComponent(recordId)}`
}
