export const adminSidebar = [
  {
    key: 'dashboard',
    title: '工作台',
    path: '/admin/dashboard',
    icon: 'dashboard'
  },
  {
    key: 'basic',
    title: '基础设置',
    icon: 'settings',
    children: [
      { key: 'language-types', title: '语言类型', path: '/admin/basic/language-types' },
      { key: 'split-rules', title: '切分规则', path: '/admin/basic/split-rules' },
      { key: 'announcements', title: '系统公告', path: '/admin/basic/announcements' }
    ]
  },
  {
    key: 'text',
    title: '文本处理',
    icon: 'text',
    children: [
      { key: 'text-import', title: '文本导入', path: '/admin/text/import' },
      { key: 'text-list', title: '文本列表', path: '/admin/text/list' },
      { key: 'text-batches', title: '文本批次', path: '/admin/text/batches' }
    ]
  },
  {
    key: 'tasks',
    title: '录音任务',
    icon: 'task',
    children: [
      { key: 'task-batches', title: '任务批次', path: '/admin/tasks/batches' },
      { key: 'task-publish', title: '任务发布', path: '/admin/tasks/publish' },
      { key: 'task-claims', title: '领取情况', path: '/admin/tasks/claims' },
      { key: 'task-recycle', title: '任务回收', path: '/admin/tasks/recycle' }
    ]
  },
  {
    key: 'review',
    title: '录音审核',
    icon: 'review',
    children: [
      { key: 'review-overview', title: '审核总览', path: '/admin/review/overview' },
      { key: 'first-review', title: '一审管理', path: '/admin/review/first' },
      { key: 'second-review', title: '二审管理', path: '/admin/review/second' },
      { key: 'rejected-records', title: '驳回记录', path: '/admin/review/rejected' }
    ]
  },
  {
    key: 'results',
    title: '录音结果',
    icon: 'result',
    children: [
      { key: 'approved-results', title: '通过结果', path: '/admin/results/approved' },
      { key: 'audio-files', title: '音频文件', path: '/admin/results/files' },
      { key: 'result-export', title: '结果导出', path: '/admin/results/export' }
    ]
  },
  {
    key: 'reports',
    title: '工作报表',
    icon: 'report',
    children: [
      { key: 'project-statistics', title: '项目统计', path: '/admin/reports/projects' },
      { key: 'recorder-statistics', title: '录音员统计', path: '/admin/reports/recorders' },
      { key: 'reviewer-statistics', title: '审核员统计', path: '/admin/reports/reviewers' }
    ]
  },
  {
    key: 'voice-generation',
    title: '语音生成',
    icon: 'voice',
    owner: 'collaborator',
    status: 'active',
    children: [
      {
        key: 'voice-generation-workbench',
        title: '语音生成工作台',
        path: '/admin/voice-generation/workbench'
      },
      { key: 'voice-config', title: '声音配置', path: '/admin/voice-generation/config' },
      {
        key: 'voice-generation-records',
        title: '生成记录',
        path: '/admin/voice-generation/records'
      }
    ]
  },
  {
    key: 'system',
    title: '系统管理',
    icon: 'system',
    children: [
      { key: 'users', title: '用户管理', path: '/admin/system/users' },
      { key: 'roles', title: '角色权限', path: '/admin/system/roles' },
      { key: 'operation-logs', title: '操作日志', path: '/admin/system/logs' },
      { key: 'system-settings', title: '系统设置', path: '/admin/system/settings' }
    ]
  }
]

export function findAdminSidebarGroupKeyByPath(path) {
  for (const item of adminSidebar) {
    if (!Array.isArray(item.children)) {
      continue
    }

    if (item.children.some((child) => child.path === path)) {
      return item.key
    }
  }

  return null
}
