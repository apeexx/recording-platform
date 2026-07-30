<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { taskApi } from '../../lib/taskApi.js'
import { useNotifications } from '../../composables/useNotifications.js'

const props = defineProps({
  modelValue: { type: String, default: '' },
  allowEmpty: { type: Boolean, default: false },
  placeholder: { type: String, default: '搜索任务编号或名称' },
})
const emit = defineEmits(['update:modelValue'])
const notifications = useNotifications()
const root = ref(null)
const input = ref(null)
const query = ref('')
const tasks = ref([])
const loading = ref(false)
const open = ref(false)
const activeIndex = ref(0)
const selectedTask = computed(() => tasks.value.find(task => task.id === props.modelValue))
const taskLabel = task => task ? `${task.taskCode} · ${task.name}` : ''
const filteredTasks = computed(() => {
  const keyword = query.value.trim().toLowerCase()
  if (!keyword || keyword === taskLabel(selectedTask.value).toLowerCase()) return tasks.value
  return tasks.value.filter(task =>
    `${task.taskCode || ''} ${task.name || ''}`.toLowerCase().includes(keyword)
  )
})

async function loadTasks() {
  loading.value = true
  try {
    const collected = []
    let page = 0
    let total = 0
    do {
      const response = await taskApi.list(page, 100)
      collected.push(...(response.items || []))
      total = Number(response.total) || collected.length
      page += 1
    } while (collected.length < total && page < 100)
    tasks.value = collected
    query.value = taskLabel(selectedTask.value)
  } catch (error) {
    notifications.error(error.message || '任务列表加载失败')
  } finally {
    loading.value = false
  }
}

function showOptions() {
  if (loading.value) return
  open.value = true
  activeIndex.value = Math.max(filteredTasks.value.findIndex(task => task.id === props.modelValue), 0)
}
async function choose(task) {
  if (!task) return
  query.value = taskLabel(task)
  emit('update:modelValue', task.id)
  open.value = false
  await nextTick()
  input.value?.blur()
}
function keydown(event) {
  if (event.key === 'Escape') {
    open.value = false
    query.value = taskLabel(selectedTask.value)
    return
  }
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault()
    showOptions()
    if (!filteredTasks.value.length) return
    const direction = event.key === 'ArrowDown' ? 1 : -1
    activeIndex.value = (activeIndex.value + direction + filteredTasks.value.length) % filteredTasks.value.length
  }
  if (event.key === 'Enter' && open.value) {
    event.preventDefault()
    choose(filteredTasks.value[activeIndex.value])
  }
}
function outside(event) {
  if (!root.value?.contains(event.target)) {
    open.value = false
    query.value = taskLabel(selectedTask.value)
  }
}
watch(() => props.modelValue, () => { query.value = taskLabel(selectedTask.value) })
onMounted(() => {
  document.addEventListener('pointerdown', outside)
  loadTasks()
})
onBeforeUnmount(() => document.removeEventListener('pointerdown', outside))
</script>

<template>
  <div ref="root" class="task-search-select" :class="{ 'is-open': open }">
    <svg class="task-search-icon" viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="m16 16 4 4"/></svg>
    <input
      ref="input"
      v-model="query"
      role="combobox"
      aria-label="搜索并选择任务"
      :aria-expanded="open"
      :placeholder="loading ? '任务加载中…' : placeholder"
      :disabled="loading"
      @focus="showOptions"
      @input="showOptions(); activeIndex = 0"
      @keydown="keydown"
    />
    <span class="task-search-chevron" aria-hidden="true"></span>
    <div v-if="open" class="task-search-menu" role="listbox">
      <button
        v-if="allowEmpty"
        type="button"
        role="option"
        :class="{ 'is-selected': !modelValue }"
        @click="choose({ id: '', taskCode: '全部任务', name: '' })"
      >全部任务</button>
      <button
        v-for="(task,index) in filteredTasks"
        :key="task.id"
        type="button"
        role="option"
        :class="{ 'is-active': index === activeIndex, 'is-selected': task.id === modelValue }"
        :aria-selected="task.id === modelValue"
        @pointerenter="activeIndex = index"
        @click="choose(task)"
      ><strong class="task-search-code">{{ task.taskCode }}</strong><span class="task-search-name">{{ task.name }}</span></button>
      <p v-if="!filteredTasks.length && !allowEmpty">未找到匹配任务</p>
    </div>
  </div>
</template>
