$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$workflowRoot = Join-Path $repoRoot '.github/workflows'
$errors = [System.Collections.Generic.List[string]]::new()

function Read-Workflow([string]$name) {
    $path = Join-Path $workflowRoot $name
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        $script:errors.Add("Missing workflow: .github/workflows/$name")
        return ''
    }
    return Get-Content -LiteralPath $path -Raw
}

function Require-Text([string]$content, [string]$needle, [string]$message) {
    if (-not $content.Contains($needle)) {
        $script:errors.Add($message)
    }
}

$sharedTriggers = @('.gitignore', 'docker/**', 'scripts/**', 'README.md', 'API_CONTRACT.md')

$backend = Read-Workflow 'backend-ci.yml'
if ($backend) {
    Require-Text $backend 'actions/checkout@v6' 'Backend CI must use actions/checkout@v6'
    Require-Text $backend 'actions/setup-java@v5' 'Backend CI must use actions/setup-java@v5'
    Require-Text $backend "java-version: '17'" 'Backend CI must use Java 17'
    Require-Text $backend 'mvn test' 'Backend CI must run mvn test'
    Require-Text $backend '3307:3306' 'Backend CI must expose MySQL on 3307'
    Require-Text $backend '6379:6379' 'Backend CI must expose Redis on 6379'
    Require-Text $backend '5672:5672' 'Backend CI must expose RabbitMQ on 5672'
    Require-Text $backend 'ALTER DATABASE retina_vision CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci' 'Backend CI must normalize the empty database collation before Flyway'
    Require-Text $backend 'RETINA_REPORT_FONT_PATH' 'Backend CI must configure the report font path'
    Require-Text $backend 'fonts-noto-cjk' 'Backend CI must install a Chinese report font'
    Require-Text $backend 'src/**' 'Backend CI must trigger for backend source changes'
    Require-Text $backend 'pom.xml' 'Backend CI must trigger for pom.xml changes'
    foreach ($trigger in $sharedTriggers) {
        Require-Text $backend $trigger "Backend CI must trigger for $trigger changes"
    }
}

$frontend = Read-Workflow 'frontend-ci.yml'
if ($frontend) {
    Require-Text $frontend 'actions/checkout@v6' 'Frontend CI must use actions/checkout@v6'
    Require-Text $frontend 'actions/setup-node@v6' 'Frontend CI must use actions/setup-node@v6'
    Require-Text $frontend "node-version: '20'" 'Frontend CI must use Node 20'
    Require-Text $frontend 'cache-dependency-path: frontend/package-lock.json' 'Frontend CI must cache from the frontend lockfile'
    Require-Text $frontend 'working-directory: frontend' 'Frontend CI steps must run in frontend'
    Require-Text $frontend 'npm ci' 'Frontend CI must run npm ci'
    Require-Text $frontend 'npm run type-check' 'Frontend CI must run type-check'
    Require-Text $frontend 'npm run build' 'Frontend CI must run build'
    Require-Text $frontend 'frontend/**' 'Frontend CI must trigger for frontend changes'
    foreach ($trigger in $sharedTriggers) {
        Require-Text $frontend $trigger "Frontend CI must trigger for $trigger changes"
    }
}

$ai = Read-Workflow 'ai-service-ci.yml'
if ($ai) {
    Require-Text $ai 'actions/checkout@v6' 'AI Service CI must use actions/checkout@v6'
    Require-Text $ai 'actions/setup-python@v6' 'AI Service CI must use actions/setup-python@v6'
    Require-Text $ai "python-version: '3.11'" 'AI Service CI must use Python 3.11'
    Require-Text $ai 'working-directory: ai-service' 'AI Service CI steps must run in ai-service'
    Require-Text $ai 'https://download.pytorch.org/whl/cpu' 'AI Service CI must install CPU PyTorch'
    Require-Text $ai 'python -m pytest -q tests -m "not model_integration"' 'AI Service CI must exclude the real checkpoint test'
    Require-Text $ai 'ai-service/**' 'AI Service CI must trigger for AI service changes'
    foreach ($trigger in $sharedTriggers) {
        Require-Text $ai $trigger "AI Service CI must trigger for $trigger changes"
    }
}

$secretScan = Read-Workflow 'secret-scan.yml'
if ($secretScan) {
    Require-Text $secretScan 'fetch-depth: 0' 'Secret Scan must fetch full history'
    Require-Text $secretScan 'pull-requests: read' 'Secret Scan must be able to inspect pull request commits'
    if ($secretScan -match '(?m)^\s+paths(?:-ignore)?:') {
        $errors.Add('Secret Scan must not use path filters')
    }
}

if ($errors.Count -gt 0) {
    $errors | ForEach-Object { Write-Host "ERROR: $_" -ForegroundColor Red }
    exit 1
}

Write-Output 'Workflow verification passed for Backend, Frontend, AI Service, and Secret Scan.'
