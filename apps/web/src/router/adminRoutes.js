import AdminLayout from '../layouts/AdminLayout.vue'

export const adminRoutes = {
  path: '/admin',
  component: AdminLayout,
  redirect: '/admin/dashboard',
  children: [
    {
      path: 'dashboard',
      name: 'admin-dashboard',
      component: () => import('../pages/admin/dashboard/AdminDashboard.vue'),
      meta: { title: '工作台' }
    },
    {
      path: 'basic/language-types',
      name: 'admin-language-types',
      component: () => import('../pages/admin/basic/LanguageTypesPage.vue'),
      meta: { title: '语言类型' }
    },
    {
      path: 'basic/split-rules',
      name: 'admin-split-rules',
      component: () => import('../pages/admin/basic/SplitRulesPage.vue'),
      meta: { title: '切分规则' }
    },
    {
      path: 'basic/announcements',
      name: 'admin-announcements',
      component: () => import('../pages/admin/basic/AnnouncementsPage.vue'),
      meta: { title: '系统公告' }
    },
    {
      path: 'text/import',
      name: 'admin-text-import',
      component: () => import('../pages/admin/text/TextImportPage.vue'),
      meta: { title: '文本导入' }
    },
    {
      path: 'text/list',
      name: 'admin-text-list',
      component: () => import('../pages/admin/text/TextListPage.vue'),
      meta: { title: '文本列表' }
    },
    {
      path: 'text/batches',
      name: 'admin-text-batches',
      component: () => import('../pages/admin/text/TextBatchesPage.vue'),
      meta: { title: '文本批次' }
    },
    {
      path: 'tasks/batches',
      name: 'admin-task-batches',
      component: () => import('../pages/admin/tasks/TaskBatchesPage.vue'),
      meta: { title: '任务批次' }
    },
    {
      path: 'tasks/publish',
      name: 'admin-task-publish',
      component: () => import('../pages/admin/tasks/TaskPublishPage.vue'),
      meta: { title: '任务发布' }
    },
    {
      path: 'tasks/claims',
      name: 'admin-task-claims',
      component: () => import('../pages/admin/tasks/TaskClaimsPage.vue'),
      meta: { title: '领取情况' }
    },
    {
      path: 'tasks/recycle',
      name: 'admin-task-recycle',
      component: () => import('../pages/admin/tasks/TaskRecyclePage.vue'),
      meta: { title: '任务回收' }
    },
    {
      path: 'review/overview',
      name: 'admin-review-overview',
      component: () => import('../pages/admin/review/ReviewOverviewPage.vue'),
      meta: { title: '审核总览' }
    },
    {
      path: 'review/first',
      name: 'admin-first-review',
      component: () => import('../pages/admin/review/FirstReviewPage.vue'),
      meta: { title: '一审管理' }
    },
    {
      path: 'review/second',
      name: 'admin-second-review',
      component: () => import('../pages/admin/review/SecondReviewPage.vue'),
      meta: { title: '二审管理' }
    },
    {
      path: 'review/rejected',
      name: 'admin-rejected-records',
      component: () => import('../pages/admin/review/RejectedRecordsPage.vue'),
      meta: { title: '驳回记录' }
    },
    {
      path: 'results/approved',
      name: 'admin-approved-results',
      component: () => import('../pages/admin/results/ApprovedResultsPage.vue'),
      meta: { title: '通过结果' }
    },
    {
      path: 'results/files',
      name: 'admin-audio-files',
      component: () => import('../pages/admin/results/AudioFilesPage.vue'),
      meta: { title: '音频文件' }
    },
    {
      path: 'results/export',
      name: 'admin-result-export',
      component: () => import('../pages/admin/results/ResultExportPage.vue'),
      meta: { title: '结果导出' }
    },
    {
      path: 'reports/projects',
      name: 'admin-project-statistics',
      component: () => import('../pages/admin/reports/ProjectStatisticsPage.vue'),
      meta: { title: '项目统计' }
    },
    {
      path: 'reports/recorders',
      name: 'admin-recorder-statistics',
      component: () => import('../pages/admin/reports/RecorderStatisticsPage.vue'),
      meta: { title: '录音员统计' }
    },
    {
      path: 'reports/reviewers',
      name: 'admin-reviewer-statistics',
      component: () => import('../pages/admin/reports/ReviewerStatisticsPage.vue'),
      meta: { title: '审核员统计' }
    },
    {
      path: 'voice-generation/workbench',
      name: 'admin-voice-generation-workbench',
      component: () =>
        import('../pages/admin/voice-generation/VoiceGenerationWorkbenchPage.vue'),
      meta: { title: '语音生成工作台' }
    },
    {
      path: 'voice-generation/config',
      name: 'admin-voice-config',
      component: () => import('../pages/admin/voice-generation/VoiceConfigPage.vue'),
      meta: { title: '声音配置' }
    },
    {
      path: 'voice-generation/records',
      name: 'admin-voice-generation-records',
      component: () =>
        import('../pages/admin/voice-generation/VoiceGenerationRecordsPage.vue'),
      meta: { title: '生成记录' }
    },
    {
      path: 'system/users',
      name: 'admin-users',
      component: () => import('../pages/admin/system/UsersPage.vue'),
      meta: { title: '用户管理' }
    },
    {
      path: 'system/roles',
      name: 'admin-roles',
      component: () => import('../pages/admin/system/RolesPage.vue'),
      meta: { title: '角色权限' }
    },
    {
      path: 'system/logs',
      name: 'admin-operation-logs',
      component: () => import('../pages/admin/system/OperationLogsPage.vue'),
      meta: { title: '操作日志' }
    },
    {
      path: 'system/settings',
      name: 'admin-system-settings',
      component: () => import('../pages/admin/system/SystemSettingsPage.vue'),
      meta: { title: '系统设置' }
    }
  ]
}
