const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')

const read = file => fs.readFileSync(file, 'utf8')

test('任务大厅与任务数据页使用三段流程', () => {
	const hall = read('pages/tasks/index.wxml')
	const listScript = read('pages/work-list/index.js')
	const listPage = read('pages/work-list/index.wxml')
	assert.match(hall, /查看任务/)
	assert.doesNotMatch(hall, /查看待办/)
	assert.match(listScript, /PENDING.*待处理.*SUBMITTED.*已提交.*FINISHED.*已完成/s)
	assert.match(listPage, /任务数据/)
	assert.match(listPage, /待审核/)
	assert.match(listPage, /已完成/)
})

test('作业页区分可编辑和只读状态并使用本地 SVG 图标', () => {
	const script = read('pages/work/index.js')
	const page = read('pages/work/index.wxml')
	assert.match(script, /editable/)
	assert.match(script, /readOnly/)
	assert.match(script, /SUBMITTED/)
	assert.match(script, /STALE_STATE/)
	assert.match(page, /assets\/icons\/iconfont\/microphone\.svg/)
	assert.match(page, /assets\/icons\/iconfont\/trash\.svg/)
	assert.doesNotMatch(page, /🎙|🗑/)
	assert.match(page, /wx:if="{{editable}}"/)
	assert.match(page, /wx:if="{{readOnly}}"/)
})

test('Iconfont 本地资源记录来源与许可', () => {
	const notice = read('assets/icons/iconfont/README.md')
	assert.match(notice, /iconfont\.cn/)
	assert.match(notice, /许可|授权/)
	assert.ok(fs.existsSync('assets/icons/iconfont/microphone.svg'))
	assert.ok(fs.existsSync('assets/icons/iconfont/trash.svg'))
})
