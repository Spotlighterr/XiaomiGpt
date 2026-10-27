$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$toolRoot = Join-Path $projectRoot '_archive\toolchain'
New-Item -ItemType Directory -Path $toolRoot -Force | Out-Null

function Get-VerifiedArchive($Url, $Destination, $Hash, $Algorithm = 'SHA256') {
    if (!(Test-Path -LiteralPath $Destination) -or (Get-FileHash -LiteralPath $Destination -Algorithm $Algorithm).Hash -ne $Hash) {
        & curl.exe --fail --location --silent --show-error --retry 2 --connect-timeout 30 --max-time 900 --output $Destination $Url
        if ($LASTEXITCODE -ne 0) { throw "Download failed: $Url" }
    }
    if ((Get-FileHash -LiteralPath $Destination -Algorithm $Algorithm).Hash -ne $Hash) { throw "Checksum mismatch: $Destination" }
}

$jdkRoot = Join-Path $toolRoot 'jdk'
if (!(Test-Path -LiteralPath $jdkRoot)) {
    Write-Output 'Downloading verified Temurin JDK 17...'
    $jdkAsset = @(Invoke-RestMethod 'https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse')[0]
    $jdkZip = Join-Path $toolRoot 'jdk.zip'
    Get-VerifiedArchive $jdkAsset.binary.package.link $jdkZip $jdkAsset.binary.package.checksum
    New-Item -ItemType Directory -Path $jdkRoot -Force | Out-Null
    & tar.exe -xf $jdkZip -C $jdkRoot
    if ($LASTEXITCODE -ne 0) { throw 'JDK extraction failed' }
}
$javaRoot = (Get-ChildItem -LiteralPath $jdkRoot -Directory | Select-Object -First 1).FullName
$env:JAVA_HOME = $javaRoot
$env:PATH = "$javaRoot\bin;$env:PATH"

$gradleRoot = Join-Path $toolRoot 'gradle-8.13'
if (!(Test-Path -LiteralPath $gradleRoot)) {
    Write-Output 'Downloading verified Gradle 8.13...'
    $gradleHash = (Invoke-RestMethod 'https://services.gradle.org/distributions/gradle-8.13-bin.zip.sha256').Trim()
    $gradleZip = Join-Path $toolRoot 'gradle.zip'
    Get-VerifiedArchive 'https://services.gradle.org/distributions/gradle-8.13-bin.zip' $gradleZip $gradleHash
    & tar.exe -xf $gradleZip -C $toolRoot
    if ($LASTEXITCODE -ne 0) { throw 'Gradle extraction failed' }
}

$sdkRoot = Join-Path $toolRoot 'android-sdk'
$cmdRoot = Join-Path $toolRoot 'android-commandline-12'
$sdkManager = Join-Path $cmdRoot 'cmdline-tools\bin\sdkmanager.bat'
if (!(Test-Path -LiteralPath $sdkManager)) {
    Write-Output 'Downloading verified Android command-line tools...'
    [xml]$androidRepository = (Invoke-WebRequest 'https://dl.google.com/android/repository/repository2-1.xml' -UseBasicParsing).Content
    $commandPackage = $androidRepository.SelectSingleNode("//*[local-name()='remotePackage' and @path='cmdline-tools;12.0']")
    $windowsArchive = $commandPackage.SelectSingleNode(".//*[local-name()='archive'][*[local-name()='host-os']='windows']/*[local-name()='complete']")
    if ($null -eq $windowsArchive) { throw 'Windows SDK archive not found in official repository' }
    $sdkUrl = 'https://dl.google.com/android/repository/' + $windowsArchive.url
    $sdkHash = $windowsArchive.SelectSingleNode("*[local-name()='checksum']").InnerText.Trim()
    $sdkHashAlgorithm = if ($sdkHash.Length -eq 64) { 'SHA256' } else { 'SHA1' }
    $sdkZip = Join-Path $toolRoot 'android-commandline-12.zip'
    Get-VerifiedArchive $sdkUrl $sdkZip $sdkHash $sdkHashAlgorithm
    New-Item -ItemType Directory -Path $cmdRoot -Force | Out-Null
    & tar.exe -xf $sdkZip -C $cmdRoot
    if ($LASTEXITCODE -ne 0) { throw 'SDK tools extraction failed' }
}

New-Item -ItemType Directory -Path $sdkRoot -Force | Out-Null
$env:ANDROID_USER_HOME = Join-Path $toolRoot 'android-user'
$env:GRADLE_USER_HOME = Join-Path $toolRoot 'gradle-cache'
Write-Output 'Installing SDK 36 and build tools into the project archive...'
(1..100 | ForEach-Object { 'y' }) | & $sdkManager "--sdk_root=$sdkRoot" --licenses | Out-File (Join-Path $toolRoot 'sdk-licenses.log')
if ($LASTEXITCODE -ne 0) { throw 'SDK license setup failed' }
& $sdkManager "--sdk_root=$sdkRoot" 'platforms;android-36' 'build-tools;36.0.0' 'platform-tools'
if ($LASTEXITCODE -ne 0) { throw 'SDK installation failed' }
if (!(Test-Path (Join-Path $sdkRoot 'platforms\android-36\android.jar')) -or !(Test-Path (Join-Path $sdkRoot 'build-tools\36.0.0\aapt2.exe'))) { throw 'SDK package files missing despite installer exit code' }

$sdkProperty = $sdkRoot.Replace('\','/').Replace(':','\:')
[IO.File]::WriteAllText((Join-Path $projectRoot 'local.properties'), "sdk.dir=$sdkProperty`n")
@{ javaHome = $javaRoot; sdkRoot = $sdkRoot; gradleRoot = $gradleRoot } | ConvertTo-Json | Set-Content (Join-Path $toolRoot 'toolchain.json')
Write-Output 'Project-local toolchain ready. Run scripts/build.ps1.'
