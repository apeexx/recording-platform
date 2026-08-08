import { describe, expect, it } from 'vitest'
import { formatPercentage, percentageValue } from '../lib/percentage.js'
import fs from 'node:fs'
import path from 'node:path'

describe('percentage formatter', () => {
  it('formats ratios with exactly two decimal places', () => {
    expect(formatPercentage(0, 0)).toBe('0.00%')
    expect(formatPercentage(1, 3)).toBe('33.33%')
    expect(formatPercentage(3, 3)).toBe('100.00%')
  })

  it('keeps progress values numeric and clamps invalid ranges', () => {
    expect(percentageValue(1, 3)).toBeCloseTo(33.333333, 5)
    expect(percentageValue(-1, 3)).toBe(0)
    expect(percentageValue(4, 3)).toBe(100)
    expect(percentageValue(1, 0)).toBe(0)
  })

  it('uses the shared formatter on every visible web percentage', () => {
    const review = fs.readFileSync(path.resolve('src/pages/admin/review/ReviewTaskSelectPage.vue'), 'utf8')
    const pool = fs.readFileSync(path.resolve('src/pages/admin/tasks/TaskPoolPage.vue'), 'utf8')
    const detail = fs.readFileSync(path.resolve('src/pages/admin/tasks/TaskDetailPage.vue'), 'utf8')

    expect(review).toContain('formatPercentage')
    expect(pool).toContain('formatPercentage')
    expect(detail).toContain('formatPercentage')
    expect(review).not.toContain('Math.round(value / total * 100)')
    expect(pool).not.toContain('Math.round((batchJob.value.processedCount || 0) / batchJob.value.selectedCount * 100)')
    expect(detail).not.toContain('Math.round(processedRows.value / totalRows * 100)')
  })
})
