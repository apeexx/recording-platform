<script setup>
defineProps({
  title: {
    type: String,
    required: true
  },
  description: {
    type: String,
    required: true
  },
  collaborator: {
    type: Boolean,
    default: false
  }
})
</script>

<template>
  <div class="admin-page">
    <section class="admin-page-card admin-placeholder">
      <span class="status-pill">占位页面，后续开发</span>
      <h2>{{ title }}</h2>
      <p>{{ description }}</p>
      <p v-if="collaborator" class="admin-placeholder__notice">
        该模块预留给合作者开发，当前不实现业务逻辑。
      </p>
    </section>
  </div>
</template>
