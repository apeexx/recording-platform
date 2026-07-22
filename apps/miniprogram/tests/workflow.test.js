const test = require('node:test')
const assert = require('node:assert/strict')
const { createWorkflow, claimNextWithRetry, shouldAutoClaim } = require('../services/workflow.js')

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

test('自动领取下一条在弱网重试时复用同一幂等键', async () => {
  let attempts = 0
  const keys = []
  const result = await claimNextWithRetry({
    taskId: 'task-1',
    operationId: () => 'stable-claim-next',
    start: async (taskId, key) => {
      assert.equal(taskId, 'task-1')
      keys.push(key)
      if (++attempts === 1) { const error = new Error('network'); error.network = true; throw error }
      return { id: 'item-next' }
    },
  })
  assert.equal(result.id, 'item-next')
  assert.deepEqual(keys, ['stable-claim-next', 'stable-claim-next'])
})
