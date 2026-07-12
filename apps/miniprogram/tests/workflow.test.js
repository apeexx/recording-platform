const test = require('node:test')
const assert = require('node:assert/strict')
const { createWorkflow, shouldAutoClaim } = require('../services/workflow.js')

test('弱网重试复用同一 operationId，不重复生成写操作身份', async () => {
  let attempts = 0
  const keys = []
  const flow = createWorkflow({
    operationId: () => 'stable-operation',
    submit: async payload => { keys.push(payload.operationId); if (++attempts === 1) { const e=new Error('network');e.network=true;throw e } return {status:'COMPLETED'} }
  })
  const result = await flow.submitWithRetry({ itemId:'i1' })
  assert.equal(result.status, 'COMPLETED')
  assert.deepEqual(keys, ['stable-operation','stable-operation'])
})

test('自动下一条只在提交成功、权限和任务仍有效时继续', () => {
  assert.equal(shouldAutoClaim({enabled:true,submitted:true,grantActive:true,taskRunning:true,poolEmpty:false}), true)
  assert.equal(shouldAutoClaim({enabled:true,submitted:true,grantActive:false,taskRunning:true,poolEmpty:false}), false)
  assert.equal(shouldAutoClaim({enabled:true,submitted:false,grantActive:true,taskRunning:true,poolEmpty:false}), false)
})
