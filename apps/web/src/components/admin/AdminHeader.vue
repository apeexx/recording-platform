<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAdminSession } from '../../composables/useAdminSession.js'

const route = useRoute()
const router = useRouter()
const session = useAdminSession()

const pageTitle = computed(() => route.meta.title || '工作台')
const roleTitle = computed(() => session.user.value?.role === 'REVIEWER' ? '审核端' : '管理员端')
const avatarText = computed(() => session.user.value?.name?.slice(0, 1) || '用')

async function logout() {
  await session.logout()
  await router.replace({ name: 'login' })
}
</script>

<template>
  <header class="admin-header">
    <div>
      <p class="admin-header__eyebrow">{{ roleTitle }}</p>
      <h1>{{ pageTitle }}</h1>
    </div>

    <div class="admin-header__actions" aria-label="管理员工具区">
      <span class="admin-user-name">{{ session.user.value?.name }}</span>
      <span class="admin-avatar" aria-hidden="true">{{ avatarText }}</span>
      <button class="admin-logout-button" type="button" @click="logout">退出登录</button>
    </div>
  </header>
</template>
