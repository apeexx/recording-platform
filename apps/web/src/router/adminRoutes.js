import AdminLayout from '../layouts/AdminLayout.vue'

const placeholder = () => import('../pages/admin/AdminPlaceholderPage.vue')

export const adminRoutes = {
  path: '/admin',
  component: AdminLayout,
  redirect: '/admin/dashboard',
  meta: { requiresAuth: true },
  children: [
    { path: 'dashboard', component: () => import('../pages/admin/dashboard/AdminDashboard.vue'), meta: { title: '工作台', roles: ['ADMIN'] } },
    { path: 'platforms', component: placeholder, meta: { title: '平台管理', roles: ['ADMIN'] } },
    { path: 'tasks', component: placeholder, meta: { title: '任务与数据池', roles: ['ADMIN'] } },
    { path: 'permissions', component: placeholder, meta: { title: '采集权限', roles: ['ADMIN'] } },
    { path: 'review/queue', component: placeholder, meta: { title: '审核池', roles: ['ADMIN', 'REVIEWER'] } },
    { path: 'reports/tasks', component: () => import('../pages/admin/reports/ProjectStatisticsPage.vue'), meta: { title: '任务统计', roles: ['ADMIN'] } },
    { path: 'reports/collectors', component: () => import('../pages/admin/reports/RecorderStatisticsPage.vue'), meta: { title: '采集员统计', roles: ['ADMIN'] } },
    { path: 'reports/reviewers', component: () => import('../pages/admin/reports/ReviewerStatisticsPage.vue'), meta: { title: '审核统计', roles: ['ADMIN', 'REVIEWER'] } },
    { path: 'voice-generation/workbench', component: () => import('../pages/admin/voice-generation/VoiceGenerationWorkbenchPage.vue'), meta: { title: '语音生成工作台', roles: ['ADMIN'] } },
    { path: 'voice-generation/config', component: () => import('../pages/admin/voice-generation/VoiceConfigPage.vue'), meta: { title: '声音配置', roles: ['ADMIN'] } },
    { path: 'voice-generation/records', component: () => import('../pages/admin/voice-generation/VoiceGenerationRecordsPage.vue'), meta: { title: '生成记录', roles: ['ADMIN'] } },
    { path: 'system/users', component: () => import('../pages/admin/system/UsersPage.vue'), meta: { title: '用户管理', roles: ['ADMIN'] } },
    { path: 'system/logs', component: () => import('../pages/admin/system/OperationLogsPage.vue'), meta: { title: '操作记录', roles: ['ADMIN'] } },
    { path: 'account', component: () => import('../pages/admin/system/AccountPage.vue'), meta: { title: '个人账号', roles: ['ADMIN', 'REVIEWER'] } }
  ]
}
