$commands = @("java", "mvn", "node", "npm")

foreach ($command in $commands) {
    $resolved = Get-Command $command -ErrorAction SilentlyContinue
    if ($null -eq $resolved) {
        Write-Host "[FALTA] $command" -ForegroundColor Red
    }
    else {
        Write-Host "[OK] $command -> $($resolved.Source)" -ForegroundColor Green
    }
}

Write-Host ""
Write-Host "Oracle debe instalarse y validarse por separado." -ForegroundColor Yellow
Write-Host "XAMPP solo se usa para Apache/frontend/proxy." -ForegroundColor Yellow
