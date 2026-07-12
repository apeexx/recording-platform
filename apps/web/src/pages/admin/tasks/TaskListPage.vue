<script setup>
import { onMounted, ref } from 'vue'
import AsyncState from '../../../components/admin/AsyncState.vue'; import PageActions from '../../../components/admin/PageActions.vue'
import PaginationControls from '../../../components/admin/PaginationControls.vue'
import { taskApi } from '../../../lib/taskApi.js'; import { operationId } from '../../../lib/apiUtils.js'
const rows=ref([]),loading=ref(false),error=ref(''),page=ref(0),total=ref(0)
async function load(){loading.value=true;error.value='';try{const result=await taskApi.list(page.value,20);rows.value=result.items||[];total.value=result.total||0}catch(e){error.value=e.message}finally{loading.value=false}}
function changePage(value){page.value=value;load()}
async function transition(row,action){if(['end'].includes(action)&&!confirm(`确认结束任务“${row.name}”？`))return;try{await taskApi.transition(row.id,action,operationId(`task-${action}`));await load()}catch(e){error.value=e.message}}
onMounted(load)
</script>
<template><section class="admin-page"><PageActions title="任务管理" description="发布后结构修改会创建不可变的新版本。"><router-link class="button-primary" to="/admin/tasks/new">创建任务</router-link></PageActions><div class="business-card"><AsyncState :loading="loading" :error="error" :empty="!rows.length" empty-text="尚未创建任务" @retry="load"><div class="business-table-wrap"><table class="business-table"><thead><tr><th>任务编号</th><th>任务名称</th><th>版本</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="row in rows" :key="row.id"><td>{{ row.taskCode }}</td><td><router-link :to="`/admin/tasks/${row.id}`">{{ row.name }}</router-link></td><td>v{{ row.currentVersionNumber }}</td><td>{{ row.lifecycle }}</td><td><button v-if="row.lifecycle==='DRAFT'" class="button-link" @click="transition(row,'publish')">发布</button><button v-if="row.lifecycle==='RUNNING'" class="button-link" @click="transition(row,'pause')">暂停</button><button v-if="row.lifecycle==='PAUSED'" class="button-link" @click="transition(row,'resume')">恢复</button><button v-if="row.lifecycle!=='ENDED'" class="button-link is-danger" @click="transition(row,'end')">结束</button></td></tr></tbody></table></div><PaginationControls :page="page" :total="total" :size="20" @change="changePage"/></AsyncState></div></section></template>
