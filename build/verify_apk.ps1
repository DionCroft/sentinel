$sdkRoot = "$env:LOCALAPPDATA\Android\Sdk"
$bt = Join-Path $sdkRoot "build-tools\35.0.0"
$apk = "C:\Users\viper\Projects\sentinel\build\out\wifisentinel.apk"

Write-Output "=== APK size ==="
Write-Output ((Get-Item $apk).Length + " bytes")

Write-Output "=== apksigner verify ==="
& (Join-Path $bt "apksigner.bat") verify --verbose $apk 2>&1 | Select-Object -First 12

Write-Output "=== aapt badging ==="
& (Join-Path $bt "aapt.exe") dump badging $apk 2>&1 | Select-Object -First 8

Write-Output "=== dexdump (class count) ==="
& (Join-Path $bt "dexdump.exe") $apk 2>&1 | Select-String -Pattern "Class descriptor" | Measure-Object | Select-Object -ExpandProperty Count

Write-Output "=== APK entries (key) ==="
Add-Type -AssemblyName System.IO.Compression.FileSystem
$z = [System.IO.Compression.ZipFile]::OpenRead($apk)
$z.Entries | Where-Object { $_.FullName -match "classes.dex|assets/|AndroidManifest|resources.arsc" } | ForEach-Object { Write-Output ($_.FullName + " (" + $_.Length + ")") }
$z.Dispose()
