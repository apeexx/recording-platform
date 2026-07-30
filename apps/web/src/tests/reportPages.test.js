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

  it('采集员任务下钻与完整明细保留日期和分页参数', async () => {
    httpRequest.mockResolvedValue({ items: [] })
    await reportApi.collectorTask('collector-1', 'task-1', {
      fromDate: '2026-07-01', toDate: '2026-07-27',
    })
    await reportApi.collectorTaskSubmissions('collector-1', 'task-1', {
      fromDate: '2026-07-01', toDate: '2026-07-27', page: 2, size: 20,
    })
    await reportApi.collectorTaskCompletions('collector-1', 'task-1', {
      fromDate: '2026-07-01', toDate: '2026-07-27', page: 1, size: 20,
    })
    expect(httpRequest).toHaveBeenNthCalledWith(
      1, '/api/reports/collectors/collector-1/tasks/task-1?fromDate=2026-07-01&toDate=2026-07-27'
    )
    expect(httpRequest).toHaveBeenNthCalledWith(
      2, '/api/reports/collectors/collector-1/tasks/task-1/submissions?fromDate=2026-07-01&toDate=2026-07-27&page=2&size=20'
    )
    expect(httpRequest).toHaveBeenNthCalledWith(
      3, '/api/reports/collectors/collector-1/tasks/task-1/completions?fromDate=2026-07-01&toDate=2026-07-27&page=1&size=20'
    )
    expect(reportApi.reviewers).toBeUndefined()
  })

  it('统计列表和独立详情展示双阶段、小时分布与两套明细', () => {
    const collector = readFileSync(join(process.cwd(), 'src/pages/admin/reports/CollectorStatisticsPage.vue'), 'utf8')
    const detail = readFileSync(join(process.cwd(), 'src/pages/admin/reports/CollectorStatisticsDetailPage.vue'), 'utf8')
    const cards = readFileSync(join(process.cwd(), 'src/components/admin/StageSummaryPanel.vue'), 'utf8')
    const hours = readFileSync(join(process.cwd(), 'src/components/admin/SubmissionHourDistribution.vue'), 'utf8')
    expect(collector).toContain('TaskSearchSelect')
    expect(collector).not.toContain('UserSearchSelect')
    expect(collector).toContain('今天')
    expect(collector).toContain('昨天')
    expect(collector).toContain('近 7 日')
    expect(collector).toContain('本月')
    expect(collector).toContain('全部')
    expect(collector).toContain('reportApi.taskCollectors')
    expect(collector).toContain('taskApi.exportItems')
    expect(collector).toContain('await taskApi.prepareExport')
    expect(collector).toContain('collector-statistics-detail')
    expect(collector).toContain('首次提交')
    expect(collector).toContain('高峰时段')
    expect(detail).toContain('reportApi.collectorTask')
    expect(detail).toContain('reportApi.collectorTaskSubmissions')
    expect(detail).toContain('reportApi.collectorTaskCompletions')
    expect(detail).toContain('提交明细')
    expect(detail).toContain('完成明细')
    expect(cards).toContain('提交统计')
    expect(cards).toContain('完成统计')
    expect(hours).toContain('24 小时首次提交分布')
  })
})
