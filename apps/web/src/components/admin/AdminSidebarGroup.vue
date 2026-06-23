<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AdminSidebarItem from './AdminSidebarItem.vue'

const props = defineProps({
  item: {
    type: Object,
    required: true
  }
})

const route = useRoute()

const hasChildren = computed(() => Array.isArray(props.item.children))
const isActiveGroup = computed(() => {
  if (!hasChildren.value) {
    return route.path === props.item.path
  }

  return props.item.children.some((child) => route.path === child.path)
})
</script>

<template>
  <div class="admin-sidebar-group" :class="{ 'is-active': isActiveGroup }">
    <AdminSidebarItem v-if="!hasChildren" :item="item" />

    <template v-else>
      <div class="admin-sidebar-group__title">
        <span class="admin-sidebar-item__icon" aria-hidden="true">{{ item.title.slice(0, 1) }}</span>
        <span>{{ item.title }}</span>
      </div>
      <div class="admin-sidebar-group__children">
        <AdminSidebarItem
          v-for="child in item.children"
          :key="child.key"
          :item="child"
          child
        />
      </div>
    </template>
  </div>
</template>
