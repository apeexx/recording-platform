<script setup>
import { onMounted, ref } from 'vue'
import { fetchRecords } from '../../../lib/voiceGenerationApi.js'
import { useNotifications } from '../../../composables/useNotifications.js'
import PaginationControls from '../../../components/admin/PaginationControls.vue'
import HelpPopover from '../../../components/form/HelpPopover.vue'

const notifications = useNotifications()
const loading = ref(false)
const message = ref('生成记录来自后端 MongoDB。')
const records = ref([])
const page = ref(0)
const size = ref(20)
const total = ref(0)

async function loadRecords() {
  loading.value = true
  try {
    const data = await fetchRecords({ page: page.value, size: size.value })
    records.value = data.items || []
    total.value = Number(data.total) || 0
    message.value = `共 ${total.value} 条生成记录`
  } catch (error) {
    notifications.error(error.message)
  } finally {
    loading.value = false
  }
}

async function changePage(value) { page.value = value; await loadRecords() }
async function changeSize(value) { size.value = value; page.value = 0; await loadRecords() }

onMounted(loadRecords)
</script>

<template>
  <div class="admin-page">
    <div class="voice-title-row">
      <div>
        <h2>生成记录 <HelpPopover label="生成记录状态说明" content="记录区分试听、克隆和日常合成；处理中表示后端尚未结束，失败记录保留脱敏错误摘要，成功记录可播放生成音频。" /></h2>
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
      <PaginationControls :page="page" :size="size" :total="total" @change="changePage" @size-change="changeSize" />
    </section>
  </div>
</template>
