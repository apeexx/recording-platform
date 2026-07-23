<script setup>
import { reactive, watch } from 'vue'

const props = defineProps({
  open: Boolean,
  item: { type: Object, default: null },
  referenceTypes: { type: Array, default: () => [] },
  busy: Boolean,
})
const emit = defineEmits(['close', 'save'])
const form = reactive({ referenceText: '', referenceAudioUrl: '', referenceVideoUrl: '' })

watch(() => props.item, (item) => {
  form.referenceText = item?.referenceText || ''
  form.referenceAudioUrl = item?.referenceAudioUrl || ''
  form.referenceVideoUrl = item?.referenceVideoUrl || ''
}, { immediate: true })
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="modal-backdrop" role="presentation" @click.self="emit('close')">
      <section class="business-card item-edit-modal" role="dialog" aria-modal="true" aria-labelledby="item-edit-title">
        <div class="business-heading">
          <div>
            <h3 id="item-edit-title">编辑待领取数据</h3>
            <p>{{ item?.itemCode }} · 编号与序号不可修改</p>
          </div>
          <button type="button" class="button-link" aria-label="关闭" @click="emit('close')">关闭</button>
        </div>
        <form class="business-form" novalidate @submit.prevent="emit('save', { ...form })">
          <label v-if="referenceTypes.includes('TEXT')">参考文字
            <textarea v-model.trim="form.referenceText" class="task-reference-textarea" rows="5" />
          </label>
          <label v-if="referenceTypes.includes('AUDIO')">参考音频 URL
            <input v-model.trim="form.referenceAudioUrl" type="url">
          </label>
          <label v-if="referenceTypes.includes('VIDEO')">参考视频 URL
            <input v-model.trim="form.referenceVideoUrl" type="url">
          </label>
          <div class="business-actions">
            <button type="button" class="button-secondary" :disabled="busy" @click="emit('close')">取消</button>
            <button class="button-primary" :disabled="busy">{{ busy ? '保存中…' : '保存修改' }}</button>
          </div>
        </form>
      </section>
    </div>
  </Teleport>
</template>
