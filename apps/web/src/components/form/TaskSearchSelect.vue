<script setup>
import { computed, onMounted, ref } from 'vue'
import { taskApi } from '../../lib/taskApi.js'
import { useNotifications } from '../../composables/useNotifications.js'

const props = defineProps({
  modelValue: { type: String, default: '' },
  allowEmpty: { type: Boolean, default: false },
  placeholder: { type: String, default: '搜索任务编号或名称' },
})
const emit = defineEmits(['update:modelValue'])
const notifications = useNotifications()
const query = ref('')
const tasks = ref([])
const loading = ref(false)
const filteredTasks = computed(() => {
  const keyword = query.value.trim().toLowerCase()
  if (!keyword) return tasks.value
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
  } catch (error) {
    notifications.error(error.message || '任务列表加载失败')
  } finally {
    loading.value = false
  }
}

function select(event) { emit('update:modelValue', event.target.value) }
onMounted(loadTasks)
</script>

<template>
  <span class="task-search-select">
    <input v-model.trim="query" :placeholder="placeholder" />
    <select :value="modelValue" aria-label="任务范围" :disabled="loading" @change="select">
      <option v-if="allowEmpty" value="">全部任务</option>
      <option v-else value="">请选择任务</option>
      <option v-for="task in filteredTasks" :key="task.id" :value="task.id">
        {{ task.taskCode }} · {{ task.name }}
      </option>
    </select>
  </span>
</template>

<style scoped>
.task-search-select{display:flex;align-items:center;gap:8px;flex-wrap:wrap}.task-search-select input{min-width:190px}.task-search-select select{min-width:260px}
</style>
