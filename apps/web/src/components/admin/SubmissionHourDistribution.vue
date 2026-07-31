<script setup>
import { computed } from 'vue'
const props = defineProps({ values: { type: Array, default: () => [] } })
const normalized = computed(() => Array.from({ length: 24 }, (_, index) => {
  const hour = (index + 4) % 24
  const found = props.values.find((item) => Number(item.hour) === hour)
  return { hour, count: Number(found?.count) || 0 }
}))
const maximum = computed(() => Math.max(1, ...normalized.value.map((item) => item.count)))
</script>

<template>
  <section class="business-card hour-card">
    <div class="business-heading"><div><h3>24 小时首次提交分布</h3><p>按真实北京时间小时展示，顺序为业务日 04:00 至次日 04:00，只统计首次提交条数。</p></div></div>
    <div class="hour-chart" role="img" aria-label="24 小时首次提交分布柱状图">
      <div v-for="item in normalized" :key="item.hour" class="hour-column" :title="`${item.hour}:00 · ${item.count} 条`">
        <span class="hour-count">{{ item.count || '' }}</span>
        <i :style="{ height: `${Math.max(item.count ? 8 : 2, item.count / maximum * 100)}%` }"></i>
        <small>{{ String(item.hour).padStart(2, '0') }}</small>
      </div>
    </div>
  </section>
</template>

<style scoped>
.hour-card{padding:20px}.hour-chart{height:190px;display:grid;grid-template-columns:repeat(24,minmax(18px,1fr));align-items:end;gap:5px;padding-top:25px;overflow-x:auto}.hour-column{height:150px;min-width:18px;display:grid;grid-template-rows:18px 1fr 20px;align-items:end;text-align:center}.hour-count{font-size:10px;color:var(--muted-foreground)}.hour-column i{display:block;width:100%;min-height:2px;border-radius:4px 4px 1px 1px;background:color-mix(in srgb,var(--primary) 78%,white);transition:height .2s ease}.hour-column small{padding-top:5px;color:var(--muted-foreground);font-size:10px}@media(max-width:720px){.hour-chart{grid-template-columns:repeat(24,22px)}}
</style>
