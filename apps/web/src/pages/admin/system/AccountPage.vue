<script setup>
import { useAdminSession } from '../../../composables/useAdminSession.js'
const session = useAdminSession()
</script>
<template><section class="admin-page"><div class="admin-page-card"><h2>个人账号</h2><p>姓名：{{ session.user.value?.name }}</p><p>账号：{{ session.user.value?.username }}</p><p>角色：{{ session.user.value?.role === 'REVIEWER' ? '审核员' : '管理员' }}</p></div></section></template>
