import { describe, expect, it } from 'vitest'

async function loadStateModule() {
  return import('../lib/taskPoolState.js').catch(() => ({}))
}

describe('任务数据池页面状态', () => {
  it('从路由查询恢复重复筛选、页码和合法每页条数', async () => {
    const state = await loadStateModule()
    expect(state.parseTaskPoolRouteState).toBeTypeOf('function')

    expect(state.parseTaskPoolRouteState({
      page: '2',
      size: '50',
      itemCode: ['T000001-0000001', 'T000001-0000002'],
      group: ['SUBMITTED', 'FINISHED'],
      collectorId: 'MINI-1',
      includeUnassigned: ['false', 'true'],
      result: ['TEXT_ONLY', 'AUDIO_ONLY'],
      sourceItemIdQuery: ' script-8 ',
    })).toEqual({
      page: 2,
      size: 50,
      filters: {
        itemCodes: ['T000001-0000001', 'T000001-0000002'],
        groups: ['SUBMITTED', 'FINISHED'],
        collectorIds: ['MINI-1'],
        includeUnassigned: true,
        results: ['TEXT_ONLY', 'AUDIO_ONLY'],
        sourceItemIdQuery: 'script-8',
      },
    })
  })

  it('非法分页回退默认值并在写入路由时省略默认状态', async () => {
    const state = await loadStateModule()
    expect(state.parseTaskPoolRouteState({ page: ['2', '9'], size: '50' }).page).toBe(0)
    expect(state.parseTaskPoolRouteState({ page: '2abc', size: '50' }).page).toBe(0)
    expect(state.parseTaskPoolRouteState({ page: '2147483648', size: '50' }).page).toBe(0)
    expect(state.parseTaskPoolRouteState({ page: '-4', size: '100' })).toEqual({
      page: 0,
      size: 20,
      filters: {
        itemCodes: [], groups: [], collectorIds: [], includeUnassigned: false,
        results: [], sourceItemIdQuery: '',
      },
    })
    expect(state.buildTaskPoolRouteQuery({
      page: 0,
      size: 20,
      filters: {
        itemCodes: [], groups: [], collectorIds: [], includeUnassigned: false,
        results: [], sourceItemIdQuery: '',
      },
    })).toEqual({})
  })

  it('将非默认筛选序列化为 Vue Router 查询对象', async () => {
    const state = await loadStateModule()
    expect(state.buildTaskPoolRouteQuery({
      page: 3,
      size: 10,
      filters: {
        itemCodes: ['T000001-0000003'],
        groups: ['DISCARDED'],
        collectorIds: ['MINI-2'],
        includeUnassigned: true,
        results: ['NONE'],
        sourceItemIdQuery: 'source-2',
      },
    })).toEqual({
      page: '3', size: '10', itemCode: ['T000001-0000003'], group: ['DISCARDED'],
      collectorId: ['MINI-2'], includeUnassigned: 'true', result: ['NONE'],
      sourceItemIdQuery: 'source-2',
    })
  })

  it('长期任务缓存支持读取、写入、清理和异常降级', async () => {
    const state = await loadStateModule()
    const values = new Map()
    const storage = {
      getItem: key => values.get(key) ?? null,
      setItem: (key, value) => values.set(key, value),
      removeItem: key => values.delete(key),
    }

    expect(state.readLastTaskId(storage)).toBe('')
    state.writeLastTaskId('task-1', storage)
    expect(state.readLastTaskId(storage)).toBe('task-1')
    state.clearLastTaskId(storage)
    expect(state.readLastTaskId(storage)).toBe('')

    const blocked = {
      getItem() { throw new Error('blocked') },
      setItem() { throw new Error('blocked') },
      removeItem() { throw new Error('blocked') },
    }
    expect(state.readLastTaskId(blocked)).toBe('')
    expect(() => state.writeLastTaskId('task-2', blocked)).not.toThrow()
    expect(() => state.clearLastTaskId(blocked)).not.toThrow()
  })
})
