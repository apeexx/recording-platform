import { reactive } from 'vue'

const state = reactive({ items: [] })
let nextId = 1

function notify(message, type = 'success', options = {}) {
  const normalizedMessage = String(message || '操作失败，请稍后重试')
  const dedupeKey = options.dedupeKey ?? (type === 'error' ? `${type}:${normalizedMessage}` : undefined)
  if (dedupeKey && state.items.some(item => item.dedupeKey === dedupeKey)) return
  const item = { id: nextId++, message: normalizedMessage, type, dedupeKey }
  state.items.push(item)
  const defaultDuration = type === 'error' ? 4500 : 2600
  setTimeout(() => dismiss(item.id), options.duration ?? defaultDuration)
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
