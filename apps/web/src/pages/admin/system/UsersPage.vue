<script setup>
import { onMounted, reactive, ref } from 'vue'
import PageActions from '../../../components/admin/PageActions.vue'
import AsyncState from '../../../components/admin/AsyncState.vue'
import { useNotifications } from '../../../composables/useNotifications.js'
import { statusLabel } from '../../../lib/statusLabels.js'
import { userApi } from '../../../lib/userApi.js'

const notifications = useNotifications()
const rows = ref([])
const loading = ref(false)
const error = ref('')
const query = ref('')
const view = ref('WEB')
const form = reactive({ username: '', name: '', role: 'REVIEWER', initialPassword: '' })
let loadSequence = 0

async function load() {
  const sequence = ++loadSequence
  loading.value = true
  error.value = ''
  try {
    const response = await userApi.search({query:query.value,userType:view.value,page:0,size:100})
    if (sequence === loadSequence) rows.value = response.content || []
  } catch (exception) {
    if (sequence === loadSequence) error.value = exception.message
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}

function switchView(next) {
  if (view.value === next) return
  view.value = next
  query.value = ''
  rows.value = []
  load()
}

async function create() {
  try {
    await userApi.create({ ...form })
    Object.keys(form).forEach((key) => { form[key] = key === 'role' ? 'REVIEWER' : '' })
    notifications.success('后台账号已创建')
    await load()
  } catch (exception) {
    error.value = exception.message
    notifications.error(exception.message)
  }
}

async function disable(row) {
  if (!confirm(`确认停用账号 ${row.loginName}？其活动会话将立即失效。`)) return
  try {
    await userApi.disable(row.id)
    notifications.success('账号已停用')
    await load()
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
    await load()
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
    await load()
  } catch (exception) {
    notifications.error(exception.message)
  }
}

onMounted(load)
</script>

<template>
  <section>
    <PageActions title="用户管理" description="分别管理 Web 端账号与小程序端账号">
      <button data-user-type="WEB" :class="['tab', view === 'WEB' && 'active']" @click="switchView('WEB')">Web 端账号</button>
      <button data-user-type="MINIPROGRAM" :class="['tab', view === 'MINIPROGRAM' && 'active']" @click="switchView('MINIPROGRAM')">小程序端账号</button>
    </PageActions>
    <div class="toolbar">
      <input v-model="query" placeholder="按姓名、完整用户 ID 或登录名搜索" @keyup.enter="load">
      <button class="primary" @click="load">搜索</button>
    </div>
    <div v-if="view === 'WEB'" class="panel create">
      <input v-model="form.username" placeholder="用户名">
      <input v-model="form.name" placeholder="姓名">
      <select v-model="form.role"><option value="REVIEWER">审核员</option><option value="ADMIN">管理员</option></select>
      <input v-model="form.initialPassword" type="password" placeholder="初始密码">
      <button class="primary" @click="create">创建后台账号</button>
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
  </section>
</template>

<style scoped>
.tab{border:1px solid var(--border);background:var(--card);color:var(--foreground);padding:10px 18px;border-radius:12px}.tab.active,.primary{background:var(--primary);color:#fff;border-color:var(--primary)}.toolbar,.create{display:flex;gap:12px;margin-bottom:18px}.toolbar input{min-width:320px}.panel{background:var(--card);border:1px solid var(--border);border-radius:var(--radius);padding:20px}.create input,.create select,.toolbar input{border:1px solid var(--border);border-radius:10px;padding:10px 12px}.primary{border:0;border-radius:10px;padding:10px 18px}table{width:100%;border-collapse:collapse}th,td{text-align:left;padding:14px;border-bottom:1px solid var(--border)}.link{border:0;background:transparent;color:var(--primary);margin-right:12px}.danger{color:#dc2626}
</style>
