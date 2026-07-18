import { copyFile, mkdir, readFile, writeFile } from 'node:fs/promises'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const packageRoot = join(root, 'node_modules', '@breezystack', 'lamejs')
const metadata = JSON.parse(await readFile(join(packageRoot, 'package.json'), 'utf8'))
if (metadata.version !== '1.2.7') throw new Error(`Unexpected lamejs version: ${metadata.version}`)
const target = join(root, 'vendor')
await mkdir(target, { recursive: true })
const candidates = ['dist/lamejs.iife.js', 'lame.all.js', 'lame.min.js']
let source
for (const candidate of candidates) { try { source = await readFile(join(packageRoot, candidate), 'utf8'); break } catch {} }
if (!source) throw new Error('Unable to locate lamejs browser bundle')
await writeFile(join(target, 'lamejs.iife.js'), `// Generated from @breezystack/lamejs 1.2.7; do not edit.\n${source}\nif (typeof module !== 'undefined') module.exports = lamejs;\n`)
try { await copyFile(join(packageRoot, 'LICENSE'), join(target, 'LICENSE.lamejs')) } catch { await copyFile(join(packageRoot, 'LICENSE.txt'), join(target, 'LICENSE.lamejs')) }
