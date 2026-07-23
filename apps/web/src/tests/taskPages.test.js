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
	  expect(controls).toContain('.duration-range-card')
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
	  expect(source).toContain("['RUNNING', 'PAUSED'].includes(row.lifecycle)")
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
  it('任务详情数据池每页十条并提供服务端分页', () => {
    const detail = fs.readFileSync(path.resolve('src/pages/admin/tasks/TaskDetailPage.vue'), 'utf8')
    expect(detail).toContain("import PaginationControls from '../../../components/admin/PaginationControls.vue'")
    expect(detail).toContain('const itemPageSize = ref(10)')
    expect(detail).toContain('taskApi.items(route.params.id, page.value, itemPageSize.value)')
    expect(detail).toContain('function changePage(value)')
    expect(detail).toContain('async function changePageSize(value)')
    expect(detail).toContain('itemPageSize.value = value')
    expect(detail).toMatch(/changePageSize\(value\)[\s\S]*page\.value = 0[\s\S]*selected\.value = new Set\(\)/)
    expect(detail).toContain('selected.value = new Set()')
    expect(detail).toContain('数据池（共 {{ total }} 条）')
    expect(detail).toContain('<PaginationControls numbered :page="page" :size="itemPageSize" :page-sizes="[5, 10, 20]" :total="total" @change="changePage" @size-change="changePageSize" />')
    expect(detail).not.toContain('taskApi.items(route.params.id, 0, 100)')
    const pagination = fs.readFileSync(path.resolve('src/components/admin/PaginationControls.vue'), 'utf8')
    const paginationStyles = fs.readFileSync(path.resolve('src/styles/pagination.css'), 'utf8')
    expect(pagination).toContain('class="pagination-pages"')
    expect(paginationStyles).toMatch(/\.pagination-numbered\s*\{[^}]*justify-content:\s*space-between/)
    expect(paginationStyles).toContain('.pagination-size .base-select-menu')
    expect(paginationStyles).toMatch(/bottom:\s*calc\(100% \+ 8px\)/)
  })
  it('任务状态和数据池请求使用后端真实路径', async () => {
    httpRequest.mockResolvedValue({})
    await taskApi.transition('t1', 'publish', 'op-2')
    await taskApi.items('t1', 2, 30)
    expect(httpRequest).toHaveBeenNthCalledWith(1, '/api/tasks/t1/publish', { method: 'POST', idempotencyKey: 'op-2' })
    expect(httpRequest).toHaveBeenNthCalledWith(2, '/api/tasks/t1/items?page=2&size=30')
		expect(taskApi.versions).toBeUndefined()
  })
	it('采集权限只展示带前缀的用户 ID，不使用旧用户编号字段',()=>{
	  const source=fs.readFileSync(path.resolve('src/pages/admin/tasks/TaskPermissionsPage.vue'),'utf8')
	  expect(source).toContain('u.id')
	  expect(source).toContain('r.userId')
	  expect(source).toContain('g.userId')
	  expect(source).not.toContain('internalUserNo')
	  expect(source).not.toContain('userNo')
	  expect(source).toContain('permission-overview-grid')
	  const styles=fs.readFileSync(path.resolve('src/styles/business.css'),'utf8')
	  expect(styles).toContain('.permission-overview-grid')
	  expect(styles).toContain('align-items:stretch')
	})

  it('侧栏品牌区与顶部栏共享同一高度变量',()=>{
    const styles=fs.readFileSync(path.resolve('src/styles/admin-layout.css'),'utf8')
    expect(styles).toMatch(/--admin-header-height:\s*68px/)
    expect(styles).toMatch(/height:\s*var\(--admin-header-height\)/)
    expect(styles).toMatch(/min-height:\s*var\(--admin-header-height\)/)
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
    expect(detail).toContain('onBeforeUnmount(stopImportTracking)')
    expect(detail).toContain('processedRows.value / totalRows')
    expect(detail).toContain("'\\uFEFFreferenceText,referenceAudioUrl,referenceVideoUrl")
    expect(detail).toContain('<progress :value="importProgress"')
    expect(detail).toContain("job.value.status === 'COMPLETED'")
    expect(detail).toContain('await loadItems()')
  })

  it('仅待领取数据展示编辑和删除，草稿任务展示删除入口', () => {
    const detail = fs.readFileSync(path.resolve('src/pages/admin/tasks/TaskDetailPage.vue'), 'utf8')
    expect(detail).toContain("row.status === 'AVAILABLE'")
    expect(detail).toContain('@click="openEdit(row)">编辑')
    expect(detail).toContain('@click="deleteItem(row)">删除')
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
