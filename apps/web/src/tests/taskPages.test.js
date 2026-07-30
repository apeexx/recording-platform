import { beforeEach, describe, expect, it, vi } from 'vitest'
vi.mock('../lib/httpClient.js', () => ({ httpRequest: vi.fn() }))
import { httpRequest } from '../lib/httpClient.js'
import { taskApi } from '../lib/taskApi.js'
import fs from 'node:fs'
import path from 'node:path'

describe('任务页面 API', () => {
  beforeEach(() => vi.clearAllMocks())
	it('创建任务不发送平台或手填编号', async () => {
	  httpRequest.mockResolvedValue({ id: 't1', taskCode: 'T000001' })
	  const data = { name: '朗读任务', configuration: { referenceTypes: ['TEXT'], resultType: 'TEXT' } }
	  await taskApi.create(data, 'op-1')
	  expect(httpRequest).toHaveBeenCalledWith('/api/tasks', { method: 'POST', json: data, idempotencyKey: 'op-1' })
	})
	it('任务编辑使用嵌入配置、胶囊开关和双端时长滑块', () => {
	  const source = fs.readFileSync(path.resolve('src/pages/admin/tasks/TaskEditorPage.vue'), 'utf8')
	  expect(source).toContain('DurationRangeSlider')
	  expect(source).toContain('ToggleSwitch')
	  expect(source).toContain('configuration')
	  expect(source).not.toContain('taskApi.versions')
	  expect(source).toContain('v-if="form.humanReviewEnabled"')
	  expect(source).toContain("rejectionReasons: form.humanReviewEnabled")
	})
	it('录音时长占半行且参考类型使用彩色方形复选框', () => {
	  const source = fs.readFileSync(path.resolve('src/pages/admin/tasks/TaskEditorPage.vue'), 'utf8')
	  const controls = fs.readFileSync(path.resolve('src/styles/form-controls.css'), 'utf8')
	  const business = fs.readFileSync(path.resolve('src/styles/business.css'), 'utf8')

	  expect(source).toContain('<label class="duration-range-field">录音时长范围')
	  expect(source).not.toContain('<label class="business-span">录音时长范围')
	  expect(source).toContain('colored-checkbox')
	  expect(controls).toContain('.duration-range-track')
	  expect(controls).not.toContain('.duration-range-card')
	  expect(business).toContain('.colored-checkbox input')
	  expect(business).toContain('appearance:none')
	})
	it('任务列表移除版本并展示成果与录音配置摘要', () => {
	  const source = fs.readFileSync(path.resolve('src/pages/admin/tasks/TaskListPage.vue'), 'utf8')
	  expect(source).not.toContain('currentVersionNumber')
	  expect(source).toContain('最终成果')
	  expect(source).toContain('录音格式')
	  expect(source).toContain('采样率')
	  expect(source).toContain('时长范围')
	  expect(source).toContain("row.lifecycle === 'DRAFT'")
	  expect(source).toContain('@click="deleteTask(row)">删除')
	  expect(source).toContain("row.lifecycle === 'RUNNING'")
	  expect(source).toContain("row.lifecycle === 'PAUSED'")
	  expect(source).not.toContain("['RUNNING', 'PAUSED'].includes(row.lifecycle)")
	  expect(source).not.toContain("row.lifecycle !== 'ENDED'")
	})
  it('任务数据池完全不显示或提交外部编号', () => {
	  const detail = fs.readFileSync(path.resolve('src/pages/admin/tasks/TaskDetailPage.vue'), 'utf8')
	  const pool = fs.readFileSync(path.resolve('src/pages/admin/tasks/TaskPoolPage.vue'), 'utf8')
	  expect(detail).not.toContain('externalItemId')
	  expect(detail).not.toContain('外部编号')
	  expect(pool).not.toContain('externalItemId')
	  expect(pool).not.toContain('外部编号')
	})
  it('任务详情数据池默认每页二十条并提供服务端分页', () => {
    const detail = fs.readFileSync(path.resolve('src/pages/admin/tasks/TaskDetailPage.vue'), 'utf8')
    expect(detail).toContain("import PaginationControls from '../../../components/admin/PaginationControls.vue'")
    expect(detail).toContain('const itemPageSize = ref(20)')
    expect(detail).toContain('taskApi.items(route.params.id, page.value, itemPageSize.value, filters.value)')
    expect(detail).toContain('function changePage(value)')
    expect(detail).toContain('async function changePageSize(value)')
    expect(detail).toContain('itemPageSize.value = value')
    expect(detail).toMatch(/changePageSize\(value\)[\s\S]*page\.value = 0[\s\S]*selection\.clear\(\)/)
    expect(detail).toContain('selection.clearPageMode()')
    expect(detail).toContain('数据池（共 {{ total }} 条）')
    expect(detail).toContain('<PaginationControls :page="page" :size="itemPageSize" :page-sizes="[10, 20, 50]" :total="total" @change="changePage" @size-change="changePageSize" />')
    expect(detail).not.toContain('taskApi.items(route.params.id, 0, 100)')
    const pagination = fs.readFileSync(path.resolve('src/components/admin/PaginationControls.vue'), 'utf8')
    const paginationStyles = fs.readFileSync(path.resolve('src/styles/pagination.css'), 'utf8')
    expect(pagination).toContain('class="pagination-pages"')
    expect(paginationStyles).toMatch(/\.pagination-numbered\s*\{[^}]*justify-content:\s*space-between/)
    expect(paginationStyles).toContain('.pagination-size .base-select-menu')
    expect(paginationStyles).toMatch(/bottom:\s*calc\(100% \+ 8px\)/)
  })

  it('adds filtered item inspection and collector multi-selection', () => {
    const detail = fs.readFileSync(path.resolve('src/pages/admin/tasks/TaskDetailPage.vue'), 'utf8')
    const pool = fs.readFileSync(path.resolve('src/pages/admin/tasks/TaskPoolPage.vue'), 'utf8')
    const routes = fs.readFileSync(path.resolve('src/router/adminRoutes.js'), 'utf8')
    assert.match(detail, /TaskItemFilters/)
    assert.match(pool, /TaskItemFilters/)
    assert.match(detail, /查看/)
    assert.match(pool, /查看/)
    assert.match(routes, /items\/:itemId/)
    assert.match(routes, /TaskItemDetailPage/)
  })

  it('uses teleported multi-select table filters and one batch action menu', () => {
    const filters = fs.readFileSync(path.resolve('src/components/admin/TaskItemFilters.vue'), 'utf8')
    const popover = fs.readFileSync(path.resolve('src/components/admin/TableFilterPopover.vue'), 'utf8')
    const batchMenu = fs.readFileSync(path.resolve('src/components/admin/TaskBatchActionMenu.vue'), 'utf8')
    const detail = fs.readFileSync(path.resolve('src/pages/admin/tasks/TaskDetailPage.vue'), 'utf8')
    const pool = fs.readFileSync(path.resolve('src/pages/admin/tasks/TaskPoolPage.vue'), 'utf8')

    expect(popover).toContain('<Teleport to="body">')
    expect(popover).toContain('position: fixed')
    expect(popover).toContain('z-index: 2400')
    expect(popover).toContain("window.addEventListener('scroll'")
    expect(filters).toContain("kind === 'code'")
    expect(filters).toContain('type="checkbox"')
    expect(filters).not.toContain('type="radio"')
    expect(batchMenu).toContain('批量释放')
    expect(batchMenu).toContain('批量废弃')
    expect(batchMenu).toContain('批量恢复')
    expect(detail).toContain('TaskBatchActionMenu')
    expect(pool).toContain('TaskBatchActionMenu')
    expect(detail).not.toContain('采集员 ID</th>')
    expect(detail).not.toContain('@click="openEdit(row)">编辑')
    expect(detail).not.toContain('@click="deleteItem(row)">删除')
  })

  it('edits references inline and shows three recent operations plus an all-record modal', () => {
    const source = fs.readFileSync(path.resolve('src/pages/admin/tasks/TaskItemDetailPage.vue'), 'utf8')

    expect(source).not.toContain('TaskItemEditModal')
    expect(source).toContain('最近操作记录')
    expect(source).toContain('size: 3')
    expect(source).toContain('operationsOpen')
    expect(source).toContain('loadAllOperations')
    expect(source).toContain('referenceAudioUrl')
    expect(source).toContain('editForm.referenceAudioUrl')
    expect(source).toContain('editForm.referenceVideoUrl')
    expect(source).toContain('<textarea')
    expect(source).toContain('采集结果')
    expect(source).toContain('<Teleport to="body">')
  })

  it('preloads paginated mini-program users on the permission page', () => {
    const source = fs.readFileSync(path.resolve('src/pages/admin/tasks/TaskPermissionsPage.vue'), 'utf8')
    assert.match(source, /userType:\s*'MINIPROGRAM'/)
    assert.match(source, /PaginationControls/)
    assert.match(source, /小程序用户/)
  })

  it('defaults mini-program users to ten while keeping approvals and grants at five', () => {
    const source = fs.readFileSync(path.resolve('src/pages/admin/tasks/TaskPermissionsPage.vue'), 'utf8')
    const styles = fs.readFileSync(path.resolve('src/styles/business.css'), 'utf8')

    expect(source).toContain('const userSize = ref(10)')
    expect(source).toContain('const requestSize = ref(5)')
    expect(source).toContain('const grantSize = ref(5)')
    expect(source).toContain(':page-sizes="[5, 10]"')
    expect(source).toContain('requestQuery')
    expect(source).toContain('grantQuery')
    expect(source).toContain('row.userLoginName')
    expect(source).toContain("status: 'ACTIVE'")
    expect(styles).toMatch(/\.permission-layout\{[^}]*grid-template-columns:repeat\(2,minmax\(0,1fr\)\)/)
  })
  it('任务状态和数据池请求使用后端真实路径', async () => {
    httpRequest.mockResolvedValue({})
    await taskApi.transition('t1', 'publish', 'op-2')
    await taskApi.items('t1', 2, 30)
    expect(httpRequest).toHaveBeenNthCalledWith(1, '/api/tasks/t1/publish', { method: 'POST', idempotencyKey: 'op-2' })
    expect(httpRequest).toHaveBeenNthCalledWith(2, '/api/tasks/t1/items?page=2&size=30')
		expect(taskApi.versions).toBeUndefined()
  })
  it('CSV 导出先经统一请求预检，再返回继承全部筛选和日期范围的原生下载地址', async () => {
    httpRequest.mockResolvedValue(null)
    await taskApi.prepareExport('t1', {
      sourceItemIdQuery: 'script-',
    }, { fromDate: '2026-07-01', toDate: '2026-07-30' })
    const url = taskApi.exportItems('t1', {
      itemCodes: ['T000001-0000001'],
      groups: ['SUBMITTED'],
      collectorIds: ['MINI-1'],
      includeUnassigned: true,
      results: ['TEXT_ONLY'],
      sourceItemIdQuery: 'script-',
    }, { fromDate: '2026-07-01', toDate: '2026-07-30' })
    expect(url).toContain('/api/tasks/t1/items/export.csv?')
    expect(url).toContain('itemCode=T000001-0000001')
    expect(url).toContain('group=SUBMITTED')
    expect(url).toContain('collectorId=MINI-1')
    expect(url).toContain('includeUnassigned=true')
    expect(url).toContain('result=TEXT_ONLY')
    expect(url).toContain('sourceItemIdQuery=script-')
    expect(url).toContain('fromDate=2026-07-01')
    expect(url).toContain('toDate=2026-07-30')
    expect(url).not.toContain('page=')
    expect(httpRequest).toHaveBeenCalledWith(
      '/api/tasks/t1/items/export.csv/ready?sourceItemIdQuery=script-&fromDate=2026-07-01&toDate=2026-07-30'
    )
  })
	it('采集权限只展示带前缀的用户 ID，不使用旧用户编号字段',()=>{
	  const source=fs.readFileSync(path.resolve('src/pages/admin/tasks/TaskPermissionsPage.vue'),'utf8')
   expect(source).toContain('user.id')
   expect(source).toContain('row.userId')
	  expect(source).not.toContain('internalUserNo')
	  expect(source).not.toContain('userNo')
   expect(source).toContain('permission-layout')
	  const styles=fs.readFileSync(path.resolve('src/styles/business.css'),'utf8')
   expect(styles).toContain('.permission-layout')
	  expect(styles).toMatch(/\.permission-layout\{[^}]*align-items:start/)
	  expect(styles).toMatch(/\.permission-users\{[^}]*align-self:start/)
	})

  it('侧栏品牌区与顶部栏共享同一高度变量',()=>{
    const styles=fs.readFileSync(path.resolve('src/styles/admin-layout.css'),'utf8')
    expect(styles).toMatch(/--admin-header-height:\s*72px/)
    expect(styles).toMatch(/\.admin-sidebar__brand\s*\{[^}]*height:\s*var\(--admin-header-height\)[^}]*margin:\s*0 -14px/)
    expect(styles).toMatch(/\.admin-header\s*\{[^}]*height:\s*var\(--admin-header-height\)[^}]*min-height:\s*var\(--admin-header-height\)[^}]*padding:\s*8px 28px/)
    expect(styles).toMatch(/@media \(max-width:\s*620px\)[\s\S]*?\.admin-header\s*\{[^}]*height:\s*auto/)
  })
  it('导入使用 multipart 且不手写内容类型', async () => {
    httpRequest.mockResolvedValue({ importJobId: 'j1' })
    const file = new File(['a'], 'items.csv')
    await taskApi.importItems('t1', file, 'op-3')
    const options = httpRequest.mock.calls[0][1]
    expect(httpRequest.mock.calls[0][0]).toBe('/api/import-jobs')
    expect(options.body).toBeInstanceOf(FormData)
    expect(options.idempotencyKey).toBe('op-3')
  })

  it('数据池支持拖放导入、自动轮询、进度与 UTF-8 BOM 示例', () => {
    const detail = fs.readFileSync(path.resolve('src/pages/admin/tasks/TaskDetailPage.vue'), 'utf8')
    expect(detail).toContain('@drop.prevent="onDrop"')
    expect(detail).toContain('window.setTimeout(refreshJob, 1000)')
    expect(detail).toContain('onBeforeUnmount(() => { stopImportTracking(); clearBatchPoll() })')
    expect(detail).toContain('processedRows.value / totalRows')
    expect(detail).toContain("'\\uFEFFreferenceText,referenceAudioUrl,referenceVideoUrl")
    expect(detail).toContain('<progress :value="importProgress"')
    expect(detail).toContain("job.value.status === 'COMPLETED'")
    expect(detail).toContain('await loadItems()')
  })

  it('任务详情数据池提供查看和状态对应的快捷操作，草稿任务仍展示删除入口', () => {
    const detail = fs.readFileSync(path.resolve('src/pages/admin/tasks/TaskDetailPage.vue'), 'utf8')
    expect(detail).toContain("row.status === 'AVAILABLE'")
    expect(detail).toContain("row.status === 'DISCARDED'")
    expect(detail).toContain("rowAction(row, 'release')")
    expect(detail).toContain("rowAction(row, 'discard')")
    expect(detail).toContain("rowAction(row, 'restore')")
    expect(detail).not.toContain('@click="openEdit(row)">编辑')
    expect(detail).not.toContain('@click="deleteItem(row)">删除')
    expect(detail).toContain('>查看</router-link>')
    expect(detail).toContain("task?.lifecycle === 'DRAFT'")
    expect(detail).toContain('@click="deleteTask">删除任务')
    expect(detail).toContain('colored-checkbox')
    expect(detail).toContain('task-reference-textarea')
  })

  it('任务和条目删除及条目编辑使用幂等真实接口', async () => {
    httpRequest.mockResolvedValue({})
    await taskApi.deleteTask('t1', 'delete-task')
    await taskApi.updateItem('i1', { expectedRevision: 2, referenceText: '新文字' }, 'edit-item')
    await taskApi.deleteItem('i1', 3, 'delete-item')
    expect(httpRequest).toHaveBeenNthCalledWith(1, '/api/tasks/t1', { method: 'DELETE', idempotencyKey: 'delete-task' })
    expect(httpRequest).toHaveBeenNthCalledWith(2, '/api/task-items/i1', {
      method: 'PUT', json: { expectedRevision: 2, referenceText: '新文字' }, idempotencyKey: 'edit-item'
    })
    expect(httpRequest).toHaveBeenNthCalledWith(3, '/api/task-items/i1?expectedRevision=3', {
      method: 'DELETE', idempotencyKey: 'delete-item'
    })
  })

  it('批量废弃按条目携带 revision 并返回逐条结果', async () => {
    httpRequest.mockResolvedValue([{ itemId: 'i1', success: true }])
    const result = await taskApi.batchAction('discard', [{ itemId: 'i1', expectedRevision: 3 }], 'batch-1')
    expect(result[0].success).toBe(true)
    expect(httpRequest).toHaveBeenCalledWith('/api/task-items/batch/discard', {
      method: 'POST', json: { operationId: 'batch-1', items: [{ itemId: 'i1', expectedRevision: 3 }] }
    })
  })

})
