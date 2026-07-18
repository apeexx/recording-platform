<script setup>
import { onMounted, ref } from 'vue'
import PageActions from '../../../components/admin/PageActions.vue'
import AsyncState from '../../../components/admin/AsyncState.vue'
import { reviewApi } from '../../../lib/reviewApi.js'
const rows=ref([]),loading=ref(false),error=ref('')
async function load(){loading.value=true;error.value='';try{rows.value=await reviewApi.tasks()}catch(e){error.value=e.message}finally{loading.value=false}}
onMounted(load)
</script>
<template><section class="admin-page"><PageActions title="选择审核任务" description="先选择任务，再进入该任务独立的审核池。"><button class="button-secondary" @click="load">刷新</button></PageActions><AsyncState :loading="loading" :error="error" :empty="!rows.length" empty-text="当前没有待审核任务" @retry="load"><div class="business-grid"><router-link v-for="row in rows" :key="row.taskId" class="business-card review-task-card" :to="`/admin/review/tasks/${row.taskId}`"><span class="status-pill">{{ row.pendingCount }} 条待审核</span><h3>{{ row.taskName }}</h3><p>{{ row.taskCode }}</p><strong>进入审核池 →</strong></router-link></div></AsyncState></section></template>
