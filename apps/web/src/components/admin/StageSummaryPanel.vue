<script setup>
defineProps({ summary: { type: Object, default: () => ({}) } })
const seconds = (value) => `${((Number(value) || 0) / 1000).toFixed(1)} 秒`
const stages = [
  ['submissions', '提交统计', '按首次提交时间归属'],
  ['completions', '完成统计', '按首次完成时间归属'],
]
</script>

<template>
  <div class="stage-summary-grid">
    <section v-for="[key,title,note] in stages" :key="key" class="business-card stage-panel">
      <div class="stage-heading"><div><h3>{{ title }}</h3><p>{{ note }}</p></div><strong>{{ summary?.[key]?.count || 0 }} 条</strong></div>
      <dl>
        <div><dt>最终录音时长</dt><dd>{{ seconds(summary?.[key]?.recordingDurationMillis) }}</dd></div>
        <div><dt>参考音频时长</dt><dd>{{ seconds(summary?.[key]?.referenceAudioDurationMillis) }}</dd></div>
        <div><dt>参考视频时长</dt><dd>{{ seconds(summary?.[key]?.referenceVideoDurationMillis) }}</dd></div>
      </dl>
    </section>
  </div>
</template>

<style scoped>
.stage-summary-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px}.stage-panel{padding:20px}.stage-heading{display:flex;justify-content:space-between;align-items:flex-start;gap:16px}.stage-heading h3{margin:0;font-size:17px}.stage-heading p{margin:5px 0 0;color:var(--muted-foreground);font-size:13px}.stage-heading strong{color:var(--primary);font-size:25px;white-space:nowrap}.stage-panel dl{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px;margin:20px 0 0}.stage-panel dl div{padding-top:14px;border-top:1px solid var(--border)}dt{color:var(--muted-foreground);font-size:12px}dd{margin:7px 0 0;font-weight:700;font-variant-numeric:tabular-nums}@media(max-width:800px){.stage-summary-grid{grid-template-columns:1fr}.stage-panel dl{grid-template-columns:1fr}.stage-panel dl div{display:flex;justify-content:space-between;gap:12px}}
</style>
