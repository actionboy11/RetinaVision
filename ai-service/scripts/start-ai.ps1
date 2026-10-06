param(
    [string]$PythonPath = 'C:\develop\anaconda3\envs\retinavision-ai\python.exe',
    [int]$Port = 8000
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$ModelPath = Join-Path $ProjectRoot 'models\model_new.pth'
$StorageRoot = Join-Path $ProjectRoot 'storage'
$LogRoot = Join-Path $ProjectRoot 'logs'
$PidFile = Join-Path $StorageRoot 'retinavision-ai.pid'

if (-not (Test-Path -LiteralPath $PythonPath -PathType Leaf)) {
    throw "Python executable not found: $PythonPath"
}
if (-not (Test-Path -LiteralPath $ModelPath -PathType Leaf)) {
    throw "Model checkpoint not found: $ModelPath"
}
if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) {
    throw "Port $Port is already in use."
}

New-Item -ItemType Directory -Force -Path $StorageRoot, $LogRoot | Out-Null
if (Test-Path -LiteralPath $PidFile) {
    $ExistingPid = [int](Get-Content -LiteralPath $PidFile -Raw)
    if (Get-Process -Id $ExistingPid -ErrorAction SilentlyContinue) {
        throw "RetinaVision AI is already running with PID $ExistingPid."
    }
    Remove-Item -LiteralPath $PidFile -Force
}

$env:RETINAVISION_AI_MODEL_PATH = $ModelPath
$env:RETINAVISION_AI_STORAGE_ROOT = $StorageRoot
$StdoutLog = Join-Path $LogRoot 'retinavision-ai.stdout.log'
$StderrLog = Join-Path $LogRoot 'retinavision-ai.stderr.log'
$Arguments = @(
    '-m', 'uvicorn', 'retinavision_ai.api:app',
    '--app-dir', 'src', '--host', '127.0.0.1',
    '--port', $Port, '--workers', '1'
)

$Process = Start-Process -FilePath $PythonPath `
    -ArgumentList $Arguments `
    -WorkingDirectory $ProjectRoot `
    -RedirectStandardOutput $StdoutLog `
    -RedirectStandardError $StderrLog `
    -WindowStyle Hidden `
    -PassThru

Set-Content -LiteralPath $PidFile -Value $Process.Id -Encoding ascii
Write-Output "RetinaVision AI started. PID=$($Process.Id), health=http://127.0.0.1:$Port/health"
