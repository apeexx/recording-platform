<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAdminSession } from '../../composables/useAdminSession.js'
import { homeForRole } from '../../router/guards.js'

const router = useRouter()
const session = useAdminSession()
const username = ref('')
const password = ref('')
const errorMessage = ref('')
const takeoverToken = ref('')

async function finishLogin(user) {
  await router.replace(user.firstPasswordChangeRequired ? '/first-password' : homeForRole(user.role))
}

async function submit() {
  errorMessage.value = ''
  takeoverToken.value = ''
  try {
    await finishLogin(await session.login(username.value.trim(), password.value))
  } catch (error) {
    if (error.code === 'ACCOUNT_IN_USE' && error.details?.takeoverToken) {
      takeoverToken.value = error.details.takeoverToken
      errorMessage.value = '该账号正在其他设备使用，继续登录会使原设备退出。'
    } else {
      errorMessage.value = error.message || '登录失败，请稍后重试。'
    }
  }
}

async function confirmTakeover() {
  errorMessage.value = ''
  try {
    await finishLogin(await session.takeover(takeoverToken.value))
  } catch (error) {
    takeoverToken.value = ''
    errorMessage.value = error.message || '接管登录失败，请重新登录。'
  }
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-card" aria-labelledby="login-title">
      <div class="auth-brand"><span>录</span><div><strong>录音任务平台</strong><small>后台管理与审核端</small></div></div>
      <h1 id="login-title">账号登录</h1>
      <p>管理员和审核员使用后台账号密码登录。</p>
      <form class="auth-form" @submit.prevent="submit">
        <label>账号<input v-model="username" name="username" autocomplete="username" required /></label>
        <label>密码<input v-model="password" name="password" type="password" autocomplete="current-password" required /></label>
        <p v-if="errorMessage" class="auth-error" role="alert">{{ errorMessage }}</p>
        <button class="auth-primary" type="submit" :disabled="session.loading.value">{{ session.loading.value ? '登录中…' : '登录' }}</button>
      </form>
      <div v-if="takeoverToken" class="auth-takeover">
        <strong>是否强制登录？</strong>
        <p>确认后，原设备会在下一次请求时返回登录页。</p>
        <div><button type="button" class="auth-secondary" @click="takeoverToken = ''">取消</button><button type="button" class="auth-primary" @click="confirmTakeover">确认登录</button></div>
      </div>
    </section>
  </main>
</template>
