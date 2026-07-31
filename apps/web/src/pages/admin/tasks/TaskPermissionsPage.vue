<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import PageActions from '../../../components/admin/PageActions.vue'
import AsyncState from '../../../components/admin/AsyncState.vue'
import PaginationControls from '../../../components/admin/PaginationControls.vue'
import HelpPopover from '../../../components/form/HelpPopover.vue'
import { taskApi } from '../../../lib/taskApi.js'
import { userApi } from '../../../lib/userApi.js'
import { operationId } from '../../../lib/apiUtils.js'
import { useNotifications } from '../../../composables/useNotifications.js'

const route = useRoute()
const notifications = useNotifications()
const loading = ref(false)
const loadError = ref('')

const users = ref([])
const userQuery = ref('')
const userPage = ref(0)
const userSize = ref(10)
const userTotal = ref(0)
const userLoading = ref(false)

const requests = ref([])
const requestQuery = ref('')
const requestPage = ref(0)
const requestSize = ref(5)
const requestTotal = ref(0)
const requestLoading = ref(false)

const grants = ref([])
const grantQuery = ref('')
const grantPage = ref(0)
const grantSize = ref(5)
const grantTotal = ref(0)
const grantLoading = ref(false)
const allGrantIds = ref(new Set())
const grantedIds = computed(() => allGrantIds.value)

async function loadAllGrantIds() {
  const ids = new Set()
  let currentPage = 0
  let total = 0
  let loaded = 0
  do {
    const result = await taskApi.grants(route.params.id, currentPage, 100, { status: 'ACTIVE' })
    const pageItems = result.items || []
    pageItems.forEach((row) => ids.add(row.userId))
    loaded += pageItems.length
    total = Number(result.total) || 0
    currentPage += 1
    if (!pageItems.length) break
  } while (loaded < total)
  allGrantIds.value = ids
}
async function loadUsers(reset = false, initial = false) {
  if (reset) userPage.value = 0
  userLoading.value = true
  try {
    const result = await userApi.search({
      query: userQuery.value, role: 'COLLECTOR', userType: 'MINIPROGRAM',
      page: userPage.value, size: userSize.value,
    })
    users.value = result.content || result.items || []
    userTotal.value = result.totalElements ?? result.total ?? 0
    const lastPage = Math.max(Math.ceil(userTotal.value / userSize.value) - 1, 0)
    if (userPage.value > lastPage) {
      userPage.value = lastPage
      return loadUsers()
    }
  } catch (error) {
    if (initial) throw error
    notifications.error(error.message)
  } finally {
    userLoading.value = false
  }
}
async function loadRequests(reset = false, initial = false) {
  if (reset) requestPage.value = 0
  requestLoading.value = true
  try {
    const result = await taskApi.accessRequests(route.params.id, requestPage.value, requestSize.value, { query: requestQuery.value })
    requests.value = result.items || []
    requestTotal.value = Number(result.total) || 0
    const lastPage = Math.max(Math.ceil(requestTotal.value / requestSize.value) - 1, 0)
    if (requestPage.value > lastPage) {
      requestPage.value = lastPage
      return loadRequests()
    }
  } catch (error) {
    if (initial) throw error
    notifications.error(error.message)
  } finally {
    requestLoading.value = false
  }
}
async function loadGrants(reset = false, initial = false) {
  if (reset) grantPage.value = 0
  grantLoading.value = true
  try {
    const result = await taskApi.grants(route.params.id, grantPage.value, grantSize.value, {
      status: 'ACTIVE', query: grantQuery.value,
    })
    grants.value = result.items || []
    grantTotal.value = Number(result.total) || 0
    const lastPage = Math.max(Math.ceil(grantTotal.value / grantSize.value) - 1, 0)
    if (grantPage.value > lastPage) {
      grantPage.value = lastPage
      return loadGrants()
    }
  } catch (error) {
    if (initial) throw error
    notifications.error(error.message)
  } finally {
    grantLoading.value = false
  }
}
async function initialize() {
  loading.value = true
  loadError.value = ''
  try {
    await Promise.all([loadUsers(false, true), loadRequests(false, true), loadGrants(false, true), loadAllGrantIds()])
  } catch (error) {
    loadError.value = error.message
  } finally {
    loading.value = false
  }
}
async function changeUserPage(value) { userPage.value = value; await loadUsers() }
async function changeRequestPage(value) { requestPage.value = value; await loadRequests() }
async function changeGrantPage(value) { grantPage.value = value; await loadGrants() }
async function changeUserSize(value) { userSize.value = value; userPage.value = 0; await loadUsers() }
async function changeRequestSize(value) { requestSize.value = value; requestPage.value = 0; await loadRequests() }
async function changeGrantSize(value) { grantSize.value = value; grantPage.value = 0; await loadGrants() }
async function refreshUsersAndGrants() {
  await Promise.all([loadUsers(), loadGrants(), loadAllGrantIds()])
}
async function grant(user) {
  try {
    await taskApi.grant(route.params.id, user.id, operationId('grant'))
    notifications.success('采集权限已授予')
    await refreshUsersAndGrants()
  } catch (error) {
    notifications.error(error.message)
  }
}
async function revoke(row) {
  if (!confirm('确认撤销该用户的新领取权限？已领取数据仍可完成。')) return
  try {
    await taskApi.revokeGrant(route.params.id, row.userId, operationId('grant-revoke'))
    notifications.success('采集权限已撤销')
    await refreshUsersAndGrants()
  } catch (error) {
    notifications.error(error.message)
  }
}
async function decide(row, action) {
  try {
    await taskApi.decideAccess(route.params.id, row.id, action,
      action === 'reject' ? '管理员驳回' : '', operationId(`access-${action}`))
    notifications.success(action === 'approve' ? '申请已通过' : '申请已驳回')
    if (action === 'approve') await Promise.all([loadRequests(), refreshUsersAndGrants()])
    else await loadRequests()
  } catch (error) {
    notifications.error(error.message)
  }
}
onMounted(initialize)
</script>

<template>
  <section class="admin-page">
    <PageActions title="采集权限" description="三个目录均支持按姓名、完整用户 ID 或登录账号搜索。">
      <router-link class="button-secondary" :to="`/admin/tasks/${route.params.id}`">返回任务</router-link>
    </PageActions>
    <AsyncState :loading="loading" :error="loadError" :empty="false" @retry="initialize">
      <div class="permission-layout">
        <article class="business-card permission-users">
          <div class="business-heading"><div><h3>小程序用户（{{ userTotal }}）</h3><p>已授权人员会自动标记，避免重复操作。</p></div></div>
          <form class="business-inline permission-search" novalidate @submit.prevent="loadUsers(true)">
            <input v-model.trim="userQuery" placeholder="姓名、完整用户 ID 或登录账号">
            <button class="button-primary" :disabled="userLoading">搜索</button>
          </form>
          <div class="permission-list-slot permission-list-slot--ten">
            <AsyncState :loading="userLoading" :error="''" :empty="!users.length" empty-text="没有匹配的小程序用户">
              <div class="permission-user-list">
              <div v-for="user in users" :key="user.id">
                <span><strong>{{ user.name || '未设置姓名' }}</strong><small>{{ user.id }} · {{ user.loginName || '未设置登录账号' }}</small></span>
                <button class="button-secondary" :disabled="grantedIds.has(user.id)" @click="grant(user)">{{ grantedIds.has(user.id) ? '已授权' : '直接授权' }}</button>
              </div>
              </div>
            </AsyncState>
          </div>
          <PaginationControls :page="userPage" :size="userSize" :page-sizes="[5, 10]" :total="userTotal" @change="changeUserPage" @size-change="changeUserSize"/>
        </article>

        <div class="permission-side">
          <article class="business-card">
            <div class="business-heading"><div><h3>待审批申请（{{ requestTotal }}） <HelpPopover label="授权申请说明" content="采集员申请进入待审批列表；批准后形成当前任务的有效授权，拒绝只结束本次申请。" /></h3><p>仅显示等待处理的申请。</p></div></div>
            <form class="business-inline permission-search" novalidate @submit.prevent="loadRequests(true)">
              <input v-model.trim="requestQuery" placeholder="姓名、完整用户 ID 或登录账号">
              <button class="button-primary" :disabled="requestLoading">搜索</button>
            </form>
            <div class="permission-list-slot permission-list-slot--five">
              <AsyncState :loading="requestLoading" :error="''" :empty="!requests.length" empty-text="当前没有待审批申请">
                <div class="permission-user-list">
                <div v-for="row in requests" :key="row.id">
                  <span><strong>{{ row.userName || '未设置姓名' }}</strong><small>{{ row.userId }} · {{ row.userLoginName || '未设置登录账号' }}</small></span>
                  <span class="permission-row-actions"><button class="button-link" @click="decide(row,'approve')">通过</button><button class="button-link is-danger" @click="decide(row,'reject')">驳回</button></span>
                </div>
                </div>
              </AsyncState>
            </div>
            <PaginationControls :page="requestPage" :size="requestSize" :page-sizes="[5, 10]" :total="requestTotal" @change="changeRequestPage" @size-change="changeRequestSize"/>
          </article>

          <article class="business-card">
            <div class="business-heading"><div><h3>已授权用户（{{ grantTotal }}） <HelpPopover label="有效授权说明" content="有效授权允许采集员领取该任务的新条目；撤销只阻止后续领取，不影响其已领取条目的提交和释放。" /></h3><p>仅显示当前有效授权。</p></div></div>
            <form class="business-inline permission-search" novalidate @submit.prevent="loadGrants(true)">
              <input v-model.trim="grantQuery" placeholder="姓名、完整用户 ID 或登录账号">
              <button class="button-primary" :disabled="grantLoading">搜索</button>
            </form>
            <div class="permission-list-slot permission-list-slot--five">
              <AsyncState :loading="grantLoading" :error="''" :empty="!grants.length" empty-text="尚未授权采集员">
                <div class="permission-user-list">
                <div v-for="row in grants" :key="row.id">
                  <span><strong>{{ row.userName || '未设置姓名' }}</strong><small>{{ row.userId }} · {{ row.userLoginName || '未设置登录账号' }}</small></span>
                  <button class="button-link is-danger" @click="revoke(row)">撤销</button>
                </div>
                </div>
              </AsyncState>
            </div>
            <PaginationControls :page="grantPage" :size="grantSize" :page-sizes="[5, 10]" :total="grantTotal" @change="changeGrantPage" @size-change="changeGrantSize"/>
          </article>
        </div>
      </div>
    </AsyncState>
  </section>
</template>
