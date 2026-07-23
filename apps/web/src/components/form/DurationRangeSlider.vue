<script setup>
import { computed } from 'vue'

const props = defineProps({
  minValue: { type: Number, required: true },
  maxValue: { type: Number, required: true },
  min: { type: Number, default: 1 },
  max: { type: Number, default: 600 },
  step: { type: Number, default: 1 },
})
const emit = defineEmits(['update:minValue', 'update:maxValue'])
const minPercent = computed(() => (props.minValue - props.min) / (props.max - props.min) * 100)
const maxPercent = computed(() => (props.maxValue - props.min) / (props.max - props.min) * 100)
const fillStyle = computed(() => ({ left: `${minPercent.value}%`, right: `${100 - maxPercent.value}%` }))
const labelsClose = computed(() => maxPercent.value - minPercent.value < 12)
const minLabelClass = computed(() => ({
  'is-min': true,
  'is-start': minPercent.value <= 3,
  'is-end': minPercent.value >= 97,
  'is-close': labelsClose.value,
}))
const maxLabelClass = computed(() => ({
  'is-max': true,
  'is-start': maxPercent.value <= 3,
  'is-end': maxPercent.value >= 97,
  'is-close': labelsClose.value,
}))
function updateMin(event) {
  emit('update:minValue', Math.min(Number(event.target.value), props.maxValue - props.step))
}
function updateMax(event) {
  emit('update:maxValue', Math.max(Number(event.target.value), props.minValue + props.step))
}
</script>

<template>
  <div class="duration-range">
    <div class="duration-range-values" aria-hidden="true">
      <span class="duration-range-value" :class="minLabelClass" :style="{ left: `${minPercent}%` }">{{ minValue }}</span>
      <span class="duration-range-value" :class="maxLabelClass" :style="{ left: `${maxPercent}%` }">{{ maxValue }}</span>
    </div>
    <div class="duration-range-track"><span class="duration-range-fill" :style="fillStyle"></span></div>
    <input data-handle="min" type="range" :min="min" :max="max" :step="step" :value="minValue" aria-label="最短时长" :aria-valuetext="`${minValue} 秒`" @input="updateMin">
    <input data-handle="max" type="range" :min="min" :max="max" :step="step" :value="maxValue" aria-label="最长时长" :aria-valuetext="`${maxValue} 秒`" @input="updateMax">
  </div>
</template>
