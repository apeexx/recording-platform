<script setup>
const entries = [
  { title: '任务与数据池', path: '/admin/tasks', summary: '创建任务、版本、导入和池数据' },
  { title: '审核任务', path: '/admin/review', summary: '选择任务后处理待审核数据' },
  { title: '操作记录', path: '/admin/system/logs', summary: '查看东八区操作时间、操作人和内容' }
]
</script>
<template><section class="admin-page"><div class="admin-dashboard-hero"><span class="status-pill">业务工作台</span><h2>录音任务管理</h2><p>从平台和任务开始配置采集流程，再进入数据池、权限和审核环节。工作台不展示未经接口计算的模拟数字。</p></div><div class="admin-page-card"><h3>常用入口</h3><div class="admin-entry-list admin-entry-list--rich"><router-link v-for="entry in entries" :key="entry.path" :to="entry.path"><strong>{{entry.title}}</strong><span>{{entry.summary}}</span></router-link></div></div><div class="admin-page-card"><h3>当前交付边界</h3><ol class="admin-flow-list"><li>任务版本发布后不可原地覆盖，新结构生成下一版本。</li><li>录音、审核、返修、释放与废弃均由后端 revision 和幂等校验保护。</li><li>AI 转写、AI 自动审核和甲方平台自动取数尚未启用。</li></ol></div></section></template>
