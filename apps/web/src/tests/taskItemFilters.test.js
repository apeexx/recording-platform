import { describe, expect, it } from 'vitest'
import { buildTaskItemQuery, selectionFilters } from '../lib/taskItemFilters.js'

describe('task item filters', () => {
  it('serializes one or many collectors as repeated query parameters', () => {
    const query = buildTaskItemQuery(2, 20, {
      group: 'PENDING',
      collectorIds: ['MINI-1', 'MINI-2'],
      includeUnassigned: true,
      result: 'AUDIO_ONLY',
    })

    expect(query).toContain('page=2')
    expect(query).toContain('collectorId=MINI-1')
    expect(query).toContain('collectorId=MINI-2')
    expect(query).toContain('includeUnassigned=true')
  })

  it('copies visible filters into cross-page selection payloads', () => {
    expect(selectionFilters({
      itemCodes: ['T000001-0000001'],
      groups: ['FINISHED', 'SUBMITTED'],
      collectorIds: ['MINI-1'],
      includeUnassigned: false,
      results: ['TEXT_AND_AUDIO', 'AUDIO_ONLY'],
      sourceItemIdQuery: '',
    })).toEqual({
      itemCodes: ['T000001-0000001'],
      groups: ['FINISHED', 'SUBMITTED'],
      collectorIds: ['MINI-1'],
      includeUnassigned: false,
      results: ['TEXT_AND_AUDIO', 'AUDIO_ONLY'],
      sourceItemIdQuery: '',
    })
  })

  it('serializes code search and repeated code group and result values', () => {
    const query = buildTaskItemQuery(0, 20, {
      itemCodes: ['T000001-0000001', 'T000001-0000002'],
      itemCodeQuery: '000001',
      groups: ['PENDING', 'SUBMITTED'],
      results: ['TEXT_ONLY', 'AUDIO_ONLY'],
      sourceItemIdQuery: ' script.+ ',
    })
    const params = new URLSearchParams(query.slice(1))

    expect(params.getAll('itemCode')).toEqual(['T000001-0000001', 'T000001-0000002'])
    expect(params.get('itemCodeQuery')).toBe('000001')
    expect(params.getAll('group')).toEqual(['PENDING', 'SUBMITTED'])
    expect(params.getAll('result')).toEqual(['TEXT_ONLY', 'AUDIO_ONLY'])
    expect(params.get('sourceItemIdQuery')).toBe('script.+')
  })
})
