<script setup>
import { adminSidebar } from '../../config/adminSidebar.js'
import AdminSidebarGroup from './AdminSidebarGroup.vue'
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
      <AdminSidebarGroup v-for="item in adminSidebar" :key="item.key" :item="item" />
    </nav>
  </aside>
</template>
