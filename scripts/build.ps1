param([string[]]$Tasks = @('testDebugUnitTest', 'lintDebug', 'assembleDebug'))
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$toolRoot = Join-Path $projectRoot '_archive\toolchain'
$toolchain = Get-Content (Join-Path $toolRoot 'toolchain.json') -Raw | ConvertFrom-Json
$env:JAVA_HOME = $toolchain.javaHome
$env:ANDROID_HOME = $toolchain.sdkRoot
$env:ANDROID_USER_HOME = Join-Path $toolRoot 'android-user'
$env:GRADLE_USER_HOME = Join-Path $toolRoot 'gradle-cache'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:DEBUG = ''
Push-Location $projectRoot
try {
    & (Join-Path $toolchain.gradleRoot 'bin\gradle.bat') --no-daemon --no-watch-fs --max-workers=2 --console=plain @Tasks
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
} finally { Pop-Location }
