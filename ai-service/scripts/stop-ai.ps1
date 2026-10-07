param(
    [string]$PythonPath = 'C:\develop\anaconda3\envs\retinavision-ai\python.exe'
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$PidFile = Join-Path $ProjectRoot 'storage\retinavision-ai.pid'

if (-not (Test-Path -LiteralPath $PidFile -PathType Leaf)) {
    Write-Output 'RetinaVision AI PID file does not exist; nothing to stop.'
    exit 0
}

$ProcessId = [int](Get-Content -LiteralPath $PidFile -Raw)
$Process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
if ($null -eq $Process) {
    Remove-Item -LiteralPath $PidFile -Force
    Write-Output "Removed stale PID file for PID $ProcessId."
    exit 0
}

if ($Process.Path -and ((Resolve-Path $Process.Path).Path -ne (Resolve-Path $PythonPath).Path)) {
    throw "PID $ProcessId does not belong to the configured Python executable."
}

Stop-Process -Id $ProcessId
Wait-Process -Id $ProcessId -Timeout 15 -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $PidFile -Force
Write-Output "RetinaVision AI stopped. PID=$ProcessId"
