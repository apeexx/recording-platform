<script setup>
import { nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import PageActions from '../../../components/admin/PageActions.vue'
import AsyncState from '../../../components/admin/AsyncState.vue'
import BaseSelect from '../../../components/form/BaseSelect.vue'
import { useNotifications } from '../../../composables/useNotifications.js'
import { statusLabel } from '../../../lib/statusLabels.js'
import { userApi } from '../../../lib/userApi.js'

const notifications = useNotifications()
const rows = ref([])
const loading = ref(false)
const error = ref('')
const query = ref('')
const view = ref('WEB')
const createOpen = ref(false)
const creating = ref(false)
const createTrigger = ref(null)
const createPanel = ref(null)
const usernameInput = ref(null)
const nameInput = ref(null)
const passwordInput = ref(null)
const form = reactive({ username: '', name: '', role: 'REVIEWER', initialPassword: '' })
const roleOptions = [{ value: 'REVIEWER', label: '审核员' }, { value: 'ADMIN', label: '管理员' }]
let loadSequence = 0

async function load(showToast = false) {
  const sequence = ++loadSequence
  loading.value = true
  error.value = ''
  try {
    const response = await userApi.search({query:query.value,userType:view.value,page:0,size:100})
    if (sequence === loadSequence) rows.value = response.content || []
  } catch (exception) {
    if (sequence === loadSequence) {
      if (showToast || rows.value.length) notifications.error(exception.message)
      else error.value = exception.message
    }
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}

function switchView(next) {
  if (view.value === next) return
  closeCreate()
  view.value = next
  query.value = ''
  rows.value = []
  load()
}

function resetCreateForm() {
  Object.assign(form, { username: '', name: '', role: 'REVIEWER', initialPassword: '' })
}

function openCreate() {
  createOpen.value = true
}

function closeCreate() {
  if (creating.value) return
  createOpen.value = false
  resetCreateForm()
}

function handleCreateKeydown(event) {
  if (!createOpen.value) return
  if (event.key === 'Escape') {
    closeCreate()
    return
  }
  if (event.key !== 'Tab') return
  const controls = [...(createPanel.value?.querySelectorAll('input:not(:disabled), button:not(:disabled)') || [])]
  if (!controls.length) return
  const first = controls[0]
  const last = controls[controls.length - 1]
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}

async function create() {
  if (creating.value) return
  if (!form.username) {
    notifications.error('请输入用户名')
    usernameInput.value?.focus()
    return
  }
  if (!form.name) {
    notifications.error('请输入姓名')
    nameInput.value?.focus()
    return
  }
  if (form.initialPassword.length < 8) {
    notifications.error('初始密码至少需要 8 个字符')
    passwordInput.value?.focus()
    return
  }
  creating.value = true
  try {
    await userApi.create({ ...form })
    createOpen.value = false
    resetCreateForm()
    notifications.success('后台账号已创建')
    await load(true)
  } catch (exception) {
    notifications.error(exception.message)
  } finally {
    creating.value = false
  }
}

async function disable(row) {
  if (!confirm(`确认停用账号 ${row.loginName}？其活动会话将立即失效。`)) return
  try {
    await userApi.disable(row.id)
    notifications.success('账号已停用')
    await load(true)
  } catch (exception) {
    notifications.error(exception.message)
  }
}

async function reset(row) {
  const password = prompt(`为 ${row.name || row.loginName || row.id} 设置新密码（至少8字符）`)
  if (!password) return
  try {
    await userApi.resetPassword(row.id, password)
    notifications.success(row.role === 'COLLECTOR' ? '采集员密码已重置，活动会话已失效' : '密码已重置，用户下次登录必须修改密码')
    await load(true)
  } catch (exception) {
    notifications.error(exception.message)
  }
}

async function editAccount(row) {
  const account = prompt('输入 6–12 位非零开头数字账号', row.loginName || '')
  if (!account) return
  try {
    await userApi.updateCollectorAccount(row.id, account)
    notifications.success('采集员账号已修改，活动会话已失效')
    await load(true)
  } catch (exception) {
    notifications.error(exception.message)
  }
}

watch(createOpen, async (open) => {
  document.body.style.overflow = open ? 'hidden' : ''
  if (open) {
    await nextTick()
    createPanel.value?.querySelector('input')?.focus()
  } else {
    createTrigger.value?.focus()
  }
})

onMounted(load)
onMounted(() => window.addEventListener('keydown', handleCreateKeydown))
onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleCreateKeydown)
  document.body.style.overflow = ''
})
</script>

<template>
  <section>
    <PageActions title="用户管理" description="分别管理 Web 端账号与小程序端账号">
      <button data-user-type="WEB" :class="['tab', view === 'WEB' && 'active']" @click="switchView('WEB')">Web 端账号</button>
      <button data-user-type="MINIPROGRAM" :class="['tab', view === 'MINIPROGRAM' && 'active']" @click="switchView('MINIPROGRAM')">小程序端账号</button>
    </PageActions>
    <div class="toolbar">
      <input v-model="query" placeholder="按姓名、完整用户 ID 或登录名搜索" @keyup.enter="load(true)">
      <button class="primary" @click="load(true)">搜索</button>
      <button v-if="view === 'WEB'" ref="createTrigger" data-testid="open-create-user" class="primary create-trigger" @click="openCreate">创建后台账号</button>
    </div>
    <AsyncState :loading="loading" :error="error" :empty="!rows.length">
      <div class="panel">
        <table>
          <thead><tr><th>用户 ID</th><th>姓名</th><th>登录名</th><th>来源</th><th>角色</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="row in rows" :key="row.id">
              <td>{{ row.id }}</td><td>{{ row.name || '-' }}</td><td>{{ row.loginName || '未设置' }}</td>
              <td>{{ row.userType === 'MINIPROGRAM' ? '小程序' : '后台' }}</td>
              <td>{{ row.role === 'COLLECTOR' ? '采集员' : row.role === 'REVIEWER' ? '审核员' : '管理员' }}</td>
              <td>{{ statusLabel(row.status) }}</td>
              <td>
                <button v-if="row.userType === 'MINIPROGRAM'" class="link" @click="editAccount(row)">修改账号</button>
                <button class="link" @click="reset(row)">重置密码</button>
                <button v-if="row.role !== 'COLLECTOR'" class="link danger" @click="disable(row)">停用</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </AsyncState>
    <Teleport to="body">
      <div v-if="createOpen" data-testid="create-user-modal" class="create-modal-backdrop" @click.self="closeCreate">
        <section ref="createPanel" class="create-modal" role="dialog" aria-modal="true" aria-label="创建后台账号">
          <h2>创建后台账号</h2>
          <form class="create-modal-form" novalidate @submit.prevent="create">
            <label>用户名<input ref="usernameInput" v-model.trim="form.username" required autocomplete="off" placeholder="请输入用户名"></label>
            <label>姓名<input ref="nameInput" v-model.trim="form.name" required autocomplete="off" placeholder="请输入姓名"></label>
            <label>角色<BaseSelect v-model="form.role" :options="roleOptions" aria-label="角色" /></label>
            <label>初始密码<input ref="passwordInput" v-model="form.initialPassword" type="password" required minlength="8" autocomplete="new-password" placeholder="至少 8 个字符"></label>
            <div class="create-modal-actions">
              <button type="button" data-testid="create-user-cancel" class="modal-secondary" :disabled="creating" @click="closeCreate">取消</button>
              <button type="submit" class="primary" :disabled="creating">{{ creating ? '创建中…' : '确定创建' }}</button>
            </div>
          </form>
        </section>
      </div>
    </Teleport>
  </section>
</template>

<style scoped>
.tab{border:1px solid var(--border);background:var(--card);color:var(--foreground);padding:10px 18px;border-radius:12px}.tab.active,.primary{background:var(--primary);color:#fff;border-color:var(--primary)}.toolbar{display:flex;gap:12px;margin-bottom:18px}.toolbar input{min-width:320px;border:1px solid var(--border);border-radius:10px;padding:10px 12px}.create-trigger{margin-left:auto}.panel{background:var(--card);border:1px solid var(--border);border-radius:var(--radius);padding:20px}.primary{border:0;border-radius:10px;padding:10px 18px}.primary:disabled,.modal-secondary:disabled{opacity:.65;cursor:not-allowed}table{width:100%;border-collapse:collapse}th,td{text-align:left;padding:14px;border-bottom:1px solid var(--border)}.link{border:0;background:transparent;color:var(--primary);margin-right:12px}.danger{color:#dc2626}.create-modal-backdrop{position:fixed;inset:0;z-index:2900;display:grid;place-items:center;padding:24px;background:rgba(15,23,42,.48);backdrop-filter:blur(4px)}.create-modal{width:min(480px,100%);padding:26px;border:1px solid var(--border);border-radius:var(--radius);background:var(--card);color:var(--foreground);box-shadow:0 24px 70px rgba(15,23,42,.22)}.create-modal h2{margin:0 0 22px}.create-modal-form{display:grid;gap:16px}.create-modal-form label{display:grid;gap:8px;font-weight:700}.create-modal-form input,.create-modal-form select{width:100%;border:1px solid var(--border);border-radius:10px;background:var(--background);color:var(--foreground);padding:11px 12px}.create-modal-actions{display:flex;justify-content:flex-end;gap:10px;margin-top:8px}.modal-secondary{border:1px solid var(--border);border-radius:10px;background:var(--card);color:var(--foreground);padding:10px 18px}@media(max-width:640px){.toolbar{flex-wrap:wrap}.toolbar input{min-width:0;flex:1 1 100%}.create-trigger{margin-left:0}.create-modal-backdrop{padding:16px}}
</style>
