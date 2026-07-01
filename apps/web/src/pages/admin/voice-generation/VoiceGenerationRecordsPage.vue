<script setup>
import { onMounted, ref } from 'vue'
import { fetchRecords } from '../../../lib/voiceGenerationApi.js'

const loading = ref(false)
const message = ref('生成记录来自后端 MongoDB。')
const records = ref([])

async function loadRecords() {
  loading.value = true
  try {
    const data = await fetchRecords({ page: 0, size: 50 })
    records.value = data.items || []
    message.value = `已加载 ${records.value.length} 条生成记录`
  } catch (error) {
    message.value = error.message
    records.value = []
  } finally {
    loading.value = false
  }
}

onMounted(loadRecords)
</script>

<template>
  <div class="admin-page">
    <div class="voice-title-row">
      <div>
        <h2>生成记录</h2>
        <p>查看真实语音生成、试听和克隆记录，音频文件由后端本地目录提供。</p>
      </div>
      <button class="voice-button" type="button" :disabled="loading" @click="loadRecords">刷新记录</button>
    </div>

    <section class="voice-panel">
      <div class="voice-status">{{ message }}</div>
      <table class="voice-record-table">
        <thead>
          <tr>
            <th>文本内容</th>
            <th>音色</th>
            <th>模式</th>
            <th>状态</th>
            <th>创建时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="record in records" :key="record.id">
            <td>{{ record.text || record.message || '-' }}</td>
            <td>{{ record.voiceId || '-' }}</td>
            <td>{{ record.mode }}</td>
            <td>{{ record.status }}</td>
            <td>{{ record.createdAt || '-' }}</td>
            <td>
              <a v-if="record.audioUrl" :href="record.audioUrl">播放</a>
              <span v-else>-</span>
            </td>
          </tr>
          <tr v-if="!records.length">
            <td colspan="6">暂无生成记录。完成一次 0 元试听或日常合成后会显示在这里。</td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>
