import { describe, expect, it } from 'vitest'
import { readFileSync, readdirSync } from 'node:fs'
import { join } from 'node:path'

const read = relativePath => readFileSync(join(process.cwd(), relativePath), 'utf8')

function vueFiles(directory) {
  return readdirSync(join(process.cwd(), directory), { withFileTypes: true }).flatMap(entry => {
    const relativePath = `${directory}/${entry.name}`
    if (entry.isDirectory()) return vueFiles(relativePath)
    return entry.name.endsWith('.vue') ? [relativePath] : []
  })
}

const productionVueFiles = [
  ...vueFiles('src/pages'),
  ...vueFiles('src/components'),
]

describe('Web 操作错误展示', () => {
  it('生产页面不再使用行内红字展示操作错误', () => {
    for (const file of productionVueFiles) {
      const source = read(file)
      expect(source, file).not.toContain('class="business-error"')
      expect(source, file).not.toContain('class="auth-error"')
    }
  })

  it('任务详情仅将首次加载错误交给 AsyncState', () => {
    const source = read('src/pages/admin/tasks/TaskDetailPage.vue')

    expect(source).toContain('const loadError = ref')
    expect(source).toContain(':error="loadError"')
    expect(source).not.toContain('editError')
    expect(source).not.toContain('error.value = exception.message')
    expect(source).not.toContain('importErrorSummary')
  })

  it('业务表单关闭浏览器原生校验并保留手动提交', () => {
    for (const file of productionVueFiles) {
      const source = read(file)
      const forms = source.match(/<form\b[^>]*>/g) || []
      for (const form of forms) expect(form, file).toMatch(/\snovalidate(?:\s|>)/)
    }
  })

  it('语音生成异常通过 Toast 展示而不覆盖普通状态文案', () => {
    for (const file of [
      'src/pages/admin/voice-generation/VoiceGenerationWorkbenchPage.vue',
      'src/pages/admin/voice-generation/VoiceConfigPage.vue',
      'src/pages/admin/voice-generation/VoiceGenerationRecordsPage.vue',
    ]) {
      const source = read(file)
      expect(source, file).toContain('useNotifications')
      expect(source, file).not.toMatch(/catch \(error\) \{\s*(?:statusMessage|message)\.value = error\.message/)
    }
  })
})
