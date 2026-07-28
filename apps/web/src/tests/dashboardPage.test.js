import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'

describe('admin dashboard', () => {
  it('loads real dashboard and recent operation APIs', () => {
    const source = readFileSync(join(process.cwd(), 'src/pages/admin/dashboard/AdminDashboard.vue'), 'utf8')
    expect(source).toContain('reportApi.dashboard')
    expect(source).toContain('reportApi.operations')
    expect(source).toContain('近 7 日首次提交')
    expect(source).not.toContain('模拟')
  })
})
