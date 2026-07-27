<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AsyncState from '../../../components/admin/AsyncState.vue'
import PageActions from '../../../components/admin/PageActions.vue'
import PaginationControls from '../../../components/admin/PaginationControls.vue'
import UserSearchSelect from '../../../components/form/UserSearchSelect.vue'
import { reviewApi } from '../../../lib/reviewApi.js'
import { operationId } from '../../../lib/apiUtils.js'
import { useAdminSession } from '../../../composables/useAdminSession.js'
import { useNotifications } from '../../../composables/useNotifications.js'

const notifications=useNotifications(),route=useRoute(),router=useRouter(),session=useAdminSession()
const rows=ref([]),loading=ref(false),error=ref(''),count=ref(5),selected=ref(new Set())
const notice=ref(''),page=ref(0),total=ref(0),reviewerId=ref('')
const isAdmin=computed(()=>session.user.value?.role==='ADMIN'),isReviewer=computed(()=>session.user.value?.role==='REVIEWER')
const selectedRows=computed(()=>rows.value.filter(row=>selected.value.has(row.id)))
const claimableRows=computed(()=>selectedRows.value.filter(row=>row.status==='SUBMITTED'))
const approvableRows=computed(()=>selectedRows.value.filter(row=>row.status==='REVIEW_PENDING'))

async function load(showToast=false){
  loading.value=true;error.value=''
  try{const result=await reviewApi.pool(route.params.taskId,page.value,20);rows.value=result.items||[];total.value=result.total||0}
  catch(e){if(showToast||rows.value.length)notifications.error(e.message);else error.value=e.message}
  finally{loading.value=false}
}
async function claim(){try{const item=await reviewApi.claim(route.params.taskId,operationId('review-claim'));notifications.success('已领取一条审核数据');router.push(`/admin/review/${item.id}`)}catch(e){notifications.error(e.message)}}
async function claimBatch(){try{const items=await reviewApi.claimBatch(route.params.taskId,Number(count.value),operationId('review-claim-batch'));notifications.success(`已领取 ${items.length} 条审核数据`);items[0]?router.push(`/admin/review/${items[0].id}`):router.push('/admin/review')}catch(e){notifications.error(e.message)}}
async function claimItem(row){try{const item=await reviewApi.claimItem(row.id,row.revision,operationId('review-claim-item'));notifications.success('已领取该审核数据');router.push(`/admin/review/${item.id}`)}catch(e){notifications.error(e.message)}}
function toggle(row){const next=new Set(selected.value);next.has(row.id)?next.delete(row.id):next.add(row.id);selected.value=next}
function commands(values){return values.map(row=>({itemId:row.id,expectedRevision:row.revision}))}
function resultNotice(label,result,skipped){
  const success=result.filter(row=>row.success).length
  const failed=result.length-success
  notice.value=`${label}完成：成功 ${success}，失败 ${failed}，不适用 ${skipped}`
  notifications.success(notice.value)
}
async function batchClaimSelected(){
  if(!claimableRows.value.length||!confirm(`确认批量领取 ${claimableRows.value.length} 条已提交数据？`))return
  try{
    const result=await reviewApi.batchClaim(commands(claimableRows.value),operationId('review-batch-claim-selected'))
    resultNotice('批量领取审核',result,selectedRows.value.length-claimableRows.value.length)
    selected.value=new Set();await load(true)
  }catch(e){notifications.error(e.message)}
}
async function batchAssign(){
  if(!reviewerId.value){notifications.error('请先选择审核员');return}
  if(!claimableRows.value.length||!confirm(`确认批量分配 ${claimableRows.value.length} 条已提交数据？`))return
  try{
    const result=await reviewApi.batchAssign(commands(claimableRows.value),reviewerId.value,operationId('review-batch-assign'))
    resultNotice('批量分配',result,selectedRows.value.length-claimableRows.value.length)
    selected.value=new Set();await load(true)
  }catch(e){notifications.error(e.message)}
}
async function batchApprove(){
  if(!approvableRows.value.length||!confirm(`确认批量通过 ${approvableRows.value.length} 条待审核数据？`))return
  try{
    const payload=approvableRows.value.map(row=>({itemId:row.id,expectedRevision:row.revision,text:null}))
    const result=await reviewApi.batchApprove(payload,operationId('review-batch-approve'))
    resultNotice('批量通过',result,selectedRows.value.length-approvableRows.value.length)
    selected.value=new Set();await load(true)
  }catch(e){notifications.error(e.message)}
}
async function assign(row){
  if(!reviewerId.value){notifications.error('请先选择审核员');return}
  try{await reviewApi.assign(row.id,reviewerId.value,row.revision,operationId('review-assign'));notifications.success('审核数据已分配');await load(true)}
  catch(e){notifications.error(e.message)}
}
function changePage(value){page.value=value;selected.value=new Set();load()}
onMounted(load)
</script>

<template>
  <section class="admin-page">
    <PageActions title="任务审核池" description="已提交数据需先领取或分配，进入待审核后才能作出决定。">
      <router-link class="button-secondary" to="/admin/review">返回选择任务</router-link>
      <button class="button-secondary" @click="load(true)">刷新</button>
      <button v-if="isReviewer" class="button-primary" @click="claim">领取一条</button>
    </PageActions>
    <div class="business-card">
      <div class="business-inline">
        <template v-if="isReviewer">
          <label>批量领取数量 <input v-model.number="count" type="number" min="1" max="100" /></label>
          <button class="button-secondary" @click="claimBatch">批量领取</button>
        </template>
        <button class="button-secondary" :disabled="!claimableRows.length" @click="batchClaimSelected">批量领取审核（{{claimableRows.length}}）</button>
        <template v-if="isAdmin">
          <UserSearchSelect v-model="reviewerId" role="REVIEWER" user-type="WEB" placeholder="选择审核员" />
          <button class="button-secondary" :disabled="!claimableRows.length||!reviewerId" @click="batchAssign">批量分配（{{claimableRows.length}}）</button>
          <button class="button-secondary" :disabled="!approvableRows.length" @click="batchApprove">批量通过（{{approvableRows.length}}）</button>
        </template>
      </div>
      <p v-if="notice" class="business-success">{{notice}}</p>
      <AsyncState :loading="loading" :error="error" :empty="!rows.length" empty-text="当前任务没有已提交或待审核数据" @retry="load">
        <div class="business-table-wrap">
          <table class="business-table">
            <thead><tr><th>选择</th><th>条目</th><th>采集员 ID</th><th>采集员姓名</th><th>审核员 ID</th><th>审核员姓名</th><th>状态</th><th>文本</th><th>时长</th><th>操作</th></tr></thead>
            <tbody><tr v-for="r in rows" :key="r.id">
              <td><input type="checkbox" :checked="selected.has(r.id)" @change="toggle(r)" /></td>
              <td>{{r.itemCode}}</td><td>{{r.collectorId||'-'}}</td><td>{{r.collectorName||'-'}}</td>
              <td>{{r.reviewerId||'-'}}</td><td>{{r.reviewerName||'-'}}</td>
              <td>{{r.status==='SUBMITTED'?'已提交':'待审核'}}</td><td>{{r.hasText?'有':'无'}}</td>
              <td>{{r.audioDurationMillis?`${Math.round(r.audioDurationMillis/1000)}秒`:'-'}}</td>
              <td>
                <button v-if="r.status==='SUBMITTED'" class="button-link" @click="claimItem(r)">领取审核</button>
                <button v-if="isAdmin&&r.status==='SUBMITTED'" class="button-link" @click="assign(r)">分配</button>
                <router-link v-if="r.status==='REVIEW_PENDING'" class="button-link" :to="`/admin/review/${r.id}`">审核</router-link>
              </td>
            </tr></tbody>
          </table>
        </div>
        <PaginationControls :page="page" :total="total" :size="20" @change="changePage" />
      </AsyncState>
    </div>
  </section>
</template>
