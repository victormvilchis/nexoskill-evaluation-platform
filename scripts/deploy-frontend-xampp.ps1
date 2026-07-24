param(
    [string]$XamppHtdocs = "C:\xampp\htdocs",
    [string]$ApplicationFolder = "evaluaciones"
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$Frontend = Join-Path $Root "frontend"
$Destination = Join-Path $XamppHtdocs $ApplicationFolder

Write-Host "Compilando frontend..." -ForegroundColor Cyan
Push-Location $Frontend
try {
    if (-not (Test-Path "node_modules")) {
        npm install
    }
    npm run build
}
finally {
    Pop-Location
}

if (Test-Path $Destination) {
    Remove-Item $Destination -Recurse -Force
}

New-Item -ItemType Directory -Path $Destination -Force | Out-Null
Copy-Item (Join-Path $Frontend "dist\*") $Destination -Recurse -Force
Copy-Item (Join-Path $Frontend "public\.htaccess") `
    (Join-Path $Destination ".htaccess") -Force

Write-Host "Frontend desplegado en $Destination" -ForegroundColor Green
Write-Host "Abre http://localhost/$ApplicationFolder/" -ForegroundColor Green
