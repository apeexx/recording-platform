<script setup>
defineProps({ loading: Boolean, error: String, empty: Boolean, emptyText: { type: String, default: '暂无数据' } })
defineEmits(['retry'])
</script>
<template>
  <div v-if="loading" class="business-state" role="status">正在加载…</div>
  <div v-else-if="error" class="business-state is-error" role="alert"><span>{{ error }}</span><button type="button" @click="$emit('retry')">重试</button></div>
  <div v-else-if="empty" class="business-state">{{ emptyText }}</div>
  <slot v-else />
</template>
