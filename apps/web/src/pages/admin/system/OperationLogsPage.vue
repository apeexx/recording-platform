<script setup>
import { onMounted, ref } from 'vue'; import PageActions from '../../../components/admin/PageActions.vue'; import AsyncState from '../../../components/admin/AsyncState.vue'; import { reportApi } from '../../../lib/reportApi.js'
const props=defineProps({itemId:String}),rows=ref([]),loading=ref(false),error=ref(''),page=ref(0)
async function load(){loading.value=true;try{const request=props.itemId?reportApi.itemOperations(props.itemId,{page:page.value,size:50}):reportApi.operations({page:page.value,size:50});rows.value=(await request).items||[]}catch(e){error.value=e.message}finally{loading.value=false}}
onMounted(load)
</script>
<template><section class="admin-page"><PageActions :title="itemId?'条目操作记录':'操作记录'" description="固定显示东八区时间、操作人和操作内容。"><button class="button-secondary" @click="load">刷新</button></PageActions><div class="business-card"><AsyncState :loading="loading" :error="error" :empty="!rows.length" @retry="load"><div class="business-table-wrap"><table class="business-table"><thead><tr><th>操作时间</th><th>操作人</th><th>操作内容</th></tr></thead><tbody><tr v-for="(r,i) in rows" :key="`${r.utcTime||r.time}-${i}`"><td>{{r.time}}</td><td>{{r.operator}}</td><td>{{r.content}}</td></tr></tbody></table></div></AsyncState></div></section></template>
