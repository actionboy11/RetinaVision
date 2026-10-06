$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$requiredFiles = @(
    'pom.xml'
    'frontend/package.json'
    'ai-service/requirements.txt'
    'README.md'
    'docs/BACKEND.md'
    'frontend/README.md'
    'ai-service/README.md'
    'API_CONTRACT.md'
)

$errors = [System.Collections.Generic.List[string]]::new()
foreach ($relativePath in $requiredFiles) {
    if (-not (Test-Path -LiteralPath (Join-Path $repoRoot $relativePath) -PathType Leaf)) {
        $errors.Add("Missing required monorepo file: $relativePath")
    }
}

$composePath = Join-Path $repoRoot 'docker/docker-compose.yml'
if (-not (Test-Path -LiteralPath $composePath -PathType Leaf)) {
    $errors.Add('Missing Docker Compose file: docker/docker-compose.yml')
} else {
    $compose = Get-Content -LiteralPath $composePath -Raw
    $expectedRoot = '${RETINAVISION_AI_PROJECT_ROOT:-../ai-service}'
    $expectedOccurrences = ([regex]::Matches($compose, [regex]::Escape($expectedRoot))).Count
    if ($expectedOccurrences -ne 4) {
        $errors.Add("Docker Compose must use $expectedRoot for the build context and three bind mounts; found $expectedOccurrences occurrences")
    }
    if ($compose.Contains('../../retinavision-ai')) {
        $errors.Add('Docker Compose still references the external ../../retinavision-ai path')
    }
}

if ($errors.Count -gt 0) {
    $errors | ForEach-Object { Write-Host "ERROR: $_" -ForegroundColor Red }
    exit 1
}

Write-Output "Monorepo layout verification passed for $($requiredFiles.Count) required files and Docker Compose."
