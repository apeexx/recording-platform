param([string]$ConfirmDatabase = '')
$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$envFile = Join-Path $repositoryRoot '.env'

if ($ConfirmDatabase -cne 'recording_platform') {
    throw 'Reset refused: pass the exact confirmation word recording_platform.'
}

if (Test-Path -LiteralPath $envFile) {
    foreach ($line in Get-Content -LiteralPath $envFile) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith('#') -or -not $trimmed.Contains('=')) { continue }
        $parts = $trimmed.Split('=', 2)
        if (-not [Environment]::GetEnvironmentVariable($parts[0], 'Process')) {
            [Environment]::SetEnvironmentVariable($parts[0], $parts[1], 'Process')
        }
    }
}

if (-not $env:INITIAL_ADMIN_USERNAME -or -not $env:INITIAL_ADMIN_PASSWORD) {
    throw 'Reset refused: configure INITIAL_ADMIN_USERNAME and INITIAL_ADMIN_PASSWORD in the local .env first.'
}
if ($env:MONGODB_URI -and $env:MONGODB_URI -notmatch '/recording_platform(?:\?|$)') {
    throw 'Reset refused: MONGODB_URI must target the recording_platform database exactly.'
}

$env:RECORDING_LOCAL_RESET_ENABLED = 'true'
$env:RECORDING_LOCAL_RESET_CONFIRMATION = $ConfirmDatabase
Push-Location (Join-Path $repositoryRoot 'backend')
try {
    & .\mvnw.cmd spring-boot:run '-Dspring-boot.run.arguments=--spring.main.web-application-type=none'
    if ($LASTEXITCODE -ne 0) { throw "Local data reset failed with exit code $LASTEXITCODE" }
} finally {
    Pop-Location
    Remove-Item Env:RECORDING_LOCAL_RESET_ENABLED -ErrorAction SilentlyContinue
    Remove-Item Env:RECORDING_LOCAL_RESET_CONFIRMATION -ErrorAction SilentlyContinue
}
