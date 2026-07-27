import { beforeEach, describe, expect, it, vi } from 'vitest'
vi.mock('../lib/httpClient.js', () => ({ httpRequest: vi.fn() }))
import { httpRequest } from '../lib/httpClient.js'
import { reportApi } from '../lib/reportApi.js'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

describe('统计与操作记录 API', () => {
  beforeEach(() => vi.clearAllMocks())
  it('保留分页、用户和 UTC 时间过滤参数', async () => {
    httpRequest.mockResolvedValue({ items: [] })
    await reportApi.collectors({ userId: 'u1', from: '2026-07-01T00:00:00Z', to: '2026-07-12T00:00:00Z' })
    await reportApi.operations({ page: 1, size: 50 })
    expect(httpRequest.mock.calls[0][0]).toContain('/api/reports/collectors?')
    expect(httpRequest.mock.calls[0][0]).toContain('userId=u1')
    expect(httpRequest).toHaveBeenNthCalledWith(2, '/api/operations?page=1&size=50')
  })

  it('任务人员排名保留日期、排序和分页参数', async () => {
    httpRequest.mockResolvedValue({ items: [] })
    await reportApi.taskCollectors('task-1', {
      fromDate: '2026-07-01', toDate: '2026-07-27',
      sortBy: 'referenceAudioDurationMillis', page: 1, size: 20
    })
    expect(httpRequest).toHaveBeenCalledWith(
      '/api/reports/tasks/task-1/collectors?fromDate=2026-07-01&toDate=2026-07-27&sortBy=referenceAudioDurationMillis&page=1&size=20'
    )
  })

  it('三个统计页使用人员选择器、任务和日期筛选并展示分组指标', () => {
    const task = readFileSync(join(process.cwd(), 'src/pages/admin/reports/TaskStatisticsPage.vue'), 'utf8')
    const collector = readFileSync(join(process.cwd(), 'src/pages/admin/reports/CollectorStatisticsPage.vue'), 'utf8')
    const reviewer = readFileSync(join(process.cwd(), 'src/pages/admin/reports/ReviewerStatisticsPage.vue'), 'utf8')
    const cards = readFileSync(join(process.cwd(), 'src/components/admin/WorkSummaryCards.vue'), 'utf8')
    expect(task).toContain('采集员排名')
    expect(task).toContain('sortBy')
    expect(task).toContain('fromDate')
    expect(task).toContain('toDate')
    expect(task).toContain('collectorId')
    expect(task).toContain('TaskSearchSelect')
    expect(collector).toContain('UserSearchSelect')
    expect(collector).toContain('TaskSearchSelect')
    expect(collector).not.toContain('请输入采集员用户 ID')
    expect(reviewer).toContain('UserSearchSelect')
    expect(reviewer).toContain('TaskSearchSelect')
    expect(reviewer).not.toContain('请输入审核员用户 ID')
    expect(cards).toContain('工作量')
    expect(cards).toContain('最终结果')
    expect(cards).toContain('参考来源')
  })
})
