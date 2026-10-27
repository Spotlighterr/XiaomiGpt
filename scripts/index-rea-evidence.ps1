$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$expectedApk = '8757e57eb498071daccf4ff09b98be8072a2cc5c2f293a638605887c1a89d7a8'
$evidenceDirectory = Join-Path $projectRoot '_archive\rea\evidence'
$entries = @(Get-ChildItem -LiteralPath $evidenceDirectory -Filter '*.json' | Sort-Object Name | ForEach-Object {
    if ($_.Length -eq 0) { throw "Incomplete operation: $($_.Name)" }
    $evidence = Get-Content -LiteralPath $_.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($evidence.subject.digest.sha256 -ne $expectedApk) { throw "Unexpected APK identity: $($_.Name)" }
    $result = $evidence.normalized_result
    [ordered]@{
        file = '_archive/rea/evidence/' + $_.Name
        sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        evidence_id = $evidence.evidence_id
        operation = $evidence.operation
        class_name = $result.class_name
        method_name = $result.method.name
        query = $result.query
        representation = $result.representation
        source_status = $result.source.status
        fell_back = $result.fell_back
        authority = $evidence.authority
        confidence = $evidence.confidence
    }
})
$document = [ordered]@{
    date = '2026-10-27'
    input_file = 'eco-andriod-glass.apk'
    input_sha256 = $expectedApk
    input_authenticity = 'unverified; apksigner v3 integrity check failed'
    rea_version = '6.7.0'
    jadx_mcp_version = '0.7.1'
    jadx_jar_sha256 = '6e5eacf500b64292bfb73c49797c1958f6ee44646e43e868039ae7feb573ff75'
    execution = 'static only; input APK not installed or launched'
    entries = $entries
}
$outputPath = Join-Path $projectRoot 'docs\REA_EVIDENCE_INDEX_v1.json'
[IO.File]::WriteAllText($outputPath, ($document | ConvertTo-Json -Depth 8) + "`n")
Write-Output "Indexed $($entries.Count) REA records: $outputPath"
