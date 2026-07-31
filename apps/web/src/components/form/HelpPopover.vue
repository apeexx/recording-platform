<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({
  label: { type: String, required: true },
  content: { type: String, required: true },
})

let nextId = 0
const instanceId = `help-popover-${++nextId}`
const trigger = ref(null)
const panel = ref(null)
const hovered = ref(false)
const focused = ref(false)
const pinned = ref(false)
const suppressed = ref(false)
const position = ref({ position: 'fixed', left: '12px', top: '12px' })
const visible = computed(() => !suppressed.value && (hovered.value || focused.value || pinned.value))

function updatePosition() {
  if (!trigger.value) return
  const rect = trigger.value.getBoundingClientRect()
  const panelWidth = panel.value?.offsetWidth || 280
  const panelHeight = panel.value?.offsetHeight || 80
  const gap = 8
  const left = Math.max(12, Math.min(rect.left, window.innerWidth - panelWidth - 12))
  const below = rect.bottom + gap
  const top = below + panelHeight <= window.innerHeight - 12
    ? below
    : Math.max(12, rect.top - panelHeight - gap)
  position.value = { position: 'fixed', left: `${left}px`, top: `${top}px` }
}

function showFromHover() {
  suppressed.value = false
  hovered.value = true
}

function showFromFocus() {
  suppressed.value = false
  focused.value = true
}

function close({ suppress = false } = {}) {
  hovered.value = false
  focused.value = false
  pinned.value = false
  suppressed.value = suppress
}

function togglePinned() {
  if (pinned.value) {
    close({ suppress: true })
    return
  }
  suppressed.value = false
  pinned.value = true
  window.dispatchEvent(new CustomEvent('help-popover-open', { detail: instanceId }))
}

function handleOutside(event) {
  if (!visible.value || trigger.value?.contains(event.target) || panel.value?.contains(event.target)) return
  close({ suppress: true })
}

function handleKeydown(event) {
  if (event.key === 'Escape' && visible.value) {
    close({ suppress: true })
  }
}

function handleAnotherOpen(event) {
  if (event.detail !== instanceId) close({ suppress: true })
}

watch(visible, async isVisible => {
  if (!isVisible) return
  await nextTick()
  updatePosition()
})

onMounted(() => {
  document.addEventListener('pointerdown', handleOutside)
  document.addEventListener('keydown', handleKeydown)
  window.addEventListener('resize', updatePosition)
  window.addEventListener('scroll', updatePosition, true)
  window.addEventListener('help-popover-open', handleAnotherOpen)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', handleOutside)
  document.removeEventListener('keydown', handleKeydown)
  window.removeEventListener('resize', updatePosition)
  window.removeEventListener('scroll', updatePosition, true)
  window.removeEventListener('help-popover-open', handleAnotherOpen)
})
</script>

<template>
  <span class="help-popover">
    <button ref="trigger" class="help-popover-trigger" type="button"
      :aria-label="label" :aria-expanded="visible" :aria-describedby="visible ? instanceId : undefined"
      @pointerenter="showFromHover" @pointerleave="hovered = false"
      @focus="showFromFocus" @blur="focused = false"
      @pointerdown.stop @click.stop="togglePinned">?</button>
    <Teleport to="body">
      <div v-if="visible" :id="instanceId" ref="panel" class="help-popover-panel"
        role="tooltip" :style="position" @pointerdown.stop>
        {{ content }}
      </div>
    </Teleport>
  </span>
</template>

<style scoped>
.help-popover{display:inline-flex;flex:0 0 auto;vertical-align:middle}.help-popover-trigger{display:inline-grid;place-items:center;width:20px;height:20px;padding:0;border:1px solid color-mix(in srgb,var(--primary) 32%,var(--border));border-radius:50%;background:color-mix(in srgb,var(--primary) 7%,var(--card));color:var(--primary);font:700 12px/1 system-ui;cursor:help;user-select:none}.help-popover-trigger:hover,.help-popover-trigger:focus-visible{outline:none;border-color:var(--primary);box-shadow:0 0 0 3px color-mix(in srgb,var(--primary) 14%,transparent)}.help-popover-panel{z-index:10000;box-sizing:border-box;width:min(300px,calc(100vw - 24px));padding:11px 13px;border:1px solid color-mix(in srgb,var(--primary) 24%,var(--border));border-radius:10px;background:var(--card);box-shadow:0 12px 32px rgb(15 23 42 / 16%);color:var(--foreground);font-size:13px;font-weight:400;line-height:1.6;text-align:left;white-space:normal}
</style>
