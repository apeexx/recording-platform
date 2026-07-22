<script setup>
const props = defineProps({ modelValue: Boolean, label: { type: String, default: '' }, disabled: Boolean })
const emit = defineEmits(['update:modelValue', 'change'])
function toggle() {
  if (props.disabled) return
  const value = !props.modelValue
  emit('update:modelValue', value)
  emit('change', value)
}
</script>

<template>
  <button type="button" class="toggle-switch" role="switch" :aria-checked="modelValue" :disabled="disabled" @click="toggle">
    <span class="toggle-switch-track"><span class="toggle-switch-thumb"></span></span>
    <span v-if="label" class="toggle-switch-label">{{ label }}</span>
  </button>
</template>
