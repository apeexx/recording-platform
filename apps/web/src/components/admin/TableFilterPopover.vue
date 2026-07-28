<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

const props = defineProps({
  label: { type: String, required: true },
  active: { type: Boolean, default: false },
  width: { type: Number, default: 240 },
})
const trigger = ref(null)
const open = ref(false)
const position = ref({ top: 0, left: 0 })
const panelStyle = computed(() => ({
  top: `${position.value.top}px`,
  left: `${position.value.left}px`,
  width: `${props.width}px`,
}))

function place() {
  if (!open.value || !trigger.value) return
  const rect = trigger.value.getBoundingClientRect()
  const left = Math.max(12, Math.min(rect.left, window.innerWidth - props.width - 12))
  const panelHeight = Math.min(380, window.innerHeight - 24)
  const below = rect.bottom + 8
  const top = below + panelHeight <= window.innerHeight
    ? below
    : Math.max(12, rect.top - panelHeight - 8)
  position.value = { top, left }
}
function toggle() {
  open.value = !open.value
  if (open.value) requestAnimationFrame(place)
}
function close() { open.value = false }
function onPointerDown(event) {
  if (!open.value || trigger.value?.contains(event.target)
    || event.target.closest?.('[data-table-filter-panel]')) return
  close()
}
function onKeydown(event) { if (event.key === 'Escape') close() }

onMounted(() => {
  window.addEventListener('scroll', place, true)
  window.addEventListener('resize', place)
  document.addEventListener('pointerdown', onPointerDown)
  document.addEventListener('keydown', onKeydown)
})
onBeforeUnmount(() => {
  window.removeEventListener('scroll', place, true)
  window.removeEventListener('resize', place)
  document.removeEventListener('pointerdown', onPointerDown)
  document.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <span class="table-filter-trigger">
    <button ref="trigger" type="button" :class="{ 'is-active': active }" :aria-expanded="open" @click="toggle">
      {{ label }}<span aria-hidden="true">▾</span>
    </button>
    <Teleport to="body">
      <div v-if="open" data-table-filter-panel class="table-filter-popover" :style="panelStyle" @click.stop>
        <slot :close="close"/>
      </div>
    </Teleport>
  </span>
</template>

<style scoped>
.table-filter-trigger>button{display:inline-flex;align-items:center;gap:5px;padding:3px 0;background:transparent;color:var(--muted-foreground);font-weight:700;cursor:pointer}.table-filter-trigger>button.is-active{color:var(--primary)}.table-filter-trigger>button:focus-visible{outline:2px solid color-mix(in srgb,var(--primary) 45%,transparent);outline-offset:3px;border-radius:4px}.table-filter-popover{position: fixed;z-index: 2400;display:grid;gap:9px;max-height:min(380px,calc(100vh - 24px));padding:12px;border:1px solid var(--border);border-radius:calc(var(--radius)*.72);overflow:auto;background:var(--card);box-shadow:0 18px 44px color-mix(in srgb,var(--foreground) 18%,transparent)}
</style>
