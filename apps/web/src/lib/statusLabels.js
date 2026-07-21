const labels = {
  task: { DRAFT: '草稿', RUNNING: '进行中', PAUSED: '已暂停', ENDED: '已结束' },
  item: { AVAILABLE: '待领取', RECORDING_PENDING: '待录制', REWORK_PENDING: '待返修', SUBMITTED: '已提交', REVIEW_PENDING: '待审核', COMPLETED: '已完成', DISCARDED: '已废弃', AI_PROCESSING: '处理中' },
  grant: { ACTIVE: '已授权', REVOKED: '已撤销' },
  access: { PENDING: '待审批', APPROVED: '已通过', REJECTED: '已驳回' },
  import: { PENDING: '等待处理', PROCESSING: '导入中', SUCCESS: '导入成功', PARTIAL_SUCCESS: '部分成功', FAILED: '导入失败' },
  user: { ACTIVE: '正常', DISABLED: '已停用' },
  role: { ADMIN: '管理员', REVIEWER: '审核员', COLLECTOR: '采集员' }
}

export function statusLabel(domain, value) {
  if (value == null || value === '') return '-'
  return labels[domain]?.[value] || `未知状态（${value}）`
}
