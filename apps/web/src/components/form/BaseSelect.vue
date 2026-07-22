<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

const props = defineProps({
  modelValue: { default: '' },
  options: { type: Array, default: () => [] },
  placeholder: { type: String, default: '请选择' },
  disabled: Boolean,
  ariaLabel: { type: String, default: '选择项' },
})
const emit = defineEmits(['update:modelValue', 'change'])
const root = ref(null)
const open = ref(false)
const activeIndex = ref(0)
const selected = computed(() => props.options.find(option => option.value === props.modelValue))

function openMenu() {
  if (props.disabled) return
  activeIndex.value = Math.max(props.options.findIndex(option => option.value === props.modelValue), 0)
  open.value = true
}
function toggle() { open.value ? (open.value = false) : openMenu() }
function choose(option) {
  if (!option || option.disabled) return
  emit('update:modelValue', option.value)
  emit('change', option.value)
  open.value = false
}
function keydown(event) {
  if (props.disabled) return
  if (event.key === 'Escape') { open.value = false; return }
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    if (!open.value) openMenu()
    else choose(props.options[activeIndex.value])
    return
  }
  if (!['ArrowDown', 'ArrowUp'].includes(event.key)) return
  event.preventDefault()
  if (!open.value) { openMenu(); return }
  const direction = event.key === 'ArrowDown' ? 1 : -1
  activeIndex.value = (activeIndex.value + direction + props.options.length) % props.options.length
}
function outside(event) {
  if (root.value && !root.value.contains(event.target)) open.value = false
}
onMounted(() => document.addEventListener('pointerdown', outside))
onBeforeUnmount(() => document.removeEventListener('pointerdown', outside))
</script>

<template>
  <div ref="root" class="base-select" :class="{ 'is-open': open, 'is-disabled': disabled }">
    <button
      type="button"
      class="base-select-trigger"
      role="combobox"
      :aria-label="ariaLabel"
      :aria-expanded="open"
      :disabled="disabled"
      @click="toggle"
      @keydown="keydown"
    >
      <span :class="{ 'is-placeholder': !selected }">{{ selected?.label || placeholder }}</span>
      <span class="base-select-chevron" aria-hidden="true"></span>
    </button>
    <div v-if="open" class="base-select-menu" role="listbox">
      <button
        v-for="(option, index) in options"
        :key="String(option.value)"
        type="button"
        role="option"
        class="base-select-option"
        :class="{ 'is-active': index === activeIndex, 'is-selected': option.value === modelValue }"
        :aria-selected="option.value === modelValue"
        :disabled="option.disabled"
        @pointerenter="activeIndex = index"
        @click="choose(option)"
      >{{ option.label }}</button>
    </div>
  </div>
</template>
