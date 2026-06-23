<script setup>
import { onMounted, ref } from 'vue'
import {
  deleteVoice,
  fetchDefaultVoiceConfig,
  fetchVoices,
  saveDefaultVoiceConfig
} from '../../../lib/voiceGenerationApi.js'

const loading = ref(false)
const message = ref('声音配置页直接读取后端 MiniMax 音色资产。')
const voices = ref([])
const config = ref({
  voiceId: 'sichuan_native_01',
  speed: 0.9,
  volume: 1.0,
  pitch: 0
})

async function loadDefaultConfig() {
  try {
    const data = await fetchDefaultVoiceConfig()
    config.value = {
      voiceId: data.voiceId || 'sichuan_native_01',
      speed: data.speed ?? 0.9,
      volume: data.volume ?? 1.0,
      pitch: data.pitch ?? 0
    }
  } catch (error) {
    message.value = error.message
  }
}

async function saveConfig() {
  loading.value = true
  try {
    await saveDefaultVoiceConfig(config.value)
    message.value = '默认声音配置已保存'
  } catch (error) {
    message.value = error.message
  } finally {
    loading.value = false
  }
}

async function loadVoices() {
  loading.value = true
  try {
    const data = await fetchVoices({ excludeSystem: true })
    voices.value = normalizeVoices(data)
    message.value = `已刷新 ${voices.value.length} 个专属音色`
  } catch (error) {
    message.value = error.message
    voices.value = []
  } finally {
    loading.value = false
  }
}

async function removeVoice(voiceId) {
  loading.value = true
  try {
    await deleteVoice(voiceId)
    message.value = `已删除音色 ${voiceId}`
    await loadVoices()
  } catch (error) {
    message.value = error.message
  } finally {
    loading.value = false
  }
}

function normalizeVoices(data) {
  const cloned = Array.isArray(data.voice_cloning) ? data.voice_cloning : []
  return cloned.map((item, index) => {
    if (typeof item === 'string') {
      return { id: item, label: item, type: '克隆音色' }
    }
    return {
      id: item.voice_id || item.id || `voice-${index + 1}`,
      label: item.name || item.voice_id || `音色 ${index + 1}`,
      type: item.type || '克隆音色'
    }
  })
}

onMounted(async () => {
  await Promise.all([loadVoices(), loadDefaultConfig()])
})
</script>

<template>
  <div class="admin-page">
    <div class="voice-title-row">
      <div>
        <h2>声音配置</h2>
        <p>刷新、查看和删除 MiniMax 已克隆音色，前端不保存 API Key。</p>
      </div>
      <button class="voice-button" type="button" :disabled="loading" @click="loadVoices">刷新音色</button>
    </div>

    <section class="voice-panel">
      <div class="voice-status">{{ message }}</div>
      <div class="voice-grid">
        <label class="voice-field">
          默认音色 ID
          <input v-model="config.voiceId" type="text" />
        </label>
        <div>
          <div class="voice-range-row">
            <strong>语速</strong>
            <input v-model="config.speed" type="range" min="0.5" max="2" step="0.1" />
            <span>{{ Number(config.speed).toFixed(1) }}</span>
          </div>
          <div class="voice-range-row">
            <strong>音量</strong>
            <input v-model="config.volume" type="range" min="0.1" max="5" step="0.1" />
            <span>{{ Number(config.volume).toFixed(1) }}</span>
          </div>
          <div class="voice-range-row">
            <strong>语调</strong>
            <input v-model="config.pitch" type="range" min="-12" max="12" step="1" />
            <span>{{ config.pitch }}</span>
          </div>
          <button class="voice-button" type="button" :disabled="loading" @click="saveConfig">
            保存默认配置
          </button>
        </div>
      </div>

      <table class="voice-record-table">
        <thead>
          <tr>
            <th>音色名称</th>
            <th>音色 ID</th>
            <th>类型</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="voice in voices" :key="voice.id">
            <td>{{ voice.label }}</td>
            <td>{{ voice.id }}</td>
            <td>{{ voice.type }}</td>
            <td>
              <button class="voice-danger-button" type="button" :disabled="loading" @click="removeVoice(voice.id)">
                删除
              </button>
            </td>
          </tr>
          <tr v-if="!voices.length">
            <td colspan="4">暂无专属音色，请检查后端 API Key 或先在工作台执行付费克隆。</td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>
