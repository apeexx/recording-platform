export const adminSidebar = [
  { key: 'dashboard', title: '工作台', path: '/admin/dashboard', icon: 'dashboard', roles: ['ADMIN'] },
  {
    key: 'tasks', title: '任务管理', icon: 'task', roles: ['ADMIN'], children: [
      { key: 'platforms', title: '平台管理', path: '/admin/platforms' },
      { key: 'tasks', title: '任务与数据池', path: '/admin/tasks' },
      { key: 'permissions', title: '采集权限', path: '/admin/permissions' }
    ]
  },
  {
    key: 'review', title: '录音审核', icon: 'review', roles: ['ADMIN', 'REVIEWER'], children: [
      { key: 'review-queue', title: '审核池', path: '/admin/review/queue' }
    ]
  },
  {
    key: 'reports', title: '工作统计', icon: 'report', roles: ['ADMIN', 'REVIEWER'], children: [
      { key: 'task-reports', title: '任务统计', path: '/admin/reports/tasks', roles: ['ADMIN'] },
      { key: 'collector-reports', title: '采集员统计', path: '/admin/reports/collectors', roles: ['ADMIN'] },
      { key: 'reviewer-reports', title: '审核统计', path: '/admin/reports/reviewers' }
    ]
  },
  {
    key: 'voice-generation', title: '语音生成', icon: 'voice', roles: ['ADMIN'], children: [
      { key: 'voice-workbench', title: '语音生成工作台', path: '/admin/voice-generation/workbench' },
      { key: 'voice-config', title: '声音配置', path: '/admin/voice-generation/config' },
      { key: 'voice-records', title: '生成记录', path: '/admin/voice-generation/records' }
    ]
  },
  {
    key: 'system', title: '系统管理', icon: 'system', roles: ['ADMIN'], children: [
      { key: 'users', title: '用户管理', path: '/admin/system/users' },
      { key: 'operation-logs', title: '操作记录', path: '/admin/system/logs' }
    ]
  },
  { key: 'account', title: '个人账号', path: '/admin/account', icon: 'system', roles: ['REVIEWER'] }
]

export function sidebarForRole(role) {
  return adminSidebar.flatMap((item) => {
    if (item.roles && !item.roles.includes(role)) return []
    const children = item.children?.filter((child) => !child.roles || child.roles.includes(role))
    if (item.children && !children.length) return []
    return [{ ...item, ...(children ? { children } : {}) }]
  })
}

export function findAdminSidebarGroupKeyByPath(path, items = adminSidebar) {
  return items.find((item) => item.children?.some((child) => child.path === path))?.key || null
}
