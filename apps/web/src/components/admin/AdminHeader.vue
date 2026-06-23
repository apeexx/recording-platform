<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const pageTitle = computed(() => route.meta.title || '工作台')
</script>

<template>
  <header class="admin-header">
    <div>
      <p class="admin-header__eyebrow">管理员端</p>
      <h1>{{ pageTitle }}</h1>
    </div>

    <div class="admin-header__actions" aria-label="管理员工具区">
      <button class="admin-icon-button" type="button" aria-label="通知占位">
        <span aria-hidden="true"></span>
      </button>
      <span class="admin-user-name">系统管理员</span>
      <span class="admin-avatar" aria-hidden="true">管</span>
    </div>
  </header>
</template>
