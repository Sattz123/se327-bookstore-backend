$ErrorActionPreference = 'Stop'
$previousToken = $env:SONAR_TOKEN
$previousHost = $env:SONAR_HOST_URL
Push-Location (Split-Path $PSScriptRoot -Parent)

try {
    if (Test-Path -LiteralPath '.env.sonar') {
        foreach ($line in Get-Content -LiteralPath '.env.sonar') {
            if ($line -match '^(SONAR_HOST_URL|SONAR_TOKEN)=(.+)$') {
                Set-Item -LiteralPath "Env:$($Matches[1])" -Value $Matches[2].Trim()
            }
        }
    }

    if ([string]::IsNullOrWhiteSpace($env:SONAR_TOKEN) -or
        [string]::IsNullOrWhiteSpace($env:SONAR_HOST_URL)) {
        throw 'Set SONAR_HOST_URL and SONAR_TOKEN in .env.sonar or your environment first.'
    }

    & .\mvnw.cmd --batch-mode --no-transfer-progress clean verify org.sonarsource.scanner.maven:sonar-maven-plugin:sonar
    if ($LASTEXITCODE -ne 0) {
        throw 'Backend verification or SonarQube analysis/quality gate failed. Review the output above.'
    }
}
finally {
    $env:SONAR_TOKEN = $previousToken
    $env:SONAR_HOST_URL = $previousHost
    Pop-Location
}
