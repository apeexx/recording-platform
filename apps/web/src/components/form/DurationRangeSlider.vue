<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const THUMB_SIZE = 20
const THUMB_RADIUS = THUMB_SIZE / 2
const LABEL_EDGE_PADDING = 2
const LABEL_GAP = 8

const props = defineProps({
  minValue: { type: Number, required: true },
  maxValue: { type: Number, required: true },
  min: { type: Number, default: 1 },
  max: { type: Number, default: 600 },
  step: { type: Number, default: 1 },
})
const emit = defineEmits(['update:minValue', 'update:maxValue'])

const trackRef = ref(null)
const minLabelRef = ref(null)
const maxLabelRef = ref(null)
const trackWidth = ref(0)
const labelWidths = ref({ min: 20, max: 20 })
const dragging = ref(null)
const focusedHandle = ref(null)
let resizeObserver

const clamp = (value, lower, upper) => Math.min(Math.max(value, lower), upper)
const roundToStep = value => Math.round(value / props.step) * props.step
const usableWidth = computed(() => Math.max(1, trackWidth.value - THUMB_SIZE))

function valueToX(value) {
  const ratio = (value - props.min) / Math.max(1, props.max - props.min)
  return THUMB_RADIUS + clamp(ratio, 0, 1) * usableWidth.value
}

function clientXToValue(clientX) {
  const rect = trackRef.value?.getBoundingClientRect()
  if (!rect) return props.min
  const localX = clamp(clientX - rect.left - THUMB_RADIUS, 0, usableWidth.value)
  return clamp(
    roundToStep(props.min + localX / usableWidth.value * (props.max - props.min)),
    props.min,
    props.max,
  )
}

function measure() {
  if (!trackRef.value) return
  trackWidth.value = trackRef.value.getBoundingClientRect().width
  labelWidths.value = {
    min: minLabelRef.value?.offsetWidth || String(props.minValue).length * 8,
    max: maxLabelRef.value?.offsetWidth || String(props.maxValue).length * 8,
  }
}

const minX = computed(() => valueToX(props.minValue))
const maxX = computed(() => valueToX(props.maxValue))
const selectionStyle = computed(() => ({
  left: `${minX.value}px`,
  width: `${Math.max(0, maxX.value - minX.value)}px`,
}))

const labelGeometry = computed(() => {
  const width = trackWidth.value
  const minWidth = labelWidths.value.min
  const maxWidth = labelWidths.value.max
  let minLabelX = clamp(minX.value, minWidth / 2 + LABEL_EDGE_PADDING, width - minWidth / 2 - LABEL_EDGE_PADDING)
  let maxLabelX = clamp(maxX.value, maxWidth / 2 + LABEL_EDGE_PADDING, width - maxWidth / 2 - LABEL_EDGE_PADDING)
  const requiredGap = (minWidth + maxWidth) / 2 + LABEL_GAP

  if (maxLabelX - minLabelX < requiredGap) {
    const center = (minLabelX + maxLabelX) / 2
    minLabelX = clamp(center - requiredGap / 2, minWidth / 2 + LABEL_EDGE_PADDING, width - minWidth / 2 - LABEL_EDGE_PADDING)
    maxLabelX = clamp(center + requiredGap / 2, maxWidth / 2 + LABEL_EDGE_PADDING, width - maxWidth / 2 - LABEL_EDGE_PADDING)
  }

  return {
    minX: minLabelX,
    maxX: maxLabelX,
    staggered: maxLabelX - minLabelX < requiredGap - 1,
  }
})

function setHandleValue(handle, nextValue) {
  if (handle === 'min') {
    emit('update:minValue', clamp(roundToStep(nextValue), props.min, props.maxValue - props.step))
  } else {
    emit('update:maxValue', clamp(roundToStep(nextValue), props.minValue + props.step, props.max))
  }
}

function nearestHandle(value) {
  return Math.abs(value - props.minValue) <= Math.abs(value - props.maxValue) ? 'min' : 'max'
}

function updateFromPointer(clientX) {
  if (!dragging.value) return
  setHandleValue(dragging.value, clientXToValue(clientX))
}

function startPointer(event) {
  if (event.button !== 0) return
  const value = clientXToValue(event.clientX)
  dragging.value = event.target?.dataset?.thumb || nearestHandle(value)
  event.currentTarget?.setPointerCapture?.(event.pointerId)
  updateFromPointer(event.clientX)
}

function movePointer(event) {
  if (!dragging.value) return
  updateFromPointer(event.clientX)
}

function stopPointer(event) {
  if (!dragging.value) return
  if (event.currentTarget?.hasPointerCapture?.(event.pointerId)) {
    event.currentTarget.releasePointerCapture(event.pointerId)
  }
  dragging.value = null
}

function updateMin(event) {
  setHandleValue('min', Number(event.target.value))
}

function updateMax(event) {
  setHandleValue('max', Number(event.target.value))
}

function handleKeydown(handle, event) {
  const direction = {
    ArrowLeft: -props.step,
    ArrowDown: -props.step,
    ArrowRight: props.step,
    ArrowUp: props.step,
  }[event.key]

  if (direction !== undefined) {
    event.preventDefault()
    setHandleValue(handle, props[`${handle}Value`] + direction)
    return
  }

  if (event.key === 'Home') {
    event.preventDefault()
    setHandleValue(handle, handle === 'min' ? props.min : props.minValue + props.step)
  } else if (event.key === 'End') {
    event.preventDefault()
    setHandleValue(handle, handle === 'min' ? props.maxValue - props.step : props.max)
  }
}

watch(
  () => [props.minValue, props.maxValue],
  () => nextTick(measure),
)

onMounted(() => {
  measure()
  if (typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(measure)
    resizeObserver.observe(trackRef.value)
  }
  window.addEventListener('resize', measure)
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  window.removeEventListener('resize', measure)
})
</script>

<template>
  <div class="duration-range">
    <output
      ref="minLabelRef"
      class="duration-range-value is-min"
      :class="{ 'is-staggered': labelGeometry.staggered }"
      :style="{ left: `${labelGeometry.minX}px` }"
      aria-hidden="true"
    >{{ minValue }}</output>
    <output
      ref="maxLabelRef"
      class="duration-range-value is-max"
      :style="{ left: `${labelGeometry.maxX}px` }"
      aria-hidden="true"
    >{{ maxValue }}</output>

    <div
      ref="trackRef"
      class="duration-range-track"
      @pointerdown="startPointer"
      @pointermove="movePointer"
      @pointerup="stopPointer"
      @pointercancel="stopPointer"
    >
      <span class="duration-range-selection" :style="selectionStyle"></span>
      <span
        class="duration-range-thumb is-min"
        :class="{ 'is-dragging': dragging === 'min', 'is-keyboard-focus': focusedHandle === 'min' }"
        :style="{ left: `${minX}px` }"
        data-thumb="min"
        aria-hidden="true"
      ></span>
      <span
        class="duration-range-thumb is-max"
        :class="{ 'is-dragging': dragging === 'max', 'is-keyboard-focus': focusedHandle === 'max' }"
        :style="{ left: `${maxX}px` }"
        data-thumb="max"
        aria-hidden="true"
      ></span>
    </div>

    <input
      class="duration-range-native"
      data-handle="min"
      type="range"
      :min="min"
      :max="maxValue - step"
      :step="step"
      :value="minValue"
      aria-label="最短时长"
      :aria-valuetext="`${minValue} 秒`"
      @input="updateMin"
      @keydown="handleKeydown('min', $event)"
      @focus="focusedHandle = 'min'"
      @blur="focusedHandle = null"
    >
    <input
      class="duration-range-native"
      data-handle="max"
      type="range"
      :min="minValue + step"
      :max="max"
      :step="step"
      :value="maxValue"
      aria-label="最长时长"
      :aria-valuetext="`${maxValue} 秒`"
      @input="updateMax"
      @keydown="handleKeydown('max', $event)"
      @focus="focusedHandle = 'max'"
      @blur="focusedHandle = null"
    >
  </div>
</template>
