<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import AsyncState from '../../../components/admin/AsyncState.vue'
import PageActions from '../../../components/admin/PageActions.vue'
import { reviewApi } from '../../../lib/reviewApi.js'
import { useNotifications } from '../../../composables/useNotifications.js'
import { operationId } from '../../../lib/apiUtils.js'

const route = useRoute()
const notifications = useNotifications()
const config = ref(null)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const audioModels = ['qwen3.5-omni-plus', 'qwen3.5-omni-flash']
const textModels = ['qwen3.5-plus', 'qwen3.5-flash']

async function load() {
  loading.value = true
  error.value = ''
  try {
    config.value = await reviewApi.aiConfig(route.params.taskId)
  } catch (exception) {
    error.value = exception.message
  } finally {
    loading.value = false
  }
}

function valid(stage) {
  return stage.prompt.trim().length >= 1
    && stage.prompt.length <= 20000
    && stage.temperature >= 0 && stage.temperature < 2
    && stage.topP > 0 && stage.topP <= 1
    && stage.maxTokens >= 1 && stage.maxTokens <= 8192
    && stage.timeoutMs >= 5000 && stage.timeoutMs <= 600000
}

async function save() {
  if (!valid(config.value.audio) || !valid(config.value.text)) {
    notifications.error('请检查 Prompt 和参数范围')
    return
  }
  saving.value = true
  try {
    config.value = await reviewApi.updateAiConfig(route.params.taskId, {
      audio: config.value.audio,
      text: config.value.text,
    }, operationId('review-ai-config'))
    notifications.success('AI 审核设置已保存')
  } catch (exception) {
    notifications.error(exception.message)
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="admin-page">
    <PageActions title="AI 辅助审核设置" description="配置仅作用于当前任务；密钥由服务端环境变量管理。">
      <router-link class="button-secondary" :to="`/admin/review/tasks/${route.params.taskId}`">返回审核池</router-link>
      <button class="button-primary" :disabled="saving || !config" @click="save">{{ saving ? '保存中' : '保存设置' }}</button>
    </PageActions>
    <AsyncState :loading="loading" :error="error" :empty="!config" @retry="load">
      <div v-if="config" class="ai-settings-grid">
        <section v-for="entry in [
          { key: 'audio', title: '音频转文字', models: audioModels },
          { key: 'text', title: '文本结果转写', models: textModels },
        ]" :key="entry.key" class="business-card ai-settings-card">
          <div class="business-heading">
            <div><h3>{{ entry.title }}</h3><p>AI 只生成候选文本，不会自动通过或修改原始采集结果。</p></div>
            <label class="ai-enable"><input v-model="config[entry.key].enabled" type="checkbox">启用</label>
          </div>
          <label>模型
            <select v-model="config[entry.key].model">
              <option v-for="model in entry.models" :key="model" :value="model">{{ model }}</option>
            </select>
          </label>
          <label>Prompt
            <textarea v-model="config[entry.key].prompt" rows="8" maxlength="20000"/>
          </label>
          <div class="ai-parameter-grid">
            <label>temperature<input v-model.number="config[entry.key].temperature" type="number" min="0" max="1.99" step="0.1"></label>
            <label>topP<input v-model.number="config[entry.key].topP" type="number" min="0.01" max="1" step="0.1"></label>
            <label>maxTokens<input v-model.number="config[entry.key].maxTokens" type="number" min="1" max="8192"></label>
            <label>timeoutMs<input v-model.number="config[entry.key].timeoutMs" type="number" min="5000" max="600000" step="1000"></label>
          </div>
        </section>
      </div>
    </AsyncState>
  </section>
</template>

<style scoped>
.ai-settings-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:18px}.ai-settings-card{display:grid;gap:16px}.ai-enable{display:flex;align-items:center;gap:8px}.ai-parameter-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}@media(max-width:980px){.ai-settings-grid{grid-template-columns:1fr}}@media(max-width:600px){.ai-parameter-grid{grid-template-columns:1fr}}
</style>
