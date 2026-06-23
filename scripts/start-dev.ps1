param(
  [switch]$Help
)

$ErrorActionPreference = 'Stop'

function Show-Help {
  Write-Host 'Recording Platform development startup script'
  Write-Host ''
  Write-Host 'Usage:'
  Write-Host '  powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-dev.ps1'
  Write-Host ''
  Write-Host 'Behavior:'
  Write-Host '  1. Check and stop processes listening on ports 8080 and 5173.'
  Write-Host '  2. Start backend: backend\mvnw.cmd spring-boot:run.'
  Write-Host '  3. Start frontend: npm run dev -- --host localhost --port 5173.'
  Write-Host '  4. Print the voice generation workbench URL.'
}

if ($Help) {
  Show-Help
  exit 0
}

$RepoRoot = Split-Path -Parent $PSScriptRoot
$BackendDir = Join-Path $RepoRoot 'backend'
$WebDir = Join-Path $RepoRoot 'apps\web'
$LogDir = Join-Path $RepoRoot 'logs'
$Ports = @(8080, 5173)

function Assert-PathExists {
  param(
    [string]$Path,
    [string]$Description
  )

  if (-not (Test-Path -LiteralPath $Path)) {
    throw "Missing $Description`: $Path"
  }
}

function Stop-PortProcess {
  param([int]$Port)

  $connections = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
  if (-not $connections) {
    Write-Host "Port $Port is free."
    return
  }

  $processIds = $connections |
    Select-Object -ExpandProperty OwningProcess -Unique |
    Where-Object { $_ -and $_ -gt 0 }

  foreach ($processId in $processIds) {
    $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
    $processName = if ($process) { $process.ProcessName } else { 'unknown' }
    Write-Host "Port $Port is busy; stopping PID $processId ($processName)."
    Stop-Process -Id $processId -Force
  }
}

function Wait-Port {
  param(
    [int]$Port,
    [string]$Name,
    [int]$TimeoutSeconds = 90
  )

  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  while ((Get-Date) -lt $deadline) {
    $connection = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    if ($connection) {
      Write-Host "$Name is listening on port $Port."
      return $true
    }
    Start-Sleep -Seconds 1
  }

  Write-Host "$Name did not listen on port $Port within $TimeoutSeconds seconds."
  return $false
}

Assert-PathExists -Path $BackendDir -Description 'backend directory'
Assert-PathExists -Path $WebDir -Description 'web directory'
Assert-PathExists -Path (Join-Path $BackendDir 'mvnw.cmd') -Description 'backend Maven Wrapper'

if (-not (Test-Path -LiteralPath $LogDir)) {
  New-Item -ItemType Directory -Path $LogDir | Out-Null
}

Write-Host 'Checking development ports...'
foreach ($port in $Ports) {
  Stop-PortProcess -Port $port
}

$backendOutLog = Join-Path $LogDir 'dev-backend.out.log'
$backendErrLog = Join-Path $LogDir 'dev-backend.err.log'
$frontendOutLog = Join-Path $LogDir 'dev-frontend.out.log'
$frontendErrLog = Join-Path $LogDir 'dev-frontend.err.log'

Write-Host 'Starting Spring Boot backend...'
$backendProcess = Start-Process `
  -FilePath (Join-Path $BackendDir 'mvnw.cmd') `
  -ArgumentList 'spring-boot:run' `
  -WorkingDirectory $BackendDir `
  -WindowStyle Hidden `
  -RedirectStandardOutput $backendOutLog `
  -RedirectStandardError $backendErrLog `
  -PassThru
Write-Host "Backend process PID: $($backendProcess.Id)"

Write-Host 'Starting Vite frontend...'
$frontendArgs = 'run dev -- --host localhost --port 5173'
$frontendProcess = Start-Process `
  -FilePath 'npm.cmd' `
  -ArgumentList $frontendArgs `
  -WorkingDirectory $WebDir `
  -WindowStyle Hidden `
  -RedirectStandardOutput $frontendOutLog `
  -RedirectStandardError $frontendErrLog `
  -PassThru
Write-Host "Frontend process PID: $($frontendProcess.Id)"

$backendReady = Wait-Port -Port 8080 -Name 'Backend' -TimeoutSeconds 120
$frontendReady = Wait-Port -Port 5173 -Name 'Frontend' -TimeoutSeconds 60

if (-not $backendReady -or -not $frontendReady) {
  Write-Host 'Development services failed to start. Check logs:'
  Write-Host "  Backend stdout: $backendOutLog"
  Write-Host "  Backend stderr: $backendErrLog"
  Write-Host "  Frontend stdout: $frontendOutLog"
  Write-Host "  Frontend stderr: $frontendErrLog"
  exit 1
}

Write-Host ''
Write-Host 'Development services are ready:'
Write-Host '  Backend: http://localhost:8080'
Write-Host '  Frontend: http://localhost:5173'
Write-Host '  Voice generation workbench: http://localhost:5173/admin/voice-generation/workbench'
Write-Host ''
Write-Host 'Log files:'
Write-Host "  Backend stdout: $backendOutLog"
Write-Host "  Backend stderr: $backendErrLog"
Write-Host "  Frontend stdout: $frontendOutLog"
Write-Host "  Frontend stderr: $frontendErrLog"
