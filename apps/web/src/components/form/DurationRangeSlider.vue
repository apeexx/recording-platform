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
function updateMin(event) {
  emit('update:minValue', Math.min(Number(event.target.value), props.maxValue - props.step))
}
function updateMax(event) {
  emit('update:maxValue', Math.max(Number(event.target.value), props.minValue + props.step))
}
</script>

<template>
  <div class="duration-range">
    <div class="duration-range-values"><span>{{ minValue }} 秒</span><span>{{ maxValue }} 秒</span></div>
    <div class="duration-range-track"><span class="duration-range-fill" :style="fillStyle"></span></div>
    <input data-handle="min" type="range" :min="min" :max="max" :step="step" :value="minValue" aria-label="最短时长" @input="updateMin">
    <input data-handle="max" type="range" :min="min" :max="max" :step="step" :value="maxValue" aria-label="最长时长" @input="updateMax">
  </div>
</template>
