<script setup>
defineProps({ summary: { type: Object, default: () => ({}) } })
const seconds = value => `${Math.round((Number(value) || 0) / 1000)} 秒`
</script>
<template>
  <div class="report-metric-groups">
    <section><h3>工作量</h3><div class="summary-grid"><div><span>提交条数</span><strong>{{summary.submissionCount??summary.cumulativeSubmissions??0}}</strong></div><div><span>完成条数</span><strong>{{summary.completedCount??summary.currentCompletedCount??0}}</strong></div></div></section>
    <section><h3>最终结果</h3><div class="summary-grid"><div><span>录音时长</span><strong>{{seconds(summary.recordingDurationMillis??summary.currentDurationMillis)}}</strong></div></div></section>
    <section><h3>参考来源</h3><div class="summary-grid"><div><span>参考音频时长</span><strong>{{seconds(summary.referenceAudioDurationMillis)}}</strong></div><div><span>参考视频时长</span><strong>{{seconds(summary.referenceVideoDurationMillis)}}</strong></div></div></section>
  </div>
</template>
<style scoped>
.report-metric-groups{display:grid;gap:18px;margin-top:20px}.report-metric-groups section{padding:18px;border:1px solid var(--border);border-radius:var(--radius);background:var(--card)}.report-metric-groups h3{margin:0 0 12px}.workflow-metrics{opacity:.88}
</style>
