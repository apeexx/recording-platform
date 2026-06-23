<script setup>
import AdminHeader from '../components/admin/AdminHeader.vue'
import AdminSidebar from '../components/admin/AdminSidebar.vue'
</script>

<template>
  <div class="admin-shell">
    <AdminSidebar />
    <div class="admin-main">
      <AdminHeader />
      <section class="admin-content">
        <router-view />
      </section>
    </div>
  </div>
</template>
