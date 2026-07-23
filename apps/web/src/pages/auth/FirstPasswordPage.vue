<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAdminSession } from '../../composables/useAdminSession.js'
import { useNotifications } from '../../composables/useNotifications.js'

const router = useRouter()
const session = useAdminSession()
const notifications = useNotifications()
const newPassword = ref('')
const confirmPassword = ref('')
const newPasswordInput = ref(null)
const confirmPasswordInput = ref(null)

async function submit() {
  if (newPassword.value.length < 8) {
    notifications.error('新密码至少需要 8 个字符')
    newPasswordInput.value?.focus()
    return
  }
  if (!confirmPassword.value) {
    notifications.error('请再次输入新密码')
    confirmPasswordInput.value?.focus()
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    notifications.error('两次输入的新密码不一致')
    confirmPasswordInput.value?.focus()
    return
  }
  try {
    await session.changeInitialPassword(newPassword.value)
    await router.replace({ name: 'login', query: { reason: 'password-changed' } })
  } catch (error) {
    notifications.error(error.message || '密码修改失败，请检查后重试')
  }
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-card" aria-labelledby="password-title">
      <h1 id="password-title">设置新密码</h1>
      <p>新密码至少 8 个字符。修改成功后需要重新登录。</p>
      <form class="auth-form" novalidate @submit.prevent="submit">
        <label>新密码<input ref="newPasswordInput" v-model="newPassword" type="password" minlength="8" autocomplete="new-password" required /></label>
        <label>确认新密码<input ref="confirmPasswordInput" v-model="confirmPassword" type="password" minlength="8" autocomplete="new-password" required /></label>
        <button class="auth-primary" type="submit" :disabled="session.loading.value">保存并重新登录</button>
      </form>
    </section>
  </main>
</template>
