<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageActions from '../../../components/admin/PageActions.vue'
import { taskApi } from '../../../lib/taskApi.js'
import { operationId } from '../../../lib/apiUtils.js'
import { useNotifications } from '../../../composables/useNotifications.js'

const route = useRoute(), router = useRouter(), notifications = useNotifications()
const error = ref(''), saving = ref(false)
const form = reactive({ name: '', description: '', referenceTypes: ['TEXT'], resultType: 'AUDIO', humanReviewEnabled: true, recordingFormat: 'WAV', sampleRate: 16000, minDurationSeconds: 1, maxDurationSeconds: 600, rejectionReasons: '空音频,内容不符' })

async function init() {
  if (!route.params.id) return
  try {
    const [task, versions] = await Promise.all([taskApi.get(route.params.id), taskApi.versions(route.params.id)])
    const version = versions.find(row => row.id === task.currentVersionId) || versions.at(-1)
    Object.assign(form, { name: task.name, description: task.description || '', referenceTypes: [...(version?.referenceTypes || ['TEXT'])], resultType: version?.resultType || 'AUDIO', humanReviewEnabled: version?.humanReviewEnabled !== false, recordingFormat: version?.recordingFormat || 'WAV', sampleRate: [...(version?.sampleRates || [16000])][0], minDurationSeconds: (version?.minDurationMillis || 1000) / 1000, maxDurationSeconds: (version?.maxDurationMillis || 600000) / 1000, rejectionReasons: (version?.rejectionReasons || []).join(',') })
  } catch (e) { error.value = e.message }
}
function payload() {
  const version = { referenceTypes: form.referenceTypes, resultType: form.resultType, humanReviewEnabled: form.humanReviewEnabled, rejectionReasons: form.humanReviewEnabled ? form.rejectionReasons.split(',').map(v => v.trim()).filter(Boolean) : [], aiEnabled: false }
  Object.assign(version, { recordingFormat: form.recordingFormat, sampleRates: [Number(form.sampleRate)], channels: 1, minDurationMillis: Number(form.minDurationSeconds) * 1000, maxDurationMillis: Number(form.maxDurationSeconds) * 1000 })
  return { name: form.name, description: form.description, version }
}
async function save() {
  if (!form.referenceTypes.length) { error.value = '至少选择一种参考源'; return }
  saving.value = true; error.value = ''
  const key = operationId(route.params.id ? 'task-update' : 'task-create')
  try {
    if (route.params.id) await taskApi.update(route.params.id, payload(), key)
    else await taskApi.create(payload(), key)
    notifications.success(route.params.id ? '任务版本已保存' : '任务已创建', { dedupeKey: key })
    router.push('/admin/tasks')
  } catch (e) { error.value = e.message; notifications.error(e.message || '保存失败', { dedupeKey: `${key}:error` }) }
  finally { saving.value = false }
}
onMounted(init)
</script>
<template><section class="admin-page"><PageActions :title="route.params.id?'编辑任务版本':'创建任务'" description="任务编号由系统自动生成；所有任务均采集录音，最终成果可选择文本或音频。"/><form class="business-card business-form business-form-wide" @submit.prevent="save"><div class="business-form-grid"><label>任务名称<input v-model.trim="form.name" required/></label><label>最终成果<select v-model="form.resultType"><option value="TEXT">文本（录音＋文本）</option><option value="AUDIO">音频（仅录音）</option></select></label><label>录音格式<select v-model="form.recordingFormat"><option>WAV</option><option>MP3</option></select></label><label>采样率<select v-model.number="form.sampleRate"><option :value="8000">8000Hz</option><option :value="16000">16000Hz</option><option :value="44100">44100Hz</option><option :value="48000">48000Hz</option></select></label><label>最短时长（秒）<input v-model.number="form.minDurationSeconds" type="number" min="0.1" step="0.1"/></label><label>最长时长（秒）<input v-model.number="form.maxDurationSeconds" type="number" min="1"/></label><label class="business-span">说明<textarea v-model="form.description" rows="3"/></label></div><fieldset><legend>参考内容（至少一项）</legend><label v-for="type in ['TEXT','AUDIO','VIDEO']" :key="type" class="business-check"><input v-model="form.referenceTypes" type="checkbox" :value="type"/>{{ type==='TEXT'?'参考文字':type==='AUDIO'?'参考音频':'参考视频' }}</label></fieldset><fieldset><legend>审核</legend><label class="business-check"><input v-model="form.humanReviewEnabled" type="checkbox"/>人工审核</label></fieldset><label v-if="form.humanReviewEnabled">驳回预设原因（逗号分隔）<input v-model="form.rejectionReasons"/></label><p class="business-note">所有任务都需要录音；文本成果任务还需要同时提交文本。首期 AI 功能固定关闭。</p><p v-if="error" class="business-error">{{ error }}</p><div class="business-actions"><router-link class="button-secondary" to="/admin/tasks">取消</router-link><button class="button-primary" :disabled="saving">{{ saving?'保存中…':'保存' }}</button></div></form></section></template>
