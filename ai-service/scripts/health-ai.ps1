param(
    [string]$BaseUrl = 'http://127.0.0.1:8000'
)

$ErrorActionPreference = 'Stop'
$Health = Invoke-RestMethod -Method Get -Uri "$($BaseUrl.TrimEnd('/'))/health" -TimeoutSec 5
if ($Health.status -ne 'UP' -or -not $Health.ready) {
    Write-Error "RetinaVision AI is not ready. status=$($Health.status), ready=$($Health.ready)"
    exit 1
}

$Health | ConvertTo-Json -Depth 4
