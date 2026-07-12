<script setup>
const props=defineProps({page:{type:Number,default:0},size:{type:Number,default:20},total:{type:Number,default:0}})
const emit=defineEmits(['change'])
function go(value){const max=Math.max(Math.ceil(props.total/props.size)-1,0);emit('change',Math.min(Math.max(value,0),max))}
</script>
<template><nav v-if="total>size" class="pagination" aria-label="分页"><button class="button-secondary" :disabled="page===0" @click="go(page-1)">上一页</button><span>第 {{page+1}} / {{Math.ceil(total/size)}} 页，共 {{total}} 条</span><button class="button-secondary" :disabled="(page+1)*size>=total" @click="go(page+1)">下一页</button></nav></template>
