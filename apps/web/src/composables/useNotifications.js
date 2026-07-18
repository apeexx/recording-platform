import { reactive } from 'vue'

const state = reactive({ items: [] })
let nextId = 1

function notify(message, type = 'success', options = {}) {
  const dedupeKey = options.dedupeKey
  if (dedupeKey && state.items.some(item => item.dedupeKey === dedupeKey)) return
  const item = { id: nextId++, message, type, dedupeKey }
  state.items.push(item)
  setTimeout(() => dismiss(item.id), options.duration ?? 2600)
  return item.id
}

function dismiss(id) { const index = state.items.findIndex(item => item.id === id); if (index >= 0) state.items.splice(index, 1) }

export function useNotifications() {
  return {
    items: state.items,
    success: (message, options) => notify(message, 'success', options),
    error: (message, options) => notify(message, 'error', options),
    info: (message, options) => notify(message, 'info', options),
    dismiss
  }
}
