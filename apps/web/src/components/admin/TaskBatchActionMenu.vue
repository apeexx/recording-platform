<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'

const props = defineProps({
  selectedCount: { type: Number, default: 0 },
  counts: { type: Object, default: () => ({}) },
  statusOptions: { type: Array, default: () => [] },
  disabled: { type: Boolean, default: false },
})
const emit = defineEmits(['status', 'release', 'discard', 'restore'])
const root = ref(null)
const open = ref(false)
const statusOpen = ref(false)
const targetStatus = ref('')

function choose(action) {
  if (action === 'status') {
    targetStatus.value ||= props.statusOptions[0]?.value || ''
    statusOpen.value = true
  } else emit(action)
  open.value = false
}
function confirmStatus() {
  if (!targetStatus.value) return
  emit('status', targetStatus.value)
  statusOpen.value = false
}
function outside(event) { if (!root.value?.contains(event.target)) open.value = false }
function onKeydown(event) {
  if (event.key !== 'Escape') return
  open.value = false
  statusOpen.value = false
}
onMounted(() => {
  document.addEventListener('pointerdown', outside)
  document.addEventListener('keydown', onKeydown)
})
onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', outside)
  document.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <div ref="root" class="batch-action-menu">
    <button type="button" class="button-secondary" :disabled="disabled || !selectedCount" @click.stop="open = !open">
      调整状态（{{ selectedCount }}）
    </button>
    <div v-if="open" class="batch-action-menu__panel">
      <button type="button" :disabled="!counts.status" @click="choose('status')">变更状态 <small>{{ counts.status || 0 }}</small></button>
      <button type="button" :disabled="!counts.release" @click="choose('release')">批量释放 <small>{{ counts.release || 0 }}</small></button>
      <button type="button" class="is-danger" :disabled="!counts.discard" @click="choose('discard')">批量废弃 <small>{{ counts.discard || 0 }}</small></button>
      <button type="button" :disabled="!counts.restore" @click="choose('restore')">批量恢复 <small>{{ counts.restore || 0 }}</small></button>
    </div>
    <Teleport to="body">
      <div v-if="statusOpen" class="modal-backdrop" @click.self="statusOpen = false">
        <section class="business-card batch-status-dialog" role="dialog" aria-modal="true" aria-label="变更状态">
          <h3>变更状态</h3>
          <label>目标状态<select v-model="targetStatus"><option v-for="option in statusOptions" :key="option.value" :value="option.value">{{ option.label }}</option></select></label>
          <div class="business-actions"><button type="button" class="button-secondary" @click="statusOpen = false">取消</button><button type="button" class="button-primary" :disabled="!targetStatus" @click="confirmStatus">确认变更</button></div>
        </section>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.batch-action-menu{position:relative}.batch-action-menu__panel{position:absolute;z-index:120;top:calc(100% + 8px);right:0;display:grid;width:210px;padding:8px;border:1px solid var(--border);border-radius:calc(var(--radius)*.65);background:var(--card);box-shadow:0 16px 36px color-mix(in srgb,var(--foreground) 14%,transparent)}.batch-action-menu__panel button{display:flex;justify-content:space-between;padding:10px;border-radius:calc(var(--radius)*.45);background:transparent;text-align:left;cursor:pointer}.batch-action-menu__panel button:hover:not(:disabled){background:var(--accent)}.batch-action-menu__panel button:disabled{opacity:.42;cursor:not-allowed}.batch-action-menu__panel small{color:var(--muted-foreground)}.batch-status-dialog{display:grid;gap:16px;width:min(420px,100%)}.batch-status-dialog label{display:grid;gap:7px;font-weight:700}.batch-status-dialog select{min-height:42px;padding:8px 11px;border:1px solid var(--border);border-radius:calc(var(--radius)*.55);background:var(--background);color:var(--foreground)}
</style>
