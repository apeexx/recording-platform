import { describe, expect, it } from 'vitest'
import { buildReviewPoolQuery, reviewSelectionFilters } from '../lib/reviewFilters.js'

describe('审核池筛选', () => {
  it('列表查询和跨页选择复用同一组筛选值', () => {
    const filters = {
      itemCodes: ['T000001-0000001'],
      itemCodeQuery: '000001',
      statuses: ['SUBMITTED'],
      collectorIds: ['MINI-1'],
      reviewerIds: ['WEB-1'],
      includeUnassignedReviewer: true,
      results: ['TEXT_ONLY'],
    }
    const query = buildReviewPoolQuery(0, 20, filters)
    const selection = reviewSelectionFilters(filters)

    expect(query).toContain('itemCode=T000001-0000001')
    expect(query).toContain('itemCodeQuery=000001')
    expect(query).toContain('reviewerId=WEB-1')
    expect(query).toContain('includeUnassignedReviewer=true')
    expect(selection).toEqual(filters)
  })
})
