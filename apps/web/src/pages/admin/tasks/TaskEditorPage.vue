<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageActions from '../../../components/admin/PageActions.vue'
import BaseSelect from '../../../components/form/BaseSelect.vue'
import DurationRangeSlider from '../../../components/form/DurationRangeSlider.vue'
import ToggleSwitch from '../../../components/form/ToggleSwitch.vue'
import { taskApi } from '../../../lib/taskApi.js'
import { operationId } from '../../../lib/apiUtils.js'
import { useNotifications } from '../../../composables/useNotifications.js'

const route = useRoute(), router = useRouter(), notifications = useNotifications()
const error = ref(''), saving = ref(false)
const form = reactive({ name: '', description: '', referenceTypes: ['TEXT'], resultType: 'TEXT', humanReviewEnabled: true, recordingFormat: 'WAV', sampleRate: 16000, minDurationSeconds: 1, maxDurationSeconds: 600, rejectionReasons: '空音频,内容不符' })
const resultOptions = [{ value: 'TEXT', label: '文本或录音（可同时提交）' }, { value: 'AUDIO', label: '仅录音' }]
const formatOptions = [{ value: 'WAV', label: 'WAV' }, { value: 'MP3', label: 'MP3' }]
const sampleRateOptions = [8000, 16000, 44100, 48000].map(value => ({ value, label: `${value}Hz` }))

async function init() {
  if (!route.params.id) return
  try {
    const task = await taskApi.get(route.params.id)
    if (task.lifecycle !== 'DRAFT') {
      notifications.error('任务发布后不可修改')
      await router.replace(`/admin/tasks/${route.params.id}`)
      return
    }
    const configuration = task.configuration || {}
    Object.assign(form, { name: task.name, description: task.description || '', referenceTypes: [...(configuration.referenceTypes || ['TEXT'])], resultType: configuration.resultType || 'TEXT', humanReviewEnabled: configuration.humanReviewEnabled !== false, recordingFormat: configuration.recordingFormat || 'WAV', sampleRate: [...(configuration.sampleRates || [16000])][0], minDurationSeconds: (configuration.minDurationMillis || 1000) / 1000, maxDurationSeconds: (configuration.maxDurationMillis || 600000) / 1000, rejectionReasons: (configuration.rejectionReasons || []).join(',') })
  } catch (exception) { error.value = exception.message }
}

function payload() {
  return { name: form.name, description: form.description, configuration: { referenceTypes: form.referenceTypes, resultType: form.resultType, humanReviewEnabled: form.humanReviewEnabled, recordingFormat: form.recordingFormat, sampleRates: [Number(form.sampleRate)], channels: 1, minDurationMillis: Number(form.minDurationSeconds) * 1000, maxDurationMillis: Number(form.maxDurationSeconds) * 1000, rejectionReasons: form.humanReviewEnabled ? form.rejectionReasons.split(',').map(value => value.trim()).filter(Boolean) : [], aiEnabled: false } }
}

async function save() {
  if (!form.referenceTypes.length) { error.value = '至少选择一种参考源'; return }
  saving.value = true; error.value = ''
  const key = operationId(route.params.id ? 'task-update' : 'task-create')
  try {
    if (route.params.id) await taskApi.update(route.params.id, payload(), key)
    else await taskApi.create(payload(), key)
    notifications.success(route.params.id ? '草稿任务已保存' : '任务已创建', { dedupeKey: key })
    await router.push('/admin/tasks')
  } catch (exception) { error.value = exception.message; notifications.error(exception.message || '保存失败', { dedupeKey: `${key}:error` }) }
  finally { saving.value = false }
}
onMounted(init)
</script>

<template>
  <section class="admin-page">
    <PageActions :title="route.params.id ? '编辑草稿任务' : '创建任务'" description="任务编号由系统自动生成；任务发布后名称、说明和全部配置永久冻结。" />
    <form class="business-card business-form business-form-wide" @submit.prevent="save">
      <div class="business-form-grid">
        <label>任务名称<input v-model.trim="form.name" required></label>
        <label>最终成果<BaseSelect v-model="form.resultType" :options="resultOptions" aria-label="最终成果" /></label>
        <label>录音格式<BaseSelect v-model="form.recordingFormat" :options="formatOptions" aria-label="录音格式" /></label>
        <label>采样率<BaseSelect v-model="form.sampleRate" :options="sampleRateOptions" aria-label="采样率" /></label>
        <label class="duration-range-field">录音时长范围<DurationRangeSlider v-model:min-value="form.minDurationSeconds" v-model:max-value="form.maxDurationSeconds" :min="1" :max="600" /></label>
        <label class="business-span">说明<textarea v-model="form.description" rows="3" /></label>
      </div>
      <fieldset><legend>参考内容（至少一项）</legend><label v-for="type in ['TEXT','AUDIO','VIDEO']" :key="type" class="business-check colored-checkbox"><input v-model="form.referenceTypes" type="checkbox" :value="type"><span>{{ type === 'TEXT' ? '参考文字' : type === 'AUDIO' ? '参考音频' : '参考视频' }}</span></label></fieldset>
      <fieldset><legend>审核</legend><ToggleSwitch v-model="form.humanReviewEnabled" label="人工审核" /></fieldset>
      <label v-if="form.humanReviewEnabled">驳回预设原因（逗号分隔）<input v-model="form.rejectionReasons"></label>
      <p class="business-note">文本任务可提交文本或录音，也可同时提交；音频任务仅提交录音。首期 AI 功能固定关闭。</p>
      <p v-if="error" class="business-error">{{ error }}</p>
      <div class="business-actions"><router-link class="button-secondary" to="/admin/tasks">取消</router-link><button class="button-primary" :disabled="saving">{{ saving ? '保存中…' : '保存' }}</button></div>
    </form>
  </section>
</template>
