import AdminLayout from '../layouts/AdminLayout.vue'

export const adminRoutes = {
  path: '/admin',
  component: AdminLayout,
  redirect: '/admin/dashboard',
  meta: { requiresAuth: true },
  children: [
    { path: 'dashboard', component: () => import('../pages/admin/dashboard/AdminDashboard.vue'), meta: { title: '工作台', roles: ['ADMIN'] } },
	{ path: 'tasks', component: () => import('../pages/admin/tasks/TaskListPage.vue'), meta: { title: '任务管理', roles: ['ADMIN'] } },
    { path: 'pool', component: () => import('../pages/admin/tasks/TaskPoolPage.vue'), meta: { title: '任务数据池', roles: ['ADMIN'] } },
    { path: 'tasks/new', component: () => import('../pages/admin/tasks/TaskEditorPage.vue'), meta: { title: '创建任务', roles: ['ADMIN'] } },
    { path: 'tasks/:id', component: () => import('../pages/admin/tasks/TaskDetailPage.vue'), meta: { title: '任务详情', roles: ['ADMIN'] } },
    { path: 'tasks/:id/edit', component: () => import('../pages/admin/tasks/TaskEditorPage.vue'), meta: { title: '编辑任务版本', roles: ['ADMIN'] } },
    { path: 'tasks/:id/permissions', component: () => import('../pages/admin/tasks/TaskPermissionsPage.vue'), meta: { title: '采集权限', roles: ['ADMIN'] } },
    { path: 'permissions', component: () => import('../pages/admin/tasks/TaskPermissionsOverviewPage.vue'), meta: { title: '采集权限', roles: ['ADMIN'] } },
	{ path: 'review', component: () => import('../pages/admin/review/ReviewTaskSelectPage.vue'), meta: { title: '选择审核任务', roles: ['ADMIN', 'REVIEWER'] } },
	{ path: 'review/tasks/:taskId', component: () => import('../pages/admin/review/ReviewQueuePage.vue'), meta: { title: '任务审核池', roles: ['ADMIN', 'REVIEWER'] } },
    { path: 'review/:itemId', component: () => import('../pages/admin/review/ReviewWorkbenchPage.vue'), meta: { title: '审核工作台', roles: ['ADMIN', 'REVIEWER'] } },
    { path: 'reports/tasks', component: () => import('../pages/admin/reports/TaskStatisticsPage.vue'), meta: { title: '任务统计', roles: ['ADMIN'] } },
    { path: 'reports/collectors', component: () => import('../pages/admin/reports/CollectorStatisticsPage.vue'), meta: { title: '采集员统计', roles: ['ADMIN'] } },
    { path: 'reports/reviewers', component: () => import('../pages/admin/reports/ReviewerStatisticsPage.vue'), meta: { title: '审核统计', roles: ['ADMIN', 'REVIEWER'] } },
    { path: 'voice-generation/workbench', component: () => import('../pages/admin/voice-generation/VoiceGenerationWorkbenchPage.vue'), meta: { title: '语音生成工作台', roles: ['ADMIN'] } },
    { path: 'voice-generation/config', component: () => import('../pages/admin/voice-generation/VoiceConfigPage.vue'), meta: { title: '声音配置', roles: ['ADMIN'] } },
    { path: 'voice-generation/records', component: () => import('../pages/admin/voice-generation/VoiceGenerationRecordsPage.vue'), meta: { title: '生成记录', roles: ['ADMIN'] } },
    { path: 'system/users', component: () => import('../pages/admin/system/UsersPage.vue'), meta: { title: '用户管理', roles: ['ADMIN'] } },
    { path: 'system/logs', component: () => import('../pages/admin/system/OperationLogsPage.vue'), meta: { title: '操作记录', roles: ['ADMIN'] } },
    { path: 'items/:itemId/operations', component: () => import('../pages/admin/system/ItemOperationsPage.vue'), meta: { title: '条目操作记录', roles: ['ADMIN', 'REVIEWER'] } },
    { path: 'account', component: () => import('../pages/admin/system/AccountPage.vue'), meta: { title: '个人账号', roles: ['ADMIN', 'REVIEWER'] } }
  ]
}
