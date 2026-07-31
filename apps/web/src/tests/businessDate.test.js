import { describe, expect, it } from 'vitest'
import { businessDate, businessMonthStart, shiftBusinessDate } from '../lib/businessDate.js'

describe('凌晨四点业务日期', () => {
  it('上海时间凌晨四点前仍归属前一业务日', () => {
    expect(businessDate(new Date('2026-07-31T19:59:59Z'))).toBe('2026-07-31')
    expect(businessDate(new Date('2026-07-31T20:00:00Z'))).toBe('2026-08-01')
  })

  it('支持跨月日期偏移和业务月起点', () => {
    expect(shiftBusinessDate('2026-08-01', -1)).toBe('2026-07-31')
    expect(businessMonthStart('2026-08-01')).toBe('2026-08-01')
  })
})
