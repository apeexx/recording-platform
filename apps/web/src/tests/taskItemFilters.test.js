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
      group: 'FINISHED',
      collectorIds: ['MINI-1'],
      includeUnassigned: false,
      result: 'TEXT_AND_AUDIO',
    })).toEqual({
      group: 'FINISHED',
      collectorIds: ['MINI-1'],
      includeUnassigned: false,
      result: 'TEXT_AND_AUDIO',
    })
  })
})
