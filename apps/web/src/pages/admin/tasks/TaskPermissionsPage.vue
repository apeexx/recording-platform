<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import PageActions from '../../../components/admin/PageActions.vue'
import AsyncState from '../../../components/admin/AsyncState.vue'
import PaginationControls from '../../../components/admin/PaginationControls.vue'
import { taskApi } from '../../../lib/taskApi.js'
import { userApi } from '../../../lib/userApi.js'
import { operationId } from '../../../lib/apiUtils.js'
import { statusLabel } from '../../../lib/statusLabels.js'
import { useNotifications } from '../../../composables/useNotifications.js'

const route = useRoute(), notifications = useNotifications()
const query = ref(''), users = ref([]), userPage = ref(0), userTotal = ref(0)
const grants = ref([]), requests = ref([]), loadError = ref(''), loading = ref(false), userLoading = ref(false)
const grantedIds = computed(() => new Set(grants.value.filter(row => row.status === 'ACTIVE').map(row => row.userId)))
async function load(showToast = false) {
  loading.value = true; loadError.value = ''
  try {
    const [grantResult, requestResult] = await Promise.all([
      taskApi.grants(route.params.id, 0, 100), taskApi.accessRequests(route.params.id, 0, 100),
    ])
    grants.value = grantResult.items || []; requests.value = requestResult.items || []
  } catch (error) {
    if (showToast || grants.value.length || requests.value.length) notifications.error(error.message)
    else loadError.value = error.message
  } finally { loading.value = false }
}
async function search(reset = false) {
  if (reset) userPage.value = 0
  userLoading.value = true
  try {
    const result = await userApi.search({
      query: query.value, role: 'COLLECTOR', userType: 'MINIPROGRAM', page: userPage.value, size: 20,
    })
    users.value = result.content || result.items || []; userTotal.value = result.totalElements ?? result.total ?? 0
  } catch (error) { notifications.error(error.message) }
  finally { userLoading.value = false }
}
async function grant(user) {
  try { await taskApi.grant(route.params.id, user.id, operationId('grant')); notifications.success('采集权限已授予'); await load(true) }
  catch (error) { notifications.error(error.message) }
}
async function revoke(row) {
  if (!confirm('确认撤销该用户的新领取权限？已领取数据仍可完成。')) return
  try { await taskApi.revokeGrant(route.params.id, row.userId, operationId('grant-revoke')); notifications.success('采集权限已撤销'); await load(true) }
  catch (error) { notifications.error(error.message) }
}
async function decide(row, action) {
  const previous = [...requests.value]; requests.value = requests.value.filter(item => item.id !== row.id)
  try { await taskApi.decideAccess(route.params.id, row.id, action, action === 'reject' ? '管理员驳回' : '', operationId(`access-${action}`)); notifications.success(action === 'approve' ? '申请已通过' : '申请已驳回'); await load(true) }
  catch (error) { requests.value = previous; notifications.error(error.message) }
}
async function changeUserPage(value) { userPage.value = value; await search() }
onMounted(async () => { await Promise.all([load(), search()]) })
</script>

<template>
  <section class="admin-page">
    <PageActions title="采集权限" description="浏览全部小程序用户，按姓名、用户 ID 或登录账号搜索并授权。"><router-link class="button-secondary" :to="`/admin/tasks/${route.params.id}`">返回任务</router-link></PageActions>
    <AsyncState :loading="loading" :error="loadError" :empty="false" @retry="load">
      <div class="permission-layout">
        <article class="business-card permission-users">
          <div class="business-heading"><div><h3>小程序用户（{{ userTotal }}）</h3><p>已授权人员会自动标记，避免重复操作。</p></div></div>
          <form class="business-inline permission-search" novalidate @submit.prevent="search(true)"><input v-model.trim="query" placeholder="姓名、完整用户 ID 或登录账号"><button class="button-primary">搜索</button></form>
          <AsyncState :loading="userLoading" :error="''" :empty="!users.length" empty-text="没有匹配的小程序用户">
            <div class="permission-user-list">
              <div v-for="user in users" :key="user.id"><span><strong>{{ user.name || '未设置姓名' }}</strong><small>{{ user.id }} · {{ user.loginName || '未设置登录账号' }}</small></span><button class="button-secondary" :disabled="grantedIds.has(user.id)" @click="grant(user)">{{ grantedIds.has(user.id) ? '已授权' : '直接授权' }}</button></div>
            </div>
            <PaginationControls :page="userPage" :size="20" :total="userTotal" @change="changeUserPage"/>
          </AsyncState>
        </article>
        <div class="permission-side">
          <article class="business-card"><h3>待审批申请（{{ requests.length }}）</h3><div v-if="requests.length" class="business-list"><div v-for="row in requests" :key="row.id"><span><strong>{{ row.userName || '未设置姓名' }}</strong><small>{{ row.userId }} · {{ statusLabel('access', row.status) }}</small></span><span><button class="button-link" @click="decide(row,'approve')">通过</button><button class="button-link is-danger" @click="decide(row,'reject')">驳回</button></span></div></div><p v-else class="business-note">当前没有待审批申请。</p></article>
          <article class="business-card"><h3>已授权用户（{{ grants.filter(row => row.status === 'ACTIVE').length }}）</h3><div v-if="grants.length" class="business-list"><div v-for="row in grants" :key="row.id"><span><strong>{{ row.userName || '未设置姓名' }}</strong><small>{{ row.userId }} · {{ statusLabel('grant', row.status) }}</small></span><button v-if="row.status === 'ACTIVE'" class="button-link is-danger" @click="revoke(row)">撤销</button></div></div><p v-else class="business-note">尚未授权采集员。</p></article>
        </div>
      </div>
    </AsyncState>
  </section>
</template>
