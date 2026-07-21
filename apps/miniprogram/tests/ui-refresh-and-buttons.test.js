const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')

const read = file => fs.readFileSync(file, 'utf8')
const pageConfig = page => JSON.parse(read(`pages/${page}/index.json`))

test('小程序统一清除原生按钮边框并区分普通、迷你、胶囊和圆形按钮', () => {
  const globalStyle = read('app.wxss')
  const workListStyle = read('pages/work-list/index.wxss')
  const workStyle = read('pages/work/index.wxss')
  const playerStyle = read('components/audio-player/index.wxss')

  assert.match(globalStyle, /button\{[^}]*box-sizing:border-box[^}]*overflow:hidden/)
  assert.match(globalStyle, /button::after\{border:0\}/)
  assert.match(globalStyle, /\.button\{[^}]*display:flex[^}]*min-height:88rpx[^}]*border-radius:18rpx/)
  assert.match(globalStyle, /\.button\[size=mini\]\{[^}]*min-height:64rpx[^}]*border-radius:16rpx/)
  assert.match(workListStyle, /\.filter\{[^}]*border-radius:999rpx/)
  assert.match(workStyle, /\.mic-button\{[^}]*border-radius:50%/)
  assert.match(playerStyle, /\.play-button\{[^}]*border-radius:50%/)
})

test('只有任务大厅、任务数据和我的统计开启下拉刷新', () => {
  assert.equal(pageConfig('tasks').enablePullDownRefresh, true)
  assert.equal(pageConfig('work-list').enablePullDownRefresh, true)
  assert.equal(pageConfig('profile').enablePullDownRefresh, true)

  for (const page of ['login', 'profile-settings', 'work']) {
    assert.notEqual(pageConfig(page).enablePullDownRefresh, true)
  }
})

test('任务数据页按当前页签重新加载并始终结束下拉刷新动画', () => {
  const script = read('pages/work-list/index.js')

  assert.match(script, /onPullDownRefresh\(\)\s*\{\s*this\.load\(\)\.finally\(\(\)\s*=>\s*wx\.stopPullDownRefresh\(\)\)\s*\}/)
  assert.match(script, /kind:this\.data\.kind/)
})
