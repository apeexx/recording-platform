export const adminSidebar = [
  { key: 'dashboard', title: '数据大屏', path: '/admin/dashboard', icon: 'dashboard', roles: ['ADMIN'] },
  {
    key: 'tasks', title: '任务管理', icon: 'task', roles: ['ADMIN'], children: [
	  { key: 'tasks', title: '任务管理', path: '/admin/tasks' },
      { key: 'task-pool', title: '任务数据池', path: '/admin/pool' },
      { key: 'permissions', title: '采集权限', path: '/admin/permissions' }
    ]
  },
  { key: 'review', title: '录音审核', icon: 'review', path: '/admin/review', activePrefixes: ['/admin/review/'], roles: ['ADMIN', 'REVIEWER'] },
  { key: 'reports', title: '工作统计', icon: 'report', path: '/admin/reports/collectors', activePrefixes: ['/admin/reports/'], roles: ['ADMIN'] },
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
      { key: 'invitations', title: '邀请码管理', path: '/admin/system/invitations' },
      { key: 'operation-logs', title: '操作记录', path: '/admin/system/logs' }
    ]
  },
  { key: 'account', title: '个人账号', path: '/admin/account', icon: 'account', roles: ['REVIEWER'] }
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
  return items.find((item) => item.children?.some((child) =>
    child.path === path || child.activePrefixes?.some(prefix => path.startsWith(prefix))
  ))?.key || null
}
