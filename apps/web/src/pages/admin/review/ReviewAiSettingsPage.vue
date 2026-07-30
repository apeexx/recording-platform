<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import AsyncState from '../../../components/admin/AsyncState.vue'
import PageActions from '../../../components/admin/PageActions.vue'
import BaseSelect from '../../../components/form/BaseSelect.vue'
import ToggleSwitch from '../../../components/form/ToggleSwitch.vue'
import { reviewApi } from '../../../lib/reviewApi.js'
import { useNotifications } from '../../../composables/useNotifications.js'
import { operationId } from '../../../lib/apiUtils.js'

const route = useRoute()
const notifications = useNotifications()
const config = ref(null)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const modelOptions = models => models.map(model => ({ value: model, label: model }))
const stageEntries = [
  {
    key: 'audio',
    title: '音频转文字',
    eyebrow: '原始听音',
    models: modelOptions(['qwen3.5-omni-plus', 'qwen3.5-omni-flash']),
  },
  {
    key: 'text',
    title: '文本结果转写',
    eyebrow: '文本修订',
    models: modelOptions(['qwen3.5-plus', 'qwen3.5-flash']),
  },
]

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
  <section class="admin-page ai-settings-page">
    <PageActions title="AI 辅助审核设置" description="配置仅作用于当前任务；密钥由服务端环境变量管理。">
      <router-link class="button-secondary" :to="`/admin/review/tasks/${route.params.taskId}`">返回审核池</router-link>
      <button class="button-primary" :disabled="saving || !config" @click="save">{{ saving ? '保存中' : '保存设置' }}</button>
    </PageActions>
    <AsyncState :loading="loading" :error="error" :empty="!config" @retry="load">
      <div v-if="config" class="ai-settings-grid">
        <section v-for="entry in stageEntries" :key="entry.key" class="business-card ai-settings-card">
          <div class="ai-card-heading">
            <div>
              <span class="ai-eyebrow">{{ entry.eyebrow }}</span>
              <h3>{{ entry.title }}</h3>
              <p>AI 只生成候选文本，不会自动通过或修改原始采集结果。</p>
            </div>
            <ToggleSwitch
              v-model="config[entry.key].enabled"
              :label="config[entry.key].enabled ? '已启用' : '未启用'"
              :disabled="saving"
            />
          </div>

          <div class="ai-section-title">
            <strong>基础设置</strong>
            <span>模型和生成参数仅作用于当前任务</span>
          </div>

          <label class="ai-field-card ai-field-wide">
            <span>模型</span>
            <BaseSelect
              v-model="config[entry.key].model"
              :options="entry.models"
              :disabled="saving"
              :aria-label="`${entry.title}模型`"
            />
          </label>

          <div class="ai-parameter-grid">
            <label class="ai-field-card">
              <span>temperature</span>
              <input v-model.number="config[entry.key].temperature" type="number" min="0" max="1.99" step="0.1" :disabled="saving">
              <small>范围 0–1.99</small>
            </label>
            <label class="ai-field-card">
              <span>topP</span>
              <input v-model.number="config[entry.key].topP" type="number" min="0.01" max="1" step="0.1" :disabled="saving">
              <small>范围 0.01–1</small>
            </label>
            <label class="ai-field-card">
              <span>maxTokens</span>
              <input v-model.number="config[entry.key].maxTokens" type="number" min="1" max="8192" :disabled="saving">
              <small>范围 1–8192</small>
            </label>
            <label class="ai-field-card">
              <span>timeoutMs</span>
              <input v-model.number="config[entry.key].timeoutMs" type="number" min="5000" max="600000" step="1000" :disabled="saving">
              <small>范围 5000–600000 毫秒</small>
            </label>
          </div>

          <label class="ai-field-card ai-prompt-field">
            <span class="ai-prompt-label"><b>Prompt</b><small class="ai-prompt-count">{{ config[entry.key].prompt.length }} / 20000</small></span>
            <textarea v-model="config[entry.key].prompt" rows="9" maxlength="20000" :disabled="saving"/>
            <small>描述输出格式、纠错边界和禁止补充的信息。</small>
          </label>
        </section>
      </div>
    </AsyncState>
  </section>
</template>

<style scoped>
.ai-settings-page{background:radial-gradient(circle at 92% 5%,color-mix(in srgb,var(--primary) 8%,transparent),transparent 28%)}.ai-settings-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:18px;align-items:start}.ai-settings-card{display:grid;gap:18px;min-width:0;padding:24px;background:linear-gradient(155deg,var(--card),color-mix(in srgb,var(--primary) 3%,var(--card)))}.ai-card-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:18px;padding-bottom:18px;border-bottom:1px solid var(--border)}.ai-card-heading .toggle-switch{flex:0 0 auto}.ai-card-heading :deep(.toggle-switch-label){white-space:nowrap}.ai-card-heading h3{margin:6px 0 0;font-size:22px}.ai-card-heading p{max-width:520px;margin:8px 0 0;color:var(--muted-foreground);line-height:1.6}.ai-eyebrow{display:inline-flex;padding:5px 10px;border-radius:999px;background:color-mix(in srgb,var(--primary) 10%,var(--card));color:var(--primary);font-size:12px;font-weight:700}.ai-section-title{display:flex;align-items:center;justify-content:space-between;gap:12px}.ai-section-title span{color:var(--muted-foreground);font-size:12px}.ai-field-card{display:grid;gap:9px;min-width:0;padding:15px;border:1px solid color-mix(in srgb,var(--primary) 13%,var(--border));border-radius:calc(var(--radius)*.75);background:color-mix(in srgb,var(--background) 72%,var(--card));font-size:13px;font-weight:700}.ai-field-card>small{color:var(--muted-foreground);font-size:11px;font-weight:400}.ai-field-card input,.ai-field-card textarea{width:100%;box-sizing:border-box;border:1px solid var(--border);border-radius:calc(var(--radius)*.6);background:var(--card);color:var(--foreground);transition:border-color .18s,box-shadow .18s}.ai-field-card input{min-height:46px;padding:0 14px}.ai-field-card textarea{min-height:210px;padding:13px 14px;line-height:1.65;resize:vertical}.ai-field-card input:focus,.ai-field-card textarea:focus{outline:none;border-color:var(--primary);box-shadow:0 0 0 3px color-mix(in srgb,var(--primary) 15%,transparent)}.ai-field-card input:disabled,.ai-field-card textarea:disabled{cursor:not-allowed;opacity:.68}.ai-field-wide{grid-column:1/-1}.ai-parameter-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.ai-prompt-field{grid-column:1/-1}.ai-prompt-label{display:flex;align-items:center;justify-content:space-between;gap:12px}.ai-prompt-count{color:var(--muted-foreground);font-variant-numeric:tabular-nums;font-weight:500}@media(max-width:1080px){.ai-settings-grid{grid-template-columns:1fr}}@media(max-width:600px){.ai-settings-card{padding:18px}.ai-card-heading{align-items:flex-start;flex-direction:column}.ai-section-title{align-items:flex-start;flex-direction:column}.ai-parameter-grid{grid-template-columns:1fr}}
</style>
