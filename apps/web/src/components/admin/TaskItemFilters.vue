<script setup>
import { computed, onMounted, ref } from 'vue'
import TableFilterPopover from './TableFilterPopover.vue'
import { taskApi } from '../../lib/taskApi.js'
import { userApi } from '../../lib/userApi.js'
import { reviewApi } from '../../lib/reviewApi.js'
import { selectionFilters } from '../../lib/taskItemFilters.js'
import { useNotifications } from '../../composables/useNotifications.js'

const props = defineProps({
  modelValue: { type: Object, default: () => ({}) },
  kind: { type: String, required: true },
  taskId: { type: String, default: '' },
  reviewMode: { type: Boolean, default: false },
})
const emit = defineEmits(['update:modelValue', 'change'])
const notifications = useNotifications()
const users = ref([])
const userQuery = ref('')
const userSearching = ref(false)
const codeQuery = ref('')
const codeCandidates = ref([])
const codeSearching = ref(false)
const groupOptions = [
  ['PENDING', '待录制'], ['SUBMITTED', '已提交'],
  ['FINISHED', '已完成'], ['DISCARDED', '废弃数据'],
]
const reviewStatusOptions = [['SUBMITTED', '已提交'], ['REVIEW_PENDING', '待审核']]
const resultOptions = [
  ['NONE', '无结果'], ['TEXT_ONLY', '仅文本'],
  ['AUDIO_ONLY', '仅音频'], ['TEXT_AND_AUDIO', '文本和音频'],
]
const normalized = computed(() => props.reviewMode
  ? {
      itemCodes: props.modelValue.itemCodes || [],
      itemCodeQuery: props.modelValue.itemCodeQuery || '',
      statuses: props.modelValue.statuses || [],
      collectorIds: props.modelValue.collectorIds || [],
      includeUnassigned: false,
      reviewerIds: props.modelValue.reviewerIds || [],
      includeUnassignedReviewer: Boolean(props.modelValue.includeUnassignedReviewer),
      results: props.modelValue.results || [],
    }
  : selectionFilters(props.modelValue))
const selectedCodes = computed(() => normalized.value.itemCodes)
const visibleCodes = computed(() => {
  const byCode = new Map()
  selectedCodes.value.forEach((code) => byCode.set(code, { itemCode: code, selectedOnly: true }))
  codeCandidates.value.forEach((row) => byCode.set(row.itemCode, row))
  return [...byCode.values()]
})
const active = computed(() => ({
  code: selectedCodes.value.length > 0,
  status: (props.reviewMode ? normalized.value.statuses : normalized.value.groups).length > 0,
  collector: normalized.value.collectorIds.length > 0 || normalized.value.includeUnassigned,
  reviewer: normalized.value.reviewerIds?.length > 0 || normalized.value.includeUnassignedReviewer,
  result: normalized.value.results.length > 0,
})[props.kind])
const label = computed(() => {
  const base = { code: '编号', status: '状态', collector: '采集员姓名', reviewer: '审核员姓名', result: '结果' }[props.kind] || '筛选'
  const count = {
    code: selectedCodes.value.length,
    status: (props.reviewMode ? normalized.value.statuses : normalized.value.groups).length,
    collector: normalized.value.collectorIds.length + (normalized.value.includeUnassigned ? 1 : 0),
    reviewer: (normalized.value.reviewerIds?.length || 0) + (normalized.value.includeUnassignedReviewer ? 1 : 0),
    result: normalized.value.results.length,
  }[props.kind]
  return count ? `${base} · ${count}` : base
})

function update(patch) {
  const value = { ...normalized.value, ...patch }
  emit('update:modelValue', value)
  emit('change', value)
}
function toggleList(key, value, checked) {
  const values = new Set(normalized.value[key])
  checked ? values.add(value) : values.delete(value)
  update({ [key]: [...values] })
}
function clearDimension() {
  if (props.kind === 'code') update({ itemCodes: [] })
  if (props.kind === 'status') update(props.reviewMode ? { statuses: [] } : { groups: [] })
  if (props.kind === 'collector') update({ collectorIds: [], includeUnassigned: false })
  if (props.kind === 'reviewer') update({ reviewerIds: [], includeUnassignedReviewer: false })
  if (props.kind === 'result') update({ results: [] })
}
async function searchUsers() {
  userSearching.value = true
  try {
    const role = props.kind === 'reviewer' ? 'REVIEWER' : 'COLLECTOR'
    if (props.reviewMode) {
      users.value = await reviewApi.filterUsers(props.taskId, role, userQuery.value)
    } else {
      const response = await userApi.search({
        query: userQuery.value,
        role,
        userType: props.kind === 'reviewer' ? 'WEB' : 'MINIPROGRAM',
        page: 0, size: 50,
      })
      users.value = response.content || response.items || []
    }
  } catch (error) {
    notifications.error(error.message)
  } finally {
    userSearching.value = false
  }
}
async function searchCodes() {
  if (!props.taskId) return
  codeSearching.value = true
  try {
    const response = props.reviewMode
      ? await reviewApi.pool(props.taskId, 0, 20, { itemCodeQuery: codeQuery.value })
      : await taskApi.items(props.taskId, 0, 20, { itemCodeQuery: codeQuery.value })
    codeCandidates.value = response.items || []
  } catch (error) {
    notifications.error(error.message)
  } finally {
    codeSearching.value = false
  }
}
onMounted(() => {
  if (props.kind === 'collector' || props.kind === 'reviewer') searchUsers()
  if (props.kind === 'code') searchCodes()
})
</script>

<template>
  <TableFilterPopover :label="label" :active="active" :width="kind === 'collector' || kind === 'reviewer' ? 340 : 270">
    <form v-if="kind === 'code'" class="filter-search" novalidate @submit.prevent="searchCodes">
      <input v-model.trim="codeQuery" placeholder="搜索条目编号">
      <button class="button-link" :disabled="codeSearching">{{ codeSearching ? '搜索中' : '搜索' }}</button>
    </form>
    <form v-if="kind === 'collector' || kind === 'reviewer'" class="filter-search" novalidate @submit.prevent="searchUsers">
      <input v-model.trim="userQuery" placeholder="姓名、用户 ID 或账号">
      <button class="button-link" :disabled="userSearching">{{ userSearching ? '搜索中' : '搜索' }}</button>
    </form>

    <div class="filter-option-list">
      <label class="filter-option" :class="{ 'is-selected': !active }">
        <input type="checkbox" :checked="!active" @change="clearDimension">
        <span>全部</span>
      </label>
      <template v-if="kind === 'code'">
        <label v-for="row in visibleCodes" :key="row.itemCode" class="filter-option" :class="{ 'is-selected': selectedCodes.includes(row.itemCode) }">
          <input type="checkbox" :checked="selectedCodes.includes(row.itemCode)" @change="toggleList('itemCodes', row.itemCode, $event.target.checked)">
          <span>{{ row.itemCode }}</span>
        </label>
        <p v-if="!visibleCodes.length && !codeSearching" class="filter-empty">没有匹配的编号</p>
      </template>
      <template v-if="kind === 'status'">
        <label v-for="[value, text] in (reviewMode ? reviewStatusOptions : groupOptions)" :key="value" class="filter-option" :class="{ 'is-selected': (reviewMode ? normalized.statuses : normalized.groups).includes(value) }">
          <input type="checkbox" :checked="(reviewMode ? normalized.statuses : normalized.groups).includes(value)" @change="toggleList(reviewMode ? 'statuses' : 'groups', value, $event.target.checked)">
          <span>{{ text }}</span>
        </label>
      </template>
      <template v-if="kind === 'reviewer'">
        <label class="filter-option" :class="{ 'is-selected': normalized.includeUnassignedReviewer }">
          <input type="checkbox" :checked="normalized.includeUnassignedReviewer" @change="update({ includeUnassignedReviewer: $event.target.checked })">
          <span>未分配审核员</span>
        </label>
        <label v-for="user in users" :key="user.id" class="filter-option" :class="{ 'is-selected': normalized.reviewerIds.includes(user.id) }">
          <input type="checkbox" :checked="normalized.reviewerIds.includes(user.id)" @change="toggleList('reviewerIds', user.id, $event.target.checked)">
          <span>{{ user.name || '未设置姓名' }}<small>{{ user.id }}{{ user.loginName ? ` · ${user.loginName}` : '' }}</small></span>
        </label>
        <p v-if="!users.length && !userSearching" class="filter-empty">没有匹配的审核员</p>
      </template>
      <template v-if="kind === 'collector'">
        <label class="filter-option" :class="{ 'is-selected': normalized.includeUnassigned }">
          <input type="checkbox" :checked="normalized.includeUnassigned" @change="update({ includeUnassigned: $event.target.checked })">
          <span>未分配采集员</span>
        </label>
        <label v-for="user in users" :key="user.id" class="filter-option" :class="{ 'is-selected': normalized.collectorIds.includes(user.id) }">
          <input type="checkbox" :checked="normalized.collectorIds.includes(user.id)" @change="toggleList('collectorIds', user.id, $event.target.checked)">
          <span>{{ user.name || '未设置姓名' }}<small>{{ user.id }}{{ user.loginName ? ` · ${user.loginName}` : '' }}</small></span>
        </label>
        <p v-if="!users.length && !userSearching" class="filter-empty">没有匹配的采集员</p>
      </template>
      <template v-if="kind === 'result'">
        <label v-for="[value, text] in resultOptions" :key="value" class="filter-option" :class="{ 'is-selected': normalized.results.includes(value) }">
          <input type="checkbox" :checked="normalized.results.includes(value)" @change="toggleList('results', value, $event.target.checked)">
          <span>{{ text }}</span>
        </label>
      </template>
    </div>
  </TableFilterPopover>
</template>

<style scoped>
.filter-search{display:flex;gap:7px}.filter-search input{min-width:0;width:100%}.filter-option-list{display:grid;gap:4px}.filter-option{display:flex;align-items:flex-start;gap:10px;min-height:36px;padding:8px 10px;border-radius:12px;color:var(--foreground);font-weight:500;cursor:pointer}.filter-option:hover{background:var(--accent)}.filter-option.is-selected{background:color-mix(in srgb,var(--primary) 9%,var(--accent))}.filter-option:focus-within{outline:2px solid color-mix(in srgb,var(--primary) 38%,transparent);outline-offset:1px}.filter-option input{width:18px;height:18px;margin:1px 0 0;accent-color:var(--primary);flex:0 0 auto}.filter-option small{display:block;margin-top:3px;color:var(--muted-foreground);font-size:12px;font-weight:400;overflow-wrap:anywhere}.filter-empty{margin:5px 8px;color:var(--muted-foreground);font-size:13px}
</style>
