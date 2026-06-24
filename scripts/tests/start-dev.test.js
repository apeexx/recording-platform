import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { describe, it } from 'node:test'

const script = readFileSync(new URL('../start-dev.ps1', import.meta.url), 'utf8')
const cmdUrl = new URL('../start-dev.cmd', import.meta.url)
const launcher = existsSync(cmdUrl) ? readFileSync(cmdUrl, 'utf8') : ''
const postgresScriptUrl = new URL('../create-postgres-db.ps1', import.meta.url)
const postgresScript = existsSync(postgresScriptUrl) ? readFileSync(postgresScriptUrl, 'utf8') : ''

describe('start-dev.ps1', () => {
  it('checks and frees the fixed frontend and backend ports before startup', () => {
    assert.match(script, /\$Ports\s*=\s*@\(8080,\s*5173\)/)
    assert.match(script, /Stop-Process\s+-Id\s+\$processId\s+-Force/)
    assert.match(script, /Get-NetTCPConnection/)
  })

  it('starts Spring Boot backend and Vite frontend with the expected commands', () => {
    assert.match(script, /spring-boot:run/)
    assert.match(script, /npm/)
    assert.match(script, /run dev -- --host localhost --port 5173/)
    assert.match(script, /admin\/voice-generation\/workbench/)
  })

  it('opens visible pwsh windows for live backend and frontend logs', () => {
    assert.match(script, /Start-Process\s+`?\s*-FilePath\s+'pwsh'/)
    assert.match(script, /Recording Backend/)
    assert.match(script, /Recording Frontend/)
    assert.match(script, /Press Enter to close this window/)
    assert.doesNotMatch(script, /WindowStyle\s+Hidden/)
    assert.doesNotMatch(script, /RedirectStandardOutput/)
    assert.doesNotMatch(script, /RedirectStandardError/)
    assert.doesNotMatch(script, /\$LogDir/)
    assert.doesNotMatch(script, /logs[\\/]/i)
  })

  it('provides a cmd launcher for double-click startup', () => {
    assert.ok(existsSync(cmdUrl), 'scripts/start-dev.cmd should exist')
    assert.match(launcher, /pwsh\s+-NoProfile\s+-ExecutionPolicy\s+Bypass\s+-File\s+"%~dp0start-dev\.ps1"/)
  })

  it('does not embed secrets or authorization payloads', () => {
    assert.doesNotMatch(script, /sk-[A-Za-z0-9_-]+/)
    assert.doesNotMatch(script, /Authorization/i)
    assert.doesNotMatch(script, /MINIMAX_API_KEY\s*=/)
  })
})

describe('create-postgres-db.ps1', () => {
  it('creates a PostgreSQL database and role through psql without embedding a password', () => {
    assert.ok(existsSync(postgresScriptUrl), 'scripts/create-postgres-db.ps1 should exist')
    assert.match(postgresScript, /Get-Command\s+psql/)
    assert.match(postgresScript, /recording_platform/)
    assert.match(postgresScript, /CREATE DATABASE/)
    assert.match(postgresScript, /CREATE ROLE/)
    assert.match(postgresScript, /psql/)
    assert.doesNotMatch(postgresScript, /SPRING_DATASOURCE_PASSWORD\s*=/)
    assert.doesNotMatch(postgresScript, /sk-[A-Za-z0-9_-]+/)
    assert.doesNotMatch(postgresScript, /Authorization/i)
  })
})
