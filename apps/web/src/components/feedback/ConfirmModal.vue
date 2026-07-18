<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
const props = defineProps({ open: Boolean, title: String, message: String, busy: Boolean, confirmText: { type: String, default: '确认' } })
const emit = defineEmits(['confirm', 'cancel'])
const panel = ref(null)
let previousFocus
function cancel() { if (!props.busy) emit('cancel') }
function keydown(event) {
  if (!props.open) return
  if (event.key === 'Escape') cancel()
  if (event.key !== 'Tab') return
  const buttons = [...(panel.value?.querySelectorAll('button:not(:disabled)') || [])]
  if (!buttons.length) return
  const first = buttons[0]
  const last = buttons[buttons.length - 1]
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus() }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
}
async function focusPanel() { await nextTick(); panel.value?.querySelector('button')?.focus() }
watch(() => props.open, open => { document.body.style.overflow = open ? 'hidden' : ''; if (open) { previousFocus = document.activeElement; focusPanel() } else previousFocus?.focus?.() })
onMounted(() => window.addEventListener('keydown', keydown))
onBeforeUnmount(() => { window.removeEventListener('keydown', keydown); document.body.style.overflow = '' })
</script>
<template><Teleport to="body"><div v-if="open" class="confirm-modal-backdrop" @click.self="cancel"><section ref="panel" class="confirm-modal" role="dialog" aria-modal="true" :aria-label="title"><h2>{{ title }}</h2><p>{{ message }}</p><div><button class="auth-secondary" :disabled="busy" @click="cancel">取消</button><button class="auth-primary" :disabled="busy" @click="$emit('confirm')">{{ busy ? '处理中…' : confirmText }}</button></div></section></div></Teleport></template>
