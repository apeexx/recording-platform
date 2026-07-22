<script setup>
import { computed, onMounted, ref } from 'vue'
import AsyncState from '../../../components/admin/AsyncState.vue'
import PageActions from '../../../components/admin/PageActions.vue'
import PaginationControls from '../../../components/admin/PaginationControls.vue'
import BaseSelect from '../../../components/form/BaseSelect.vue'
import { taskApi } from '../../../lib/taskApi.js'
import { operationId } from '../../../lib/apiUtils.js'
import { statusLabel } from '../../../lib/statusLabels.js'
import { useNotifications } from '../../../composables/useNotifications.js'

const notifications=useNotifications(),tasks=ref([]),taskId=ref(''),rows=ref([]),page=ref(0),total=ref(0),loading=ref(false),error=ref(''),selected=ref(new Set()),notice=ref('')
const taskOptions=computed(()=>tasks.value.map(task=>({value:task.id,label:task.name})))
async function init(){try{tasks.value=(await taskApi.list(0,100)).items||[]}catch(e){error.value=e.message}}
async function load(){if(!taskId.value)return;loading.value=true;error.value='';try{const result=await taskApi.items(taskId.value,page.value,20);rows.value=result.items||[];total.value=result.total||0}catch(e){error.value=e.message}finally{loading.value=false}}
function choose(){page.value=0;selected.value=new Set();load()}
function changePage(value){page.value=value;selected.value=new Set();load()}
function toggle(row){const next=new Set(selected.value);next.has(row.id)?next.delete(row.id):next.add(row.id);selected.value=next}
async function batch(action){if(!selected.value.size||!confirm(`确认批量${action==='release'?'释放':action==='discard'?'废弃':'恢复'} ${selected.value.size} 条数据？`))return;const items=rows.value.filter(row=>selected.value.has(row.id)).map(row=>({itemId:row.id,expectedRevision:row.revision}));try{const result=await taskApi.batchAction(action,items,operationId(`pool-${action}`));notice.value=`成功 ${result.filter(row=>row.success).length}，冲突 ${result.filter(row=>!row.success).length}`;notifications.success(notice.value);selected.value=new Set();await load()}catch(e){error.value=e.message;notifications.error(e.message)}}
onMounted(init)
</script>
<template><section class="admin-page"><PageActions title="任务数据池" description="选择任务后分页查看池数据，批量操作按条目返回成功或冲突。"/><div class="business-card"><div class="business-inline"><BaseSelect v-model="taskId" :options="taskOptions" placeholder="请选择任务" aria-label="选择任务" @update:model-value="choose"/><button class="button-secondary" :disabled="!taskId" @click="load">刷新</button><button class="button-secondary" :disabled="!selected.size" @click="batch('release')">批量释放</button><button class="button-secondary is-danger" :disabled="!selected.size" @click="batch('discard')">批量废弃</button><button class="button-secondary" :disabled="!selected.size" @click="batch('restore')">批量恢复</button></div><p v-if="notice" class="business-success">{{notice}}</p><AsyncState :loading="loading" :error="error" :empty="!taskId||!rows.length" :empty-text="taskId?'当前任务池为空':'请选择任务'" @retry="load"><div class="business-table-wrap"><table class="business-table"><thead><tr><th>选择</th><th>条目编号</th><th>状态</th><th>采集员</th><th>审核员</th><th>修订</th></tr></thead><tbody><tr v-for="row in rows" :key="row.id"><td><input type="checkbox" :checked="selected.has(row.id)" @change="toggle(row)"/></td><td><router-link :to="`/admin/items/${row.id}/operations`">{{row.itemCode}}</router-link></td><td>{{statusLabel('item',row.status)}}</td><td>{{row.collectorId||'-'}}</td><td>{{row.reviewerId||'-'}}</td><td>{{row.revision}}</td></tr></tbody></table></div><PaginationControls :page="page" :size="20" :total="total" @change="changePage"/></AsyncState></div></section></template>
