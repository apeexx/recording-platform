import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

const read = path => readFileSync(join(process.cwd(), 'src', path), 'utf8')

describe('生产页面复杂规则说明覆盖', () => {
  it('覆盖大屏、任务、审核、统计、系统和语音生成', () => {
    const coverage = {
      'pages/admin/dashboard/AdminDashboard.vue': ['今日首次提交说明', '近七日趋势说明', '条目状态分布说明', '任务完成排行说明'],
      'pages/admin/tasks/TaskEditorPage.vue': ['最终成果说明', '参考类型说明', '录音规格说明', '人工审核说明'],
      'pages/admin/tasks/TaskDetailPage.vue': ['参考文字说明', 'CSV 导入说明', '数据池说明', 'CSV 导出说明'],
      'pages/admin/tasks/TaskPoolPage.vue': ['数据池导出说明', '批量操作说明'],
      'pages/admin/tasks/TaskItemDetailPage.vue': ['条目参考源说明', '条目状态说明', '当前与最终结果说明'],
      'pages/admin/tasks/TaskPermissionsPage.vue': ['授权申请说明', '有效授权说明'],
      'pages/admin/review/ReviewQueuePage.vue': ['审核池筛选说明', '审核批量处理说明'],
      'pages/admin/review/ReviewWorkbenchPage.vue': ['原始采集结果说明', 'AI 候选说明', '审核最终答案说明', '审核结论说明'],
      'components/admin/StageSummaryPanel.vue': ['提交统计', '完成统计'],
      'components/admin/SubmissionHourDistribution.vue': ['小时分布说明'],
      'pages/admin/system/UsersPage.vue': ['用户类型角色状态说明'],
      'pages/admin/system/InvitationCodesPage.vue': ['邀请码使用规则说明', '最大使用次数说明'],
      'pages/admin/system/OperationLogsPage.vue': ['操作记录说明'],
      'pages/admin/voice-generation/VoiceGenerationWorkbenchPage.vue': ['语音生成模式说明', '克隆母带限制说明', '新音色 ID 说明', '合成文本说明', '语速说明', '音量说明', '语调说明'],
      'pages/admin/voice-generation/VoiceConfigPage.vue': ['默认音色配置说明', '默认音色 ID 说明'],
      'pages/admin/voice-generation/VoiceGenerationRecordsPage.vue': ['生成记录状态说明'],
    }
    for (const [path, labels] of Object.entries(coverage)) {
      const source = read(path)
      expect(source, path).toContain('HelpPopover')
      for (const label of labels) expect(source, `${path}: ${label}`).toContain(label)
    }
  })
})
