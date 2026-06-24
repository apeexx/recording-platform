param(
	[switch] $Help,
	[string] $DatabaseName = "recording_platform",
	[string] $RoleName = "recording_platform",
	[string] $HostName = "localhost",
	[int] $Port = 5432,
	[string] $AdminUser = "postgres"
)

$ErrorActionPreference = "Stop"

function Show-Help {
	Write-Host "Create the PostgreSQL database and role for local development."
	Write-Host ""
	Write-Host "Usage:"
	Write-Host "  pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\create-postgres-db.ps1"
	Write-Host ""
	Write-Host "Optional parameters:"
	Write-Host "  -DatabaseName recording_platform"
	Write-Host "  -RoleName recording_platform"
	Write-Host "  -HostName localhost"
	Write-Host "  -Port 5432"
	Write-Host "  -AdminUser postgres"
	Write-Host ""
	Write-Host "The script uses psql and does not store or print database passwords."
}

if ($Help) {
	Show-Help
	exit 0
}

$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $RepoRoot

$PsqlCommand = Get-Command psql -ErrorAction SilentlyContinue
if (-not $PsqlCommand) {
	Write-Error "psql was not found. Install PostgreSQL and add its bin directory to PATH, then run this script again."
}

function Invoke-Psql {
	param(
		[string] $Database,
		[string] $Sql
	)

	& psql `
		--host $HostName `
		--port $Port `
		--username $AdminUser `
		--dbname $Database `
		--set ON_ERROR_STOP=1 `
		--command $Sql
}

$RoleExistsSql = "SELECT 1 FROM pg_roles WHERE rolname = '$RoleName';"
$DatabaseExistsSql = "SELECT 1 FROM pg_database WHERE datname = '$DatabaseName';"

Write-Host "Checking PostgreSQL role '$RoleName'..."
$RoleExists = & psql --host $HostName --port $Port --username $AdminUser --dbname postgres --tuples-only --no-align --command $RoleExistsSql
if (-not ($RoleExists -contains "1")) {
	Write-Host "Creating role '$RoleName'..."
	Invoke-Psql -Database "postgres" -Sql "CREATE ROLE $RoleName LOGIN;"
} else {
	Write-Host "Role '$RoleName' already exists."
}

Write-Host "Checking PostgreSQL database '$DatabaseName'..."
$DatabaseExists = & psql --host $HostName --port $Port --username $AdminUser --dbname postgres --tuples-only --no-align --command $DatabaseExistsSql
if (-not ($DatabaseExists -contains "1")) {
	Write-Host "Creating database '$DatabaseName'..."
	Invoke-Psql -Database "postgres" -Sql "CREATE DATABASE $DatabaseName OWNER $RoleName;"
} else {
	Write-Host "Database '$DatabaseName' already exists."
}

Write-Host "Granting database privileges..."
Invoke-Psql -Database "postgres" -Sql "GRANT ALL PRIVILEGES ON DATABASE $DatabaseName TO $RoleName;"

Write-Host "PostgreSQL database is ready."
Write-Host "Configure .env with:"
Write-Host "  SPRING_DATASOURCE_URL=jdbc:postgresql://$($HostName):$Port/$DatabaseName"
Write-Host "  SPRING_DATASOURCE_USERNAME=$RoleName"
Write-Host "  SPRING_DATASOURCE_PASSWORD: set this in .env if the role requires one"
