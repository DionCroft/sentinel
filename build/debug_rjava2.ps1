$sdkRoot = "$env:LOCALAPPDATA\Android\Sdk"
$bt = Join-Path $sdkRoot "build-tools\34.0.0"
$androidJar = Join-Path $sdkRoot "platforms\android-34\android.jar"
$proj = "C:\Users\viper\Projects\sentinel"
$work = Join-Path $proj "build\work"
$manifest = Join-Path $proj "app\src\main\AndroidManifest.xml"
$resOut = Join-Path $work "res"
$flatFiles = Get-ChildItem $resOut -Filter "*.flat" | ForEach-Object { $_.FullName }

$obj = Join-Path $work "test3"
if (Test-Path $obj) { Remove-Item $obj -Recurse -Force }
New-Item -ItemType Directory -Path $obj -Force | Out-Null

Write-Output "=== TEST 3: exact build-script flags ==="
$apkUnlinked = Join-Path $work "t3.apk"
& (Join-Path $bt "aapt2.exe") link -o $apkUnlinked -I $androidJar --manifest $manifest --java $obj --auto-add-overlay --rename-manifest-package com.cadmus.wifisentinel --min-sdk-version 24 --target-sdk-version 34 $flatFiles
Write-Output ("exit: " + $LASTEXITCODE)
Write-Output "--- obj contents ---"
Get-ChildItem $obj -Recurse -ErrorAction SilentlyContinue | ForEach-Object { Write-Output $_.FullName }
Write-Output ("R.java at obj\com\cadmus\wifisentinel\R.java: " + (Test-Path (Join-Path $obj "com\cadmus\wifisentinel\R.java")))

Write-Output ""
Write-Output "=== TEST 4: WITHOUT --auto-add-overlay ==="
$obj4 = Join-Path $work "test4"
New-Item -ItemType Directory -Path $obj4 -Force | Out-Null
& (Join-Path $bt "aapt2.exe") link -o (Join-Path $work "t4.apk") -I $androidJar --manifest $manifest --java $obj4 --rename-manifest-package com.cadmus.wifisentinel --min-sdk-version 24 --target-sdk-version 34 $flatFiles
Write-Output ("exit: " + $LASTEXITCODE)
Get-ChildItem $obj4 -Recurse -ErrorAction SilentlyContinue | ForEach-Object { Write-Output $_.FullName }
