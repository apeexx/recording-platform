param(
  [switch]$Help
)

$ErrorActionPreference = 'Stop'

function Show-Help {
  Write-Host 'Recording Platform development startup script'
  Write-Host ''
  Write-Host 'Usage:'
  Write-Host '  Double-click scripts\start-dev.cmd'
  Write-Host '  pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-dev.ps1'
  Write-Host ''
  Write-Host 'Behavior:'
  Write-Host '  1. Check and stop processes listening on ports 8080 and 5173.'
  Write-Host '  2. Open a visible pwsh window for backend: backend\mvnw.cmd spring-boot:run.'
  Write-Host '  3. Open a visible pwsh window for frontend: npm run dev -- --host localhost --port 5173.'
  Write-Host '  4. Print the voice generation workbench URL.'
}

if ($Help) {
  Show-Help
  exit 0
}

$RepoRoot = Split-Path -Parent $PSScriptRoot
$BackendDir = Join-Path $RepoRoot 'backend'
$WebDir = Join-Path $RepoRoot 'apps\web'
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

function Assert-CommandExists {
  param([string]$Command)

  if (-not (Get-Command $Command -ErrorAction SilentlyContinue)) {
    throw "Missing command: $Command"
  }
}

function ConvertTo-PowerShellLiteral {
  param([string]$Value)

  return "'" + $Value.Replace("'", "''") + "'"
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
Assert-CommandExists -Command 'pwsh'

Write-Host 'Checking development ports...'
foreach ($port in $Ports) {
  Stop-PortProcess -Port $port
}

Write-Host 'Starting Spring Boot backend...'
$backendCommand = @"
`$Host.UI.RawUI.WindowTitle = 'Recording Backend'
Set-Location -LiteralPath $(ConvertTo-PowerShellLiteral $BackendDir)
& .\mvnw.cmd spring-boot:run
Write-Host ''
Read-Host 'Process ended. Press Enter to close this window'
"@
$backendProcess = Start-Process `
  -FilePath 'pwsh' `
  -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', $backendCommand) `
  -PassThru
Write-Host "Backend process PID: $($backendProcess.Id)"

Write-Host 'Starting Vite frontend...'
$frontendArgs = 'run dev -- --host localhost --port 5173'
$frontendCommand = @"
`$Host.UI.RawUI.WindowTitle = 'Recording Frontend'
Set-Location -LiteralPath $(ConvertTo-PowerShellLiteral $WebDir)
& npm.cmd $frontendArgs
Write-Host ''
Read-Host 'Process ended. Press Enter to close this window'
"@
$frontendProcess = Start-Process `
  -FilePath 'pwsh' `
  -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', $frontendCommand) `
  -PassThru
Write-Host "Frontend process PID: $($frontendProcess.Id)"

$backendReady = Wait-Port -Port 8080 -Name 'Backend' -TimeoutSeconds 120
$frontendReady = Wait-Port -Port 5173 -Name 'Frontend' -TimeoutSeconds 60

if (-not $backendReady -or -not $frontendReady) {
  Write-Host 'Development services failed to start. Check the backend and frontend pwsh windows.'
  exit 1
}

Write-Host ''
Write-Host 'Development services are ready:'
Write-Host '  Backend: http://localhost:8080'
Write-Host '  Frontend: http://localhost:5173'
Write-Host '  Voice generation workbench: http://localhost:5173/admin/voice-generation/workbench'
Write-Host ''
Write-Host 'Live output is shown in the Recording Backend and Recording Frontend pwsh windows.'
