<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAdminSession } from '../../composables/useAdminSession.js'

const router = useRouter()
const session = useAdminSession()
const currentPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const errorMessage = ref('')

async function submit() {
  errorMessage.value = ''
  if (newPassword.value !== confirmPassword.value) {
    errorMessage.value = '两次输入的新密码不一致。'
    return
  }
  try {
    await session.changePassword(currentPassword.value, newPassword.value)
    await router.replace({ name: 'login', query: { reason: 'password-changed' } })
  } catch (error) {
    errorMessage.value = error.message || '密码修改失败，请检查后重试。'
  }
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-card" aria-labelledby="password-title">
      <h1 id="password-title">首次登录，请修改密码</h1>
      <p>新密码至少 8 个字符。修改成功后需要重新登录。</p>
      <form class="auth-form" @submit.prevent="submit">
        <label>当前密码<input v-model="currentPassword" type="password" autocomplete="current-password" required /></label>
        <label>新密码<input v-model="newPassword" type="password" minlength="8" autocomplete="new-password" required /></label>
        <label>确认新密码<input v-model="confirmPassword" type="password" minlength="8" autocomplete="new-password" required /></label>
        <p v-if="errorMessage" class="auth-error" role="alert">{{ errorMessage }}</p>
        <button class="auth-primary" type="submit" :disabled="session.loading.value">保存并重新登录</button>
      </form>
    </section>
  </main>
</template>
