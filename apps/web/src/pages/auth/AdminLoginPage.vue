<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAdminSession } from '../../composables/useAdminSession.js'
import { homeForRole } from '../../router/guards.js'
import ConfirmModal from '../../components/feedback/ConfirmModal.vue'

const router = useRouter()
const session = useAdminSession()
const username = ref('')
const password = ref('')
const errorMessage = ref('')
const takeoverToken = ref('')
const takeoverBusy = ref(false)
const passwordVisible = ref(false)
const initialPasswordPrompt = ref(false)
const initialPasswordBusy = ref(false)

async function finishLogin(user) {
  if (user.firstPasswordChangeRequired) {
    initialPasswordPrompt.value = true
    return
  }
  await router.replace(homeForRole(user.role))
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

async function skipInitialPassword() {
  initialPasswordBusy.value = true
  try {
    await session.skipInitialPasswordChange()
    initialPasswordPrompt.value = false
    await router.replace(homeForRole(session.user.value.role))
  } catch (error) {
    errorMessage.value = error.message || '暂不修改密码失败，请稍后重试。'
  } finally {
    initialPasswordBusy.value = false
  }
}

async function openInitialPasswordPage() {
  initialPasswordPrompt.value = false
  await router.push('/first-password')
}

onMounted(() => {
  if (session.user.value?.firstPasswordChangeRequired) initialPasswordPrompt.value = true
})

async function confirmTakeover() {
  errorMessage.value = ''
  takeoverBusy.value = true
  try {
    await finishLogin(await session.takeover(takeoverToken.value))
  } catch (error) {
    takeoverToken.value = ''
    errorMessage.value = error.message || '接管登录失败，请重新登录。'
  } finally {
    takeoverBusy.value = false
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
        <label>密码<span class="auth-password-field"><input v-model="password" name="password" :type="passwordVisible ? 'text' : 'password'" autocomplete="current-password" required /><button type="button" class="auth-password-toggle" :aria-label="passwordVisible ? '隐藏密码' : '显示密码'" :aria-pressed="passwordVisible" @click="passwordVisible = !passwordVisible"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M2.5 12s3.5-6 9.5-6 9.5 6 9.5 6-3.5 6-9.5 6-9.5-6-9.5-6Z"/><circle cx="12" cy="12" r="2.7"/></svg></button></span></label>
        <p v-if="errorMessage" class="auth-error" role="alert">{{ errorMessage }}</p>
        <button class="auth-primary" type="submit" :disabled="session.loading.value">{{ session.loading.value ? '登录中…' : '登录' }}</button>
      </form>
    </section>
    <ConfirmModal :open="!!takeoverToken" title="是否强制登录？" message="确认后，原设备会在下一次请求时退出登录。" :busy="takeoverBusy" confirm-text="确认登录" @cancel="takeoverToken = ''" @confirm="confirmTakeover" />
    <ConfirmModal :open="initialPasswordPrompt" title="是否修改初始密码？" message="当前账号正在使用管理员设置的初始密码。你可以现在修改，也可以暂不修改并直接进入系统。" :busy="initialPasswordBusy" cancel-text="暂不修改" confirm-text="修改密码" @cancel="skipInitialPassword" @confirm="openInitialPasswordPage" />
  </main>
</template>
