<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AsyncState from '../../../components/admin/AsyncState.vue'
import PageActions from '../../../components/admin/PageActions.vue'
import PaginationControls from '../../../components/admin/PaginationControls.vue'
import { reviewApi } from '../../../lib/reviewApi.js'
import { operationId } from '../../../lib/apiUtils.js'
import { useAdminSession } from '../../../composables/useAdminSession.js'
import { useNotifications } from '../../../composables/useNotifications.js'
const notifications=useNotifications(),route=useRoute(),router=useRouter(),session=useAdminSession(),rows=ref([]),loading=ref(false),error=ref(''),count=ref(5),selected=ref(new Set()),notice=ref(''),page=ref(0),total=ref(0)
const isAdmin=computed(()=>session.user.value?.role==='ADMIN'),isReviewer=computed(()=>session.user.value?.role==='REVIEWER')
async function load(){loading.value=true;error.value='';try{const result=await reviewApi.pool(route.params.taskId,page.value,20);rows.value=result.items||[];total.value=result.total||0}catch(e){error.value=e.message}finally{loading.value=false}}
async function claim(){try{const item=await reviewApi.claim(route.params.taskId,operationId('review-claim'));notifications.success('已领取一条审核数据');router.push(`/admin/review/${item.id}`)}catch(e){error.value=e.message;notifications.error(e.message)}}
async function claimBatch(){try{const items=await reviewApi.claimBatch(route.params.taskId,Number(count.value),operationId('review-claim-batch'));notifications.success(`已领取 ${items.length} 条审核数据`);items[0]?router.push(`/admin/review/${items[0].id}`):router.push('/admin/review')}catch(e){error.value=e.message;notifications.error(e.message)}}
async function claimItem(row){try{const item=await reviewApi.claimItem(row.id,row.revision,operationId('review-claim-item'));notifications.success('已领取该审核数据');router.push(`/admin/review/${item.id}`)}catch(e){error.value=e.message;notifications.error(e.message)}}
function toggle(row){if(row.status!=='REVIEW_PENDING')return;const next=new Set(selected.value);next.has(row.id)?next.delete(row.id):next.add(row.id);selected.value=next}
async function batchApprove(){if(!selected.value.size||!confirm(`确认批量通过 ${selected.value.size} 条审核数据？`))return;try{const commands=rows.value.filter(r=>selected.value.has(r.id)).map(r=>({itemId:r.id,expectedRevision:r.revision,text:null}));const result=await reviewApi.batchApprove(commands,operationId('review-batch-approve'));notice.value=`批量通过完成：成功 ${result.filter(r=>r.success).length}，冲突 ${result.filter(r=>!r.success).length}`;notifications.success(notice.value);selected.value=new Set();await load()}catch(e){error.value=e.message;notifications.error(e.message)}}
async function assign(row){const reviewerId=prompt('请输入审核员用户 ID');if(!reviewerId)return;try{await reviewApi.assign(row.id,reviewerId,row.revision,operationId('review-assign'));notifications.success('审核数据已分配');await load()}catch(e){error.value=e.message;notifications.error(e.message)}}
function changePage(value){page.value=value;selected.value=new Set();load()}
onMounted(load)
</script>
<template>
  <section class="admin-page">
    <PageActions title="任务审核池" description="已提交数据需先领取或分配，进入待审核后才能作出决定。">
      <router-link class="button-secondary" to="/admin/review">返回选择任务</router-link>
      <button class="button-secondary" @click="load">刷新</button>
      <button v-if="isReviewer" class="button-primary" @click="claim">领取一条</button>
    </PageActions>
    <div class="business-card">
      <div class="business-inline">
        <template v-if="isReviewer">
          <label>批量领取数量 <input v-model.number="count" type="number" min="1" max="100" /></label>
          <button class="button-secondary" @click="claimBatch">批量领取</button>
        </template>
        <button v-if="isAdmin" class="button-secondary" :disabled="!selected.size" @click="batchApprove">批量通过</button>
      </div>
      <p v-if="notice" class="business-success">{{notice}}</p>
      <AsyncState :loading="loading" :error="error" :empty="!rows.length" empty-text="当前任务没有已提交或待审核数据" @retry="load">
        <div class="business-table-wrap">
          <table class="business-table">
            <thead><tr><th v-if="isAdmin">选择</th><th>条目</th><th>采集员</th><th>状态</th><th>文本</th><th>时长</th><th>操作</th></tr></thead>
            <tbody><tr v-for="r in rows" :key="r.id">
              <td v-if="isAdmin"><input type="checkbox" :disabled="r.status!=='REVIEW_PENDING'" :checked="selected.has(r.id)" @change="toggle(r)" /></td>
              <td>{{r.itemCode}}</td>
              <td>{{r.collectorName||'未设置姓名'}}<small>{{r.collectorId||'-'}}</small></td>
              <td>{{r.status==='SUBMITTED'?'已提交':'待审核'}}</td>
              <td>{{r.hasText?'有':'无'}}</td>
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
