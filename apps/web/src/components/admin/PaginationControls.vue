<script setup>
import { computed } from 'vue'
import BaseSelect from '../form/BaseSelect.vue'

const props=defineProps({
  page:{type:Number,default:0},
  size:{type:Number,default:20},
  total:{type:Number,default:0},
  numbered:{type:Boolean,default:false},
  pageSizes:{type:Array,default:()=>[]}
})
const emit=defineEmits(['change','size-change'])
const totalPages=computed(()=>Math.max(Math.ceil(props.total/props.size),1))
const sizeOptions=computed(()=>props.pageSizes.map(value=>({value,label:`${value} 条/页`})))

const pageItems=computed(()=>{
  const pages=totalPages.value
  if(pages<=7)return Array.from({length:pages},(_,index)=>({key:`page-${index}`,type:'page',value:index,label:String(index+1)}))
  const current=props.page+1
  let start=Math.max(2,current-2)
  let end=Math.min(pages-1,current+2)
  if(start===2)end=Math.min(pages-1,6)
  if(end===pages-1)start=Math.max(2,pages-5)
  const items=[{key:'page-0',type:'page',value:0,label:'1'}]
  if(start>2)items.push({key:'ellipsis-left',type:'ellipsis',label:'…'})
  for(let value=start;value<=end;value+=1)items.push({key:`page-${value-1}`,type:'page',value:value-1,label:String(value)})
  if(end<pages-1)items.push({key:'ellipsis-right',type:'ellipsis',label:'…'})
  items.push({key:`page-${pages-1}`,type:'page',value:pages-1,label:String(pages)})
  return items
})

function go(value){const max=totalPages.value-1;emit('change',Math.min(Math.max(value,0),max))}
function changeSize(value){const next=Number(value);if(next!==props.size&&props.pageSizes.includes(next))emit('size-change',next)}
</script>

<template>
  <nav v-if="numbered" class="pagination pagination-numbered" aria-label="分页">
    <BaseSelect class="pagination-size" :model-value="size" :options="sizeOptions" aria-label="每页条数" @update:model-value="changeSize" />
    <button class="pagination-arrow" :disabled="page===0" aria-label="上一页" @click="go(page-1)">‹</button>
    <template v-for="item in pageItems" :key="item.key">
      <button v-if="item.type==='page'" class="pagination-page" :class="{'is-active':item.value===page}" :data-page="item.value" :disabled="item.value===page" :aria-current="item.value===page?'page':undefined" @click="go(item.value)">{{ item.label }}</button>
      <span v-else class="pagination-ellipsis">{{ item.label }}</span>
    </template>
    <button class="pagination-arrow" :disabled="page+1>=totalPages" aria-label="下一页" @click="go(page+1)">›</button>
  </nav>
  <nav v-else-if="total>size" class="pagination" aria-label="分页"><button class="button-secondary" :disabled="page===0" @click="go(page-1)">上一页</button><span>第 {{page+1}} / {{Math.ceil(total/size)}} 页，共 {{total}} 条</span><button class="button-secondary" :disabled="(page+1)*size>=total" @click="go(page+1)">下一页</button></nav>
</template>
