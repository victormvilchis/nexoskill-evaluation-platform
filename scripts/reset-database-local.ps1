[CmdletBinding(DefaultParameterSetName = 'Preview')]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$DatabaseUrl,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z][A-Za-z0-9_$#]*$')]
    [string]$DatabaseUsername,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$DatabasePassword,

    [ValidatePattern('^[^@\s]+@[^@\s]+$')]
    [string]$PreserveAdministratorEmail = 'admin01@gmail.com',

    [Parameter(Mandatory = $true, ParameterSetName = 'Preview')]
    [switch]$PreviewOnly,

    [Parameter(Mandatory = $true, ParameterSetName = 'Execute')]
    [switch]$ConfirmReset,

    [string]$SqlPlusPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$ExpectedCommit = 'f0905099b77e850d4037bce02fdedaaf867dca6f'
$ExpectedBranch = 'feature/multitenancy-core'
$ExpectedSchema = 'EVALUATION_APP'
$BackupRoot = 'C:\xampp\htdocs\nexoskill-update-backups'

function Resolve-SqlPlus {
    param([string]$ExplicitPath)

    if (-not [string]::IsNullOrWhiteSpace($ExplicitPath)) {
        if (-not (Test-Path -LiteralPath $ExplicitPath -PathType Leaf)) {
            throw "No existe SQL*Plus en la ruta indicada: $ExplicitPath"
        }
        return (Resolve-Path -LiteralPath $ExplicitPath).Path
    }

    foreach ($commandName in @('sqlplus.exe', 'sqlplus')) {
        $command = Get-Command $commandName -ErrorAction SilentlyContinue
        if ($null -ne $command) {
            return $command.Source
        }
    }

    if (-not [string]::IsNullOrWhiteSpace($env:ORACLE_HOME)) {
        $candidate = Join-Path $env:ORACLE_HOME 'bin\sqlplus.exe'
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            return $candidate
        }
    }

    throw 'No se encontro SQL*Plus. Agrega sqlplus.exe al PATH o usa -SqlPlusPath con su ruta completa.'
}

function Invoke-GitText {
    param(
        [Parameter(Mandatory = $true)][string]$WorkingDirectory,
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )

    $output = & git -C $WorkingDirectory @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Git fallo al ejecutar: git $($Arguments -join ' ')`n$($output -join [Environment]::NewLine)"
    }
    return (($output | ForEach-Object { [string]$_ }) -join "`n").Trim()
}

try {
    $scriptPath = $MyInvocation.MyCommand.Path
    $scriptsDirectory = Split-Path -Parent $scriptPath
    $projectPath = [System.IO.Path]::GetFullPath((Join-Path $scriptsDirectory '..'))
    $sqlScript = Join-Path $projectPath 'database\maintenance\reset-transactional-data.sql'

    if (-not (Test-Path -LiteralPath (Join-Path $projectPath '.git') -PathType Container)) {
        throw "El script debe ejecutarse dentro del repositorio Git: $projectPath"
    }

    $branch = Invoke-GitText -WorkingDirectory $projectPath -Arguments @('branch', '--show-current')
    if ($branch -ne $ExpectedBranch) {
        throw "Rama incorrecta. Esperada: $ExpectedBranch. Actual: $branch"
    }

    $head = Invoke-GitText -WorkingDirectory $projectPath -Arguments @('rev-parse', 'HEAD')
    if ($head -ne $ExpectedCommit) {
        throw "Commit base incorrecto. Esperado: $ExpectedCommit. Actual: $head"
    }

    if (-not (Test-Path -LiteralPath $sqlScript -PathType Leaf)) {
        throw "No se encontro el script SQL de mantenimiento: $sqlScript"
    }

    if ($DatabaseUsername.ToUpperInvariant() -ne $ExpectedSchema) {
        throw "La limpieza solo esta autorizada para el esquema $ExpectedSchema. Usuario recibido: $DatabaseUsername"
    }

    $jdbcPrefix = 'jdbc:oracle:thin:@'
    if (-not $DatabaseUrl.StartsWith($jdbcPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "URL JDBC no valida. Debe iniciar con: $jdbcPrefix"
    }

    $connectIdentifier = $DatabaseUrl.Substring($jdbcPrefix.Length).Trim()
    if ([string]::IsNullOrWhiteSpace($connectIdentifier)) {
        throw 'La URL JDBC no contiene un identificador de conexion Oracle.'
    }

    $sqlPlus = Resolve-SqlPlus -ExplicitPath $SqlPlusPath

    if (-not (Test-Path -LiteralPath $BackupRoot -PathType Container)) {
        New-Item -ItemType Directory -Path $BackupRoot -Force | Out-Null
    }

    $mode = if ($ConfirmReset) { 'execute' } else { 'preview' }
    $timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $logDirectory = Join-Path $BackupRoot "database-transactional-reset-$timestamp"
    New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
    $logPath = Join-Path $logDirectory "reset-$mode.log"

    $escapedPassword = $DatabasePassword.Replace('"', '""')
    $normalizedSqlPath = ([System.IO.Path]::GetFullPath($sqlScript)).Replace('\', '/')
    $executeValue = if ($ConfirmReset) { 'YES' } else { 'NO' }

    $inputLines = @(
        'SET ECHO OFF',
        'SET TERMOUT ON',
        "CONNECT `"$DatabaseUsername`"/`"$escapedPassword`"@$connectIdentifier",
        "DEFINE preserve_admin_email = $($PreserveAdministratorEmail.ToLowerInvariant())",
        "DEFINE execute_reset = $executeValue",
        "@`"$normalizedSqlPath`""
    )

    Write-Host ''
    Write-Host 'NexoSkill database maintenance' -ForegroundColor Cyan
    Write-Host "Mode:                 $mode"
    Write-Host "Schema:               $DatabaseUsername"
    Write-Host "Administrator kept:   $PreserveAdministratorEmail"
    Write-Host "SQL*Plus:             $sqlPlus"
    Write-Host "Log:                  $logPath"
    Write-Host ''

    if ($ConfirmReset) {
        Write-Host 'WARNING: This operation permanently deletes transactional data after all validations pass.' -ForegroundColor Yellow
        Write-Host 'The SQL script uses a single transaction and rolls back completely if an error occurs.' -ForegroundColor Yellow
        Write-Host ''
    }

    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $sqlPlus
    $startInfo.Arguments = '-L /nolog'
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo

    try {
        if (-not $process.Start()) {
            throw 'No fue posible iniciar SQL*Plus.'
        }

        foreach ($line in $inputLines) {
            $process.StandardInput.WriteLine($line)
        }
        $process.StandardInput.Close()

        $stdout = $process.StandardOutput.ReadToEnd()
        $stderr = $process.StandardError.ReadToEnd()
        $process.WaitForExit()
        $exitCode = $process.ExitCode

        $combined = @()
        if (-not [string]::IsNullOrWhiteSpace($stdout)) { $combined += $stdout.TrimEnd() }
        if (-not [string]::IsNullOrWhiteSpace($stderr)) { $combined += $stderr.TrimEnd() }
        $logContent = $combined -join [Environment]::NewLine
        [System.IO.File]::WriteAllText($logPath, $logContent, (New-Object System.Text.UTF8Encoding($false)))

        if (-not [string]::IsNullOrWhiteSpace($logContent)) {
            Write-Host $logContent
        }

        if ($exitCode -ne 0) {
            throw "SQL*Plus termino con codigo $exitCode. La operacion fue revertida. Revisa el log: $logPath"
        }
    } finally {
        $process.Dispose()
    }

    Write-Host ''
    if ($ConfirmReset) {
        Write-Host 'La limpieza transaccional termino correctamente.' -ForegroundColor Green
    } else {
        Write-Host 'La vista previa termino correctamente. No se modifico informacion.' -ForegroundColor Green
    }
    Write-Host "Log: $logPath"
} catch {
    Write-Error $_.Exception.Message
    exit 1
}
