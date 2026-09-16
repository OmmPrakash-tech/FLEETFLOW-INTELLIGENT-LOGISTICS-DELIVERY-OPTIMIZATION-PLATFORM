param([string]$Goal = 'spring-boot:run')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$envFile = Join-Path $projectRoot '.env'
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^([A-Z_]+)=(.*)$') { [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process') }
    }
}
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to your JDK 25 installation.' }
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Push-Location (Join-Path $projectRoot 'backend')
try { & .\mvnw.cmd -B $Goal; exit $LASTEXITCODE } finally { Pop-Location }
