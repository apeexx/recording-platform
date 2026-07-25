const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { execFileSync } = require('node:child_process')

const scriptsRoot = path.resolve(__dirname, '..')
const repositoryRoot = path.resolve(scriptsRoot, '..')
const read = (name) => fs.readFileSync(path.join(scriptsRoot, name), 'utf8')

test('生产部署文件均存在', () => {
  for (const name of [
    'deploy-production.sh',
    'pm2-production.config.cjs',
    'maven-settings-production.xml'
  ]) {
    assert.equal(fs.existsSync(path.join(scriptsRoot, name)), true, name)
  }
})

test('Linux 后端 Maven Wrapper 在 Git 中保持可执行', () => {
  const entry = execFileSync(
    'git',
    ['ls-files', '-s', 'backend/mvnw'],
    { cwd: repositoryRoot, encoding: 'utf8' }
  )
  assert.match(entry, /^100755 /)
})

test('部署脚本使用干净 main 门禁和仅快进更新后重载自身', () => {
  const script = read('deploy-production.sh')
  assert.match(script, /git status --porcelain/)
  assert.match(script, /git branch --show-current/)
  assert.match(script, /git fetch origin main/)
  assert.match(script, /git pull --ff-only origin main/)
  assert.match(script, /exec .+--after-pull/)
  assert.doesNotMatch(script, /git (?:reset|clean|checkout|stash)|push --force/)
})

test('部署脚本在 PM2 之前完成三端验证和产物检查', () => {
  const script = read('deploy-production.sh')
  const webTest = script.indexOf('npm test -- --run')
  const webBuild = script.indexOf('npm run build')
  const backendBuild = script.indexOf('clean package')
  const miniTest = script.lastIndexOf('npm test')
  const pm2Action = script.indexOf('pm2 describe')
  assert.ok(webTest >= 0 && webTest < webBuild)
  assert.ok(webBuild < backendBuild)
  assert.ok(backendBuild < miniTest)
  assert.ok(miniTest < pm2Action)
  assert.match(script, /apps\/web\/dist\/index\.html/)
  assert.match(script, /recording-platform-backend-0\.0\.1-SNAPSHOT\.jar/)
})

test('部署脚本固定使用国内镜像、Node 22 和 Java 17', () => {
  const script = read('deploy-production.sh')
  assert.match(script, /registry\.npmmirror\.com/)
  assert.match(script, /maven\.aliyun\.com\/repository\/public/)
  assert.match(script, /nvm use 22/)
  assert.match(script, /Java 17/)
})

test('部署脚本在构建前校验集成摘要为 64 位十六进制', () => {
  const script = read('deploy-production.sh')
  assert.match(
    script,
    /\^RECORDING_INTEGRATION_API_KEY_SHA256=\[0-9A-Fa-f\]\{64\}\$/
  )
})

test('部署脚本只在完整就绪后保存 PM2 状态', () => {
  const script = read('deploy-production.sh')
  const health = script.indexOf('/api/health/ready')
  const overall = script.indexOf('overall')
  const mongo = script.indexOf('mongo')
  const storage = script.indexOf('storage')
  const save = script.indexOf('pm2 save')
  assert.ok(health >= 0 && overall > health)
  assert.ok(mongo > health && storage > health)
  assert.ok(save > overall && save > mongo && save > storage)
})

test('PM2 配置是根目录工作目录下的单 Java fork 实例', () => {
  const config = read('pm2-production.config.cjs')
  assert.match(config, /path\.resolve\(__dirname, '\.\.'\)/)
  assert.match(config, /name:\s*'recording-platform-backend'/)
  assert.match(config, /script:\s*'\/usr\/bin\/java'/)
  assert.match(config, /instances:\s*1/)
  assert.match(config, /exec_mode:\s*'fork'/)
  assert.match(config, /SERVER_ADDRESS:\s*'127\.0\.0\.1'/)
  assert.match(config, /SERVER_PORT:\s*'8080'/)
  assert.match(config, /\/var\/log\/recording-platform/)
})

test('Maven 配置只提供公开镜像且生产文件不包含秘密值', () => {
  const settings = read('maven-settings-production.xml')
  const combined = [
    read('deploy-production.sh'),
    read('pm2-production.config.cjs'),
    settings
  ].join('\n')
  assert.match(settings, /https:\/\/maven\.aliyun\.com\/repository\/public/)
  assert.match(settings, /<mirrorOf>\*<\/mirrorOf>/)
  assert.doesNotMatch(combined, /mongodb:\/\/[^<\s]+:[^<\s]+@/)
  assert.doesNotMatch(combined, /WECHAT_APP_SECRET\s*[:=]\s*['"][^'"]+/)
  assert.doesNotMatch(combined, /INITIAL_ADMIN_PASSWORD\s*[:=]\s*['"][^'"]+/)
})
