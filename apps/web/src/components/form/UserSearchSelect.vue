<script setup>
import { onMounted, ref, watch } from 'vue'
import { userApi } from '../../lib/userApi.js'
import { useNotifications } from '../../composables/useNotifications.js'

const props = defineProps({
  modelValue: { type: String, default: '' },
  role: { type: String, required: true },
  userType: { type: String, required: true },
  placeholder: { type: String, default: '搜索并选择用户' },
})
const emit = defineEmits(['update:modelValue'])
const notifications = useNotifications()
const query = ref(props.modelValue)
const options = ref([])
const loading = ref(false)

async function search() {
  loading.value = true
  try {
    const response = await userApi.search({
      query: query.value, role: props.role, userType: props.userType, page: 0, size: 50,
    })
    options.value = response.content || response.items || []
  } catch (error) {
    notifications.error(error.message || '用户列表加载失败')
  } finally {
    loading.value = false
  }
}
function select(event) { emit('update:modelValue', event.target.value) }
watch(() => props.role, search)
onMounted(search)
</script>

<template>
  <span class="user-search-select">
    <input v-model.trim="query" :placeholder="placeholder" @keyup.enter.prevent="search" />
    <button type="button" class="button-secondary" :disabled="loading" @click="search">
      {{ loading ? '搜索中' : '搜索' }}
    </button>
    <select :value="modelValue" :aria-label="placeholder" @change="select">
      <option value="">请选择</option>
      <option v-for="user in options" :key="user.id" :value="user.id">
        {{ user.name || '未设置姓名' }} · {{ user.id }}{{ user.loginName ? ` · ${user.loginName}` : '' }}
      </option>
    </select>
  </span>
</template>

<style scoped>
.user-search-select{display:flex;align-items:center;gap:8px;flex-wrap:wrap}.user-search-select input{min-width:190px}.user-search-select select{min-width:260px}
</style>
