const test=require('node:test')
const assert=require('node:assert/strict')
const fs=require('node:fs')
const path=require('node:path')

function pngDimensions(buffer){
  assert.equal(buffer.subarray(0,8).toString('hex'),'89504e470d0a1a0a')
  return {width:buffer.readUInt32BE(16),height:buffer.readUInt32BE(20)}
}

test('任务和我的 Tab 均配置 81×81 的本地 PNG 图标',()=>{
  const app=JSON.parse(fs.readFileSync(path.resolve('app.json'),'utf8'))
  assert.equal(app.tabBar.list.length,2)
  for(const item of app.tabBar.list){
    assert.match(item.iconPath,/^assets\/tabbar\/.+\.png$/)
    assert.match(item.selectedIconPath,/^assets\/tabbar\/.+-active\.png$/)
    for(const key of ['iconPath','selectedIconPath']){
      const filename=path.resolve(item[key])
      assert.equal(fs.existsSync(filename),true,`${item[key]} 不存在`)
      const content=fs.readFileSync(filename)
      assert.deepEqual(pngDimensions(content),{width:81,height:81})
      assert.ok(content.length<40*1024,`${item[key]} 超过 40KB`)
    }
  }
})
