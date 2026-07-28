<script setup>
import { onMounted, ref } from 'vue'
import { userApi } from '../../lib/userApi.js'
import { defaultTaskItemFilters, selectionFilters } from '../../lib/taskItemFilters.js'
import { useNotifications } from '../../composables/useNotifications.js'

const props = defineProps({
  modelValue: { type: Object, default: defaultTaskItemFilters },
  kind: { type: String, default: 'all' },
})
const emit = defineEmits(['update:modelValue', 'change'])
const notifications = useNotifications()
const users = ref([])
const userQuery = ref('')
const searching = ref(false)
const groupOptions = [
  ['ALL', '全部'], ['PENDING', '待录制'], ['SUBMITTED', '已提交'],
  ['FINISHED', '已完成'], ['DISCARDED', '废弃数据'],
]
const resultOptions = [
  ['ALL', '全部'], ['NONE', '无结果'], ['TEXT_ONLY', '仅文本'],
  ['AUDIO_ONLY', '仅音频'], ['TEXT_AND_AUDIO', '文本和音频'],
]

function update(patch) {
  const value = { ...selectionFilters(props.modelValue), ...patch }
  emit('update:modelValue', value)
  emit('change', value)
}
function toggleCollector(id, checked) {
  const ids = new Set(props.modelValue.collectorIds || [])
  checked ? ids.add(id) : ids.delete(id)
  update({ collectorIds: [...ids] })
}
async function searchUsers() {
  searching.value = true
  try {
    const response = await userApi.search({
      query: userQuery.value, role: 'COLLECTOR', userType: 'MINIPROGRAM', page: 0, size: 50,
    })
    users.value = response.content || response.items || []
  } catch (error) {
    notifications.error(error.message)
  } finally {
    searching.value = false
  }
}
onMounted(() => {
  if (['all', 'collector'].includes(props.kind)) searchUsers()
})
</script>

<template>
  <span class="task-item-filters">
    <details v-if="['all', 'status'].includes(kind)" class="table-filter">
      <summary>状态{{ modelValue.group !== 'ALL' ? ' · 已筛选' : '' }}</summary>
      <div class="table-filter__menu">
        <label v-for="[value,label] in groupOptions" :key="value">
          <input type="radio" :checked="modelValue.group === value" @change="update({ group: value })">{{ label }}
        </label>
      </div>
    </details>
    <details v-if="['all', 'collector'].includes(kind)" class="table-filter table-filter--wide">
      <summary>采集员姓名{{ modelValue.collectorIds?.length || modelValue.includeUnassigned ? ` · ${modelValue.collectorIds?.length || 0}人` : '' }}</summary>
      <div class="table-filter__menu">
        <form class="filter-search" novalidate @submit.prevent="searchUsers">
          <input v-model.trim="userQuery" placeholder="姓名、用户 ID 或账号">
          <button class="button-link" :disabled="searching">{{ searching ? '搜索中' : '搜索' }}</button>
        </form>
        <label><input type="checkbox" :checked="modelValue.includeUnassigned" @change="update({ includeUnassigned: $event.target.checked })">未分配采集员</label>
        <label v-for="user in users" :key="user.id">
          <input type="checkbox" :checked="modelValue.collectorIds?.includes(user.id)" @change="toggleCollector(user.id, $event.target.checked)">
          <span>{{ user.name || '未设置姓名' }}<small>{{ user.id }}{{ user.loginName ? ` · ${user.loginName}` : '' }}</small></span>
        </label>
        <button type="button" class="button-link" @click="update({ collectorIds: [], includeUnassigned: false })">清空人员筛选</button>
      </div>
    </details>
    <details v-if="['all', 'result'].includes(kind)" class="table-filter">
      <summary>结果{{ modelValue.result !== 'ALL' ? ' · 已筛选' : '' }}</summary>
      <div class="table-filter__menu">
        <label v-for="[value,label] in resultOptions" :key="value">
          <input type="radio" :checked="modelValue.result === value" @change="update({ result: value })">{{ label }}
        </label>
      </div>
    </details>
  </span>
</template>

<style scoped>
.task-item-filters{display:contents}.table-filter{position:relative}.table-filter summary{list-style:none;color:var(--muted-foreground);font-weight:700;cursor:pointer;white-space:nowrap}.table-filter summary::-webkit-details-marker{display:none}.table-filter summary::after{content:" ▾";color:var(--primary)}.table-filter[open] summary::after{content:" ▴"}.table-filter__menu{position:absolute;z-index:30;top:calc(100% + 10px);left:0;display:grid;gap:9px;width:190px;padding:12px;border:1px solid var(--border);border-radius:calc(var(--radius)*.65);background:var(--card);box-shadow:0 16px 36px color-mix(in srgb,var(--foreground) 14%,transparent)}.table-filter--wide .table-filter__menu{width:330px}.table-filter__menu label{display:flex;align-items:flex-start;gap:8px;color:var(--foreground);font-weight:500}.table-filter__menu small{display:block;margin-top:2px;color:var(--muted-foreground)}.filter-search{display:flex;gap:6px}.filter-search input{min-width:0;width:100%}
</style>
