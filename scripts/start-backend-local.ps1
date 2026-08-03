param(
    [string]$DatabaseUrl = "jdbc:oracle:thin:@//localhost:1521/FREEPDB1",
    [string]$DatabaseUsername = "EVALUATION_APP",
    [string]$DatabasePassword = "EvaluationApp123!"
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Backend = Join-Path $Root "backend"

$env:DATABASE_URL = $DatabaseUrl
$env:DATABASE_USERNAME = $DatabaseUsername
$env:DATABASE_PASSWORD = $DatabasePassword
$env:SPRING_PROFILES_ACTIVE = "local"
$env:SPRING_MAIN_LOG_STARTUP_INFO = "true"
$env:LOGGING_LEVEL_ROOT = "INFO"
$env:LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_BOOT_AUTOCONFIGURE = "WARN"
Remove-Item Env:DEBUG -ErrorAction SilentlyContinue

Push-Location $Backend
try {
    mvn spring-boot:run
}
finally {
    Pop-Location
}
