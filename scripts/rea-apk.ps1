param(
    [Parameter(Mandatory=$true)][string]$Operation,
    [string]$ClassName,
    [string]$MethodName,
    [string]$Query,
    [int]$Overload = -1,
    [Parameter(Mandatory=$true)][string]$OutputName
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$toolchain = Get-Content (Join-Path $projectRoot '_archive\toolchain\toolchain.json') -Raw | ConvertFrom-Json
$env:JAVA_HOME = $toolchain.javaHome
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:REA_JADX_MCP_JAR = Join-Path $projectRoot '_archive\rea\jadx-headless-mcp-0.7.1-all.jar'
$env:REA_JADX_HEAP_MIB = '3072'
$env:REA_JADX_OPERATION_TIMEOUT_MS = '240000'
$env:DEBUG = ''
if ((Get-FileHash $env:REA_JADX_MCP_JAR -Algorithm SHA256).Hash.ToLowerInvariant() -ne '6e5eacf500b64292bfb73c49797c1958f6ee44646e43e868039ae7feb573ff75') { throw 'JADX checksum mismatch' }
if ($OutputName -notmatch '^[A-Za-z0-9_-]+$') { throw 'Use a simple output name' }
$outputDirectory = Join-Path $projectRoot '_archive\rea\evidence'
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
$reaArguments = @('-y', 'rea-agents@6.7.0', $Operation, (Join-Path $projectRoot 'eco-andriod-glass.apk'))
if ($ClassName) { $reaArguments += $ClassName }
if ($MethodName) { $reaArguments += $MethodName }
if ($PSBoundParameters.ContainsKey('Query')) { $reaArguments += $Query }
if ($Overload -ge 0) { $reaArguments += @('--overload-index', $Overload) }
$reaArguments += @('--format', 'json')
& npx.cmd @reaArguments | Out-File -LiteralPath (Join-Path $outputDirectory "$OutputName.json") -Encoding utf8
if ($LASTEXITCODE -ne 0) { throw "REA exited with code $LASTEXITCODE; see $OutputName.json" }
Write-Output "REA result saved: $OutputName.json"
