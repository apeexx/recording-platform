import { beforeEach, describe, expect, it, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
vi.mock('../lib/httpClient.js', () => ({
  httpRequest: vi.fn(),
  configureSessionReplacedHandler: vi.fn(),
  markWebSessionEstablished: vi.fn(),
}))
import { httpRequest } from '../lib/httpClient.js'
import { reviewApi } from '../lib/reviewApi.js'
import ReviewQueuePage from '../pages/admin/review/ReviewQueuePage.vue'

describe('审核页面 API', () => {
  beforeEach(() => vi.clearAllMocks())
  it('领取使用 Idempotency-Key，驳回提交原因多选与说明', async () => {
    httpRequest.mockResolvedValue({})
	await reviewApi.claim('task-1', 'claim-1')
    await reviewApi.reject('item-1', 4, ['空音频'], '请重新录制', 'reject-1')
	expect(httpRequest).toHaveBeenNthCalledWith(1, '/api/reviews/tasks/task-1/claim', { method: 'POST', idempotencyKey: 'claim-1' })
    expect(httpRequest).toHaveBeenNthCalledWith(2, '/api/reviews/item-1/reject', {
      method: 'POST', json: { operationId: 'reject-1', expectedRevision: 4, reasons: ['空音频'], note: '请重新录制' }
    })
  })

	it('指定已提交条目必须先领取再审核', async () => {
		httpRequest.mockResolvedValue({})
		await reviewApi.claimItem('item-1', 7, 'claim-item-1')
		expect(httpRequest).toHaveBeenCalledWith('/api/reviews/item-1/claim', {
			method: 'POST', json: { operationId: 'claim-item-1', expectedRevision: 7 }
		})
	})

	it('选中条目支持批量领取和批量分配', async () => {
		httpRequest.mockResolvedValue([])
		const items = [{ itemId: 'item-1', expectedRevision: 3 }]
		await reviewApi.batchClaim(items, 'claim-selected-1')
		await reviewApi.batchAssign(items, 'reviewer-1', 'assign-selected-1')
		expect(httpRequest).toHaveBeenNthCalledWith(1, '/api/reviews/batch/claim', {
			method: 'POST', json: { operationId: 'claim-selected-1', items }
		})
		expect(httpRequest).toHaveBeenNthCalledWith(2, '/api/reviews/batch/assign', {
			method: 'POST', json: { operationId: 'assign-selected-1', reviewerId: 'reviewer-1', items }
		})
	})

  it('按后台角色区分审核池与工作台操作，管理员不会再看到审核员专属按钮', () => {
    const queue = readFileSync(join(process.cwd(), 'src/pages/admin/review/ReviewQueuePage.vue'), 'utf8')
    const workbench = readFileSync(join(process.cwd(), 'src/pages/admin/review/ReviewWorkbenchPage.vue'), 'utf8')
    expect(workbench).toContain('review-source-column')
    expect(workbench).toContain('review-decision-column')

    expect(queue).toContain("const isAdmin = computed(() => session.user.value?.role === 'ADMIN')")
	expect(queue).toContain("const isReviewer = computed(() => session.user.value?.role === 'REVIEWER')")
    expect(queue).toContain('v-if="isReviewer" class="button-primary" @click="claim"')
	expect(queue).toContain("row.status === 'SUBMITTED'")
	expect(queue).toContain('领取审核')
	expect(queue).toContain('已提交')
	expect(queue).toContain('待审核')
		expect(workbench).toContain("const isReviewer = computed(() => session.user.value?.role === 'REVIEWER')")
		expect(workbench).toContain("item.value?.status === 'REVIEW_PENDING'")
		expect(workbench).toContain('v-if="isOwnReviewAssignment" class="button-secondary" @click="release"')
    expect(workbench).toContain('v-if="canDecide" class="business-actions"')
  })

  it('审核池展示采集员 prefixed ID，不读取旧 collectorUserNo',()=>{
    const queue=readFileSync(join(process.cwd(),'src/pages/admin/review/ReviewQueuePage.vue'),'utf8')
    expect(queue).toContain('r.collectorId')
    expect(queue).not.toContain('collectorUserNo')
  })

  it('审核池将五维筛选编码为可重复查询参数', async () => {
    httpRequest.mockResolvedValue({ items: [], total: 0 })
    await reviewApi.pool('task-1', 2, 20, {
      itemCodes: ['T000001-0000001'],
      statuses: ['SUBMITTED', 'REVIEW_PENDING'],
      collectorIds: ['MINI-1'],
      reviewerIds: ['WEB-1'],
      includeUnassignedReviewer: true,
      results: ['TEXT_AND_AUDIO'],
    })
    const url = httpRequest.mock.calls[0][0]
    expect(url).toContain('itemCode=T000001-0000001')
    expect(url).toContain('status=SUBMITTED')
    expect(url).toContain('status=REVIEW_PENDING')
    expect(url).toContain('collectorId=MINI-1')
    expect(url).toContain('reviewerId=WEB-1')
    expect(url).toContain('includeUnassignedReviewer=true')
    expect(url).toContain('result=TEXT_AND_AUDIO')
  })

  it('审核汇总支持任务列表和单任务指标', async () => {
    httpRequest.mockResolvedValue({})
    await reviewApi.tasks()
    await reviewApi.taskSummary('task-1')
    expect(httpRequest).toHaveBeenNthCalledWith(1, '/api/reviews/tasks')
    expect(httpRequest).toHaveBeenNthCalledWith(2, '/api/reviews/tasks/task-1/summary')
  })

  it('审核人员候选使用审核域接口而不是管理员用户搜索', async () => {
    httpRequest.mockResolvedValue([])
    await reviewApi.filterUsers('task-1', 'COLLECTOR', '张')
    expect(httpRequest).toHaveBeenCalledWith(
      '/api/reviews/tasks/task-1/filter-users?role=COLLECTOR&query=%E5%BC%A0'
    )
    const filters = readFileSync(join(process.cwd(), 'src/components/admin/TaskItemFilters.vue'), 'utf8')
    expect(filters).toContain('await reviewApi.filterUsers(props.taskId, role, userQuery.value)')
    expect(filters).toContain('await reviewApi.pool(props.taskId, 0, 20')
  })

  it('审核池挂载时为采集员和审核员候选请求携带当前任务 ID', async () => {
    httpRequest.mockImplementation((url) => Promise.resolve(
      url.startsWith('/api/batch-operation-jobs') ? []
        : url.includes('/filter-users') ? []
          : { items: [{
              id: 'item-1', itemCode: 'T000001-0000001', status: 'SUBMITTED',
              revision: 1, collectorId: 'MINI-1', collectorName: '采集员',
              reviewerId: null, reviewerName: null, hasText: true, hasAudio: false,
            }], total: 1 }
    ))
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/admin/review', component: { template: '<div />' } },
        { path: '/admin/review/tasks/:taskId', component: ReviewQueuePage },
      ],
    })
    await router.push('/admin/review/tasks/task-9')
    await router.isReady()

    const wrapper = mount(ReviewQueuePage, { global: { plugins: [router] } })
    await flushPromises()
    const urls = httpRequest.mock.calls.map(([url]) => url)

    expect(urls).toContain('/api/reviews/tasks/task-9/filter-users?role=COLLECTOR')
    expect(urls).toContain('/api/reviews/tasks/task-9/filter-users?role=REVIEWER')
    expect(urls.some(url => url.includes('/api/reviews/tasks//'))).toBe(false)
    wrapper.unmount()
  })

  it('工作台只读展示原始结果并独立编辑最终答案和采用 AI 结果', () => {
    const workbench = readFileSync(join(process.cwd(), 'src/pages/admin/review/ReviewWorkbenchPage.vue'), 'utf8')
    expect(workbench).toContain('原始采集结果')
    expect(workbench).toContain('review-original-text')
    expect(workbench).toContain('v-model="finalAnswer"')
    expect(workbench).not.toContain('v-model="text"')
    expect(workbench).toContain('AI 音频转文字')
    expect(workbench).toContain('AI 文本结果转写')
    expect(workbench).toContain('采用结果')
    expect(workbench).toContain('object-fit:contain')
  })

  it('审核池允许混合勾选并按状态启用三个批量操作', () => {
    const queue = readFileSync(join(process.cwd(), 'src/pages/admin/review/ReviewQueuePage.vue'), 'utf8')
    expect(queue).not.toContain(":disabled=\"r.status!=='REVIEW_PENDING'\"")
    expect(queue).toContain('claimableRows')
    expect(queue).toContain('approvableRows')
    expect(queue).toContain('批量领取审核')
    expect(queue).toContain('批量分配')
    expect(queue).toContain('批量通过')
    expect(queue).toContain('UserSearchSelect')
    expect(queue).toContain('kind="collector"')
    expect(queue).toContain('kind="reviewer"')
    expect(queue).toContain('kind="status"')
    expect(queue).toContain('kind="result"')
    expect(queue).toContain('审核池筛选')
    expect(queue).toContain('清除筛选')
    expect(queue).toContain('review-summary-grid')
  })

  it('审核入口包含双进度轮播、六秒自动切换和手动锁定阈值', () => {
    const entry = readFileSync(join(process.cwd(), 'src/pages/admin/review/ReviewTaskSelectPage.vue'), 'utf8')
    expect(entry).toContain('任务整体进度')
    expect(entry).toContain('审核处理进度')
    expect(entry).toContain('6000')
    expect(entry).toContain('48')
    expect(entry).toContain('prefers-reduced-motion')
    expect(entry).toContain('当前积压')
    expect(entry).toContain('今日完成')
  })
})
