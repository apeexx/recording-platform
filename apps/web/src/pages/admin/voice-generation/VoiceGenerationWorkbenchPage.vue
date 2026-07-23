<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  audioUrl,
  cloneVoice,
  fetchRecords,
  fetchVoices,
  previewVoice,
  synthesizeVoice
} from '../../../lib/voiceGenerationApi.js'
import { useNotifications } from '../../../composables/useNotifications.js'

const notifications = useNotifications()
const modes = [
  { key: 'preview', label: '0元试听' },
  { key: 'clone', label: '付费克隆' },
  { key: 'synthesize', label: '日常合成' }
]

const activeMode = ref('preview')
const loading = ref(false)
const statusMessage = ref('请先配置后端 MINIMAX_API_KEY，再执行真实联通生成。')
const voiceAssets = ref([])
const recentRecords = ref([])
const resultRecordId = ref('')
const previewAudio = ref(null)
const cloneAudio = ref(null)

const form = reactive({
  voiceId: 'sichuan_native_01',
  cloneVoiceId: 'sichuan_native_01',
  text: '哎呀，今天外头终于出大太阳咯，天气硬是巴适得很！',
  speed: 0.9,
  volume: 1.0,
  pitch: 0
})

const resultAudioUrl = computed(() => (resultRecordId.value ? audioUrl(resultRecordId.value) : ''))
const submitButtonText = computed(() => {
  if (loading.value) {
    return activeMode.value === 'clone' ? '克隆中...' : '生成中...'
  }
  if (activeMode.value === 'clone') {
    return '执行付费克隆'
  }
  return activeMode.value === 'preview' ? '0元试听生成' : '开始合成'
})

function onPreviewFileChange(event) {
  previewAudio.value = event.target.files?.[0] || null
}

function onCloneFileChange(event) {
  cloneAudio.value = event.target.files?.[0] || null
}

async function refreshDashboard() {
  await Promise.all([loadVoices(), loadRecords()])
}

async function loadVoices() {
  try {
    const data = await fetchVoices({ excludeSystem: true })
    voiceAssets.value = normalizeVoices(data)
  } catch (error) {
    notifications.error(error.message)
  }
}

async function loadRecords() {
  try {
    const data = await fetchRecords({ page: 0, size: 5 })
    recentRecords.value = data.items || []
  } catch (error) {
    notifications.error(error.message)
  }
}

async function submitGeneration() {
  loading.value = true
  const previousStatus = statusMessage.value
  statusMessage.value = '正在调用后端真实接口...'
  try {
    let result
    if (activeMode.value === 'preview') {
      if (!previewAudio.value) {
        throw new Error('请先上传试听参考音频')
      }
      result = await previewVoice({
        audio: previewAudio.value,
        text: form.text,
        speed: form.speed,
        volume: form.volume,
        pitch: form.pitch
      })
    } else if (activeMode.value === 'clone') {
      if (!cloneAudio.value) {
        throw new Error('请先上传付费克隆母带')
      }
      await cloneVoice({ audio: cloneAudio.value, voiceId: form.cloneVoiceId })
      result = { message: '音色克隆已提交' }
    } else {
      result = await synthesizeVoice(form)
    }
    resultRecordId.value = result.recordId || ''
    statusMessage.value = result.message || '操作完成'
    await refreshDashboard()
  } catch (error) {
    statusMessage.value = previousStatus
    notifications.error(error.message)
  } finally {
    loading.value = false
  }
}

function normalizeVoices(data) {
  const cloned = Array.isArray(data.voice_cloning) ? data.voice_cloning : []
  return cloned.slice(0, 5).map((item, index) => {
    if (typeof item === 'string') {
      return { id: item, type: '克隆音色', label: `音色 ${index + 1}` }
    }
    return {
      id: item.voice_id || item.id || `voice-${index + 1}`,
      type: item.type || '克隆音色',
      label: item.name || item.voice_id || `音色 ${index + 1}`
    }
  })
}

onMounted(refreshDashboard)
</script>

<template>
  <div class="admin-page">
    <div class="voice-title-row">
      <div>
        <h2>语音生成工作台</h2>
        <p>连接 Spring Boot 后端与 MiniMax，完成试听、克隆、合成和结果追踪。</p>
      </div>
      <button class="voice-ghost-button" type="button" @click="refreshDashboard">刷新音色</button>
    </div>

    <div class="voice-workbench">
      <section class="voice-panel">
        <div class="voice-mode-tabs" aria-label="语音生成模式">
          <button
            v-for="mode in modes"
            :key="mode.key"
            type="button"
            :class="{ 'is-active': activeMode === mode.key }"
            @click="activeMode = mode.key"
          >
            {{ mode.label }}
          </button>
        </div>

        <div class="voice-status">{{ statusMessage }}</div>

        <div class="voice-grid">
          <div>
            <label v-if="activeMode === 'preview'" class="voice-field">
              上传参考音频
              <div class="voice-dropzone">
                <input type="file" accept="audio/*" @change="onPreviewFileChange" />
                <span>{{ previewAudio ? previewAudio.name : '支持 mp3 / wav / m4a，用于 0 元试听' }}</span>
              </div>
            </label>

            <label v-if="activeMode === 'clone'" class="voice-field">
              上传付费克隆母带
              <div class="voice-dropzone">
                <input type="file" accept="audio/*" @change="onCloneFileChange" />
                <span>{{ cloneAudio ? cloneAudio.name : '确认满意后再执行付费克隆' }}</span>
              </div>
            </label>

            <label v-if="activeMode === 'synthesize'" class="voice-field">
              已付费建库的音色 ID
              <input v-model="form.voiceId" type="text" placeholder="sichuan_native_01" />
            </label>

            <label v-if="activeMode === 'clone'" class="voice-field">
              新音色 ID
              <input v-model="form.cloneVoiceId" type="text" placeholder="sichuan_native_01" />
            </label>

            <p v-if="activeMode === 'clone'" class="voice-muted voice-helper">
              仅上传母带并设置新音色 ID。MiniMax 要求母带为 mp3 / m4a / wav，时长 10 秒到 5 分钟，文件不超过 20MB。
            </p>

            <button
              v-if="activeMode === 'clone'"
              class="voice-button"
              type="button"
              :disabled="loading"
              @click="submitGeneration"
            >
              {{ submitButtonText }}
            </button>

            <label v-if="activeMode !== 'clone'" class="voice-field">
              需要合成的文本
              <textarea v-model="form.text" maxlength="5000" />
              <span>{{ form.text.length }} / 5000</span>
            </label>
          </div>

          <aside v-if="activeMode !== 'clone'">
            <div class="voice-range-row">
              <strong>语速</strong>
              <input v-model="form.speed" type="range" min="0.5" max="2" step="0.1" />
              <span>{{ Number(form.speed).toFixed(1) }}x</span>
            </div>
            <div class="voice-range-row">
              <strong>音量</strong>
              <input v-model="form.volume" type="range" min="0.1" max="5" step="0.1" />
              <span>{{ Number(form.volume).toFixed(1) }}</span>
            </div>
            <div class="voice-range-row">
              <strong>语调</strong>
              <input v-model="form.pitch" type="range" min="-12" max="12" step="1" />
              <span>{{ form.pitch }}</span>
            </div>
            <button class="voice-button" type="button" :disabled="loading" @click="submitGeneration">
              {{ submitButtonText }}
            </button>
          </aside>
        </div>

        <div v-if="resultAudioUrl" class="voice-player">
          <strong>试听结果</strong>
          <audio :src="resultAudioUrl" controls />
          <a class="voice-button" :href="resultAudioUrl" download>下载音频</a>
        </div>
      </section>

      <aside class="voice-side-panel">
        <div class="voice-panel-header">
          <h3>音色资产</h3>
          <router-link to="/admin/voice-generation/config">管理</router-link>
        </div>
        <div class="voice-asset-list">
          <div v-for="voice in voiceAssets" :key="voice.id" class="voice-asset-row">
            <div>
              <strong>{{ voice.label }}</strong>
              <span>{{ voice.id }} · {{ voice.type }}</span>
            </div>
            <span class="status-pill">日常合成</span>
          </div>
          <div v-if="!voiceAssets.length" class="voice-asset-row">
            <span>暂无音色数据，请先配置 API Key 后刷新。</span>
          </div>
        </div>

        <div class="voice-panel-header">
          <h3>生成记录</h3>
          <router-link to="/admin/voice-generation/records">更多</router-link>
        </div>
        <div class="voice-record-list">
          <div v-for="record in recentRecords" :key="record.id" class="voice-record-row">
            <div>
              <strong>{{ record.text || record.voiceId || record.id }}</strong>
              <span>{{ record.mode }} · {{ record.status }}</span>
            </div>
            <a v-if="record.audioUrl" :href="record.audioUrl">播放</a>
          </div>
          <div v-if="!recentRecords.length" class="voice-record-row">
            <span>暂无生成记录。</span>
          </div>
        </div>
      </aside>
    </div>
  </div>
</template>
