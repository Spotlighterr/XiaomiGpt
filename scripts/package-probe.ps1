$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$toolchain = Get-Content (Join-Path $projectRoot '_archive\toolchain\toolchain.json') -Raw | ConvertFrom-Json
$env:JAVA_HOME = $toolchain.javaHome
$env:DEBUG = ''
$apk = Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk'
& (Join-Path $toolchain.sdkRoot 'build-tools\36.0.0\apksigner.bat') verify --verbose $apk
if ($LASTEXITCODE -ne 0) { throw 'Generated APK signature verification failed' }
$metadata = Get-Content (Join-Path $projectRoot 'app\build\outputs\apk\debug\output-metadata.json') -Raw | ConvertFrom-Json
$versionName = $metadata.elements[0].versionName
if ($versionName -notmatch '^([0-9]+\.[0-9]+\.[0-9]+)-probe$') { throw 'Unexpected probe version in APK metadata' }
$baseVersion = $Matches[1]
$releaseDirectory = Join-Path $projectRoot "releases\v$versionName"
New-Item -ItemType Directory -Path $releaseDirectory -Force | Out-Null
$apkName = "xiaomigpt-probe-v$baseVersion.apk"
$destination = Join-Path $releaseDirectory $apkName
Copy-Item -LiteralPath $apk -Destination $destination -Force
$digest = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllText((Join-Path $releaseDirectory 'SHA256SUMS.txt'), "$digest  $apkName`n")
Get-Item -LiteralPath $destination | Select-Object FullName,Length
Write-Output "SHA256: $digest"
