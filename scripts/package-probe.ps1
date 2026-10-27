$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$toolchain = Get-Content (Join-Path $projectRoot '_archive\toolchain\toolchain.json') -Raw | ConvertFrom-Json
$env:JAVA_HOME = $toolchain.javaHome
$env:DEBUG = ''
$apk = Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk'
& (Join-Path $toolchain.sdkRoot 'build-tools\36.0.0\apksigner.bat') verify --verbose $apk
if ($LASTEXITCODE -ne 0) { throw 'Generated APK signature verification failed' }
$releaseDirectory = Join-Path $projectRoot 'releases'
New-Item -ItemType Directory -Path $releaseDirectory -Force | Out-Null
$destination = Join-Path $releaseDirectory 'xiaomigpt-probe-v0.1.0.apk'
Copy-Item -LiteralPath $apk -Destination $destination -Force
$digest = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllText((Join-Path $releaseDirectory 'SHA256SUMS.txt'), "$digest  xiaomigpt-probe-v0.1.0.apk`n")
Get-Item -LiteralPath $destination | Select-Object FullName,Length
Write-Output "SHA256: $digest"
