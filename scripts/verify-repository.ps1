param(
    [switch]$CheckIndex
)

$ErrorActionPreference = 'Stop'
$maxTrackedBytes = 10MB

function Invoke-GitLines {
    param([string[]]$Arguments)

    $output = & git @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "git $($Arguments -join ' ') failed"
    }
    return @($output | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
}

function Test-AllowedExampleEnvironmentFile {
    param([string]$Path)

    return $Path -match '(^|/)\.env[^/]*\.example$'
}

function Get-ForbiddenReason {
    param([string]$Path)

    $normalized = $Path.Replace('\', '/')
    $name = [System.IO.Path]::GetFileName($normalized)

    if ($normalized -match '\.pth$') { return 'model checkpoint' }
    if (($name -eq '.env' -or $name -like '.env.*') -and
        -not (Test-AllowedExampleEnvironmentFile $normalized)) { return 'local environment file' }
    if ($name -match '^application-.+\.(yaml|yml)$') { return 'local Spring configuration' }
    if ($normalized -match '^(uploads|storage|storage-e2e|logs|node_modules|dist|target|\.venv|__pycache__|\.pytest_cache|\.idea|\.vscode|\.worktrees)(/|$)' -or
        $normalized -match '^(frontend|ai-service)/(uploads|storage|storage-e2e|logs|node_modules|dist|target|\.venv|__pycache__|\.pytest_cache)(/|$)') {
        return 'generated, local, or runtime directory'
    }
    if ($normalized -match '\.sql$' -and
        $normalized -notmatch '^src/main/resources/db/migration/[^/]+\.sql$' -and
        $normalized -notmatch '^docker/mysql/migration/[^/]+\.sql$' -and
        $normalized -ne 'docker/mysql/init.sql') {
        return 'database export or unapproved SQL file'
    }
    return $null
}

$hashByPath = @{}
foreach ($entry in (Invoke-GitLines @('ls-files', '--stage'))) {
    if ($entry -match '^\d+\s+([0-9a-f]+)\s+\d+\t(.+)$') {
        $hashByPath[$Matches[2].Replace('\', '/')] = $Matches[1]
    }
}
$sizeByHash = @{}
$uniqueHashes = @($hashByPath.Values | Sort-Object -Unique)
if ($uniqueHashes.Count -gt 0) {
    $batch = $uniqueHashes | & git cat-file '--batch-check=%(objectname) %(objectsize)'
    if ($LASTEXITCODE -ne 0) { throw 'Unable to read staged blob sizes' }
    foreach ($line in $batch) {
        $parts = $line -split '\s+'
        if ($parts.Count -eq 2) { $sizeByHash[$parts[0]] = [long]$parts[1] }
    }
}

$paths = if ($CheckIndex) {
    Invoke-GitLines @('diff', '--cached', '--name-only', '--diff-filter=ACMR')
} else {
    Invoke-GitLines @('ls-files')
}

$failures = [System.Collections.Generic.List[string]]::new()
foreach ($path in $paths) {
    $normalized = $path.Replace('\', '/')
    $reason = Get-ForbiddenReason $normalized
    if ($reason) {
        $failures.Add("$normalized [$reason]")
        continue
    }
    $hash = $hashByPath[$normalized]
    $size = if ($hash -and $sizeByHash.ContainsKey($hash)) { $sizeByHash[$hash] } else { 0 }
    if ($size -gt $maxTrackedBytes) {
        $failures.Add("$normalized [tracked file exceeds 10 MB]")
    }
}

if ($failures.Count -gt 0) {
    Write-Error ("Repository safety verification failed:`n - " + ($failures -join "`n - "))
    exit 1
}

$scope = if ($CheckIndex) { 'staged files' } else { 'tracked files' }
Write-Output "Repository safety verification passed for $($paths.Count) $scope."
