<script setup>
import { onMounted, ref, watch } from 'vue'
import { taskApi } from '../../lib/taskApi.js'
import { useNotifications } from '../../composables/useNotifications.js'

const props = defineProps({
  taskId: { type: String, required: true },
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: '搜索并选择已授权采集员' },
})
const emit = defineEmits(['update:modelValue'])
const notifications = useNotifications()
const query = ref('')
const options = ref([])
const loading = ref(false)

async function search() {
  if (!props.taskId) {
    options.value = []
    return
  }
  loading.value = true
  try {
    const response = await taskApi.grants(props.taskId, 0, 50, {
      status: 'ACTIVE', query: query.value,
    })
    options.value = (response.items || []).filter(row =>
      row.status === 'ACTIVE' && row.userStatus === 'ACTIVE'
    )
  } catch (error) {
    notifications.error(error.message || '已授权采集员加载失败')
  } finally {
    loading.value = false
  }
}

function select(event) {
  emit('update:modelValue', event.target.value)
}

watch(() => props.taskId, () => {
  query.value = ''
  search()
})
onMounted(search)
</script>

<template>
  <span class="task-grant-search-select">
    <input v-model.trim="query" :placeholder="placeholder" @keyup.enter.prevent="search" />
    <button type="button" class="button-secondary" :disabled="loading" @click="search">
      {{ loading ? '搜索中' : '搜索' }}
    </button>
    <select :value="modelValue" :aria-label="placeholder" @change="select">
      <option value="">请选择</option>
      <option v-for="grant in options" :key="grant.userId" :value="grant.userId">
        {{ grant.userName || '未设置姓名' }} · {{ grant.userId }}{{ grant.userLoginName ? ` · ${grant.userLoginName}` : '' }}
      </option>
    </select>
  </span>
</template>

<style scoped>
.task-grant-search-select{display:flex;align-items:center;gap:8px;flex-wrap:wrap}.task-grant-search-select input{min-width:190px}.task-grant-search-select select{min-width:260px}
</style>
