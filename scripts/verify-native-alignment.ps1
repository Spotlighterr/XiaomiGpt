param([string]$Apk = 'app/build/outputs/apk/debug/app-debug.apk')
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $Apk))
try {
    $entries = @($archive.Entries | Where-Object { $_.FullName -match '^lib/(arm64-v8a|x86_64)/[^/]+\.so$' })
    if (!$entries.Count) { throw 'No 64-bit native libraries found' }
    foreach ($entry in $entries) {
        $memory = New-Object IO.MemoryStream
        $stream = $entry.Open()
        try { $stream.CopyTo($memory) } finally { $stream.Dispose() }
        $reader = New-Object IO.BinaryReader($memory)
        try {
            $memory.Position = 0
            if ($reader.ReadUInt32() -ne 0x464c457f) { throw 'Not ELF' }
            if ($reader.ReadByte() -ne 2 -or $reader.ReadByte() -ne 1) { throw 'Expected little-endian ELF64' }
            $memory.Position = 32; $programOffset = $reader.ReadUInt64()
            $memory.Position = 54; $entrySize = $reader.ReadUInt16(); $entryCount = $reader.ReadUInt16()
            $loads = 0
            for ($index = 0; $index -lt $entryCount; $index++) {
                $headerStart = $programOffset + $index * $entrySize
                $memory.Position = $headerStart
                if ($reader.ReadUInt32() -ne 1) { continue }
                $loads++
                $memory.Position = $headerStart + 8
                $fileOffset = $reader.ReadUInt64(); $virtualAddress = $reader.ReadUInt64()
                $memory.Position = $headerStart + 48; $alignment = $reader.ReadUInt64()
                if ($alignment -lt 16384 -or ($fileOffset % 16384) -ne ($virtualAddress % 16384)) { throw "16 KB ELF alignment failed: $($entry.FullName)" }
            }
            if (!$loads) { throw 'ELF has no LOAD segments' }
            Write-Output "16 KB ELF alignment verified: $($entry.FullName)"
        } finally { $reader.Dispose(); $memory.Dispose() }
    }
} finally { $archive.Dispose() }
