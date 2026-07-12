<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  sidebarForRole,
  findAdminSidebarGroupKeyByPath
} from '../../config/adminSidebar.js'
import AdminSidebarGroup from './AdminSidebarGroup.vue'
import { useAdminSession } from '../../composables/useAdminSession.js'

const route = useRoute()
const session = useAdminSession()
const sidebarItems = computed(() => sidebarForRole(session.user.value?.role))
const openGroupKeys = ref(new Set())

function hasChildren(item) {
  return Array.isArray(item.children) && item.children.length > 0
}

function isGroupActive(item) {
  if (!hasChildren(item)) {
    return route.path === item.path
  }

  return item.children.some((child) => route.path === child.path)
}

function isGroupOpen(item) {
  return openGroupKeys.value.has(item.key)
}

function addRouteGroupKey(path) {
  const groupKey = findAdminSidebarGroupKeyByPath(path)

  if (!groupKey || openGroupKeys.value.has(groupKey)) {
    return
  }

  const next = new Set(openGroupKeys.value)
  next.add(groupKey)
  openGroupKeys.value = next
}

function toggleGroup(key) {
  const next = new Set(openGroupKeys.value)

  if (next.has(key)) {
    next.delete(key)
  } else {
    next.add(key)
  }

  openGroupKeys.value = next
}

watch(() => route.path, addRouteGroupKey, { immediate: true })
</script>

<template>
  <aside class="admin-sidebar" aria-label="管理员端侧边栏">
    <div class="admin-sidebar__brand">
      <span class="admin-sidebar__brand-mark">录</span>
      <div>
        <strong>录音任务平台</strong>
        <span>管理端</span>
      </div>
    </div>

    <nav class="admin-sidebar__nav" aria-label="管理员菜单">
      <AdminSidebarGroup
        v-for="item in sidebarItems"
        :key="item.key"
        :item="item"
        :is-open="isGroupOpen(item)"
        :is-active="isGroupActive(item)"
        @toggle="toggleGroup"
      />
    </nav>
  </aside>
</template>
