$sdkRoot = "$env:LOCALAPPDATA\Android\Sdk"
$bt = Join-Path $sdkRoot "build-tools\34.0.0"
$androidJar = Join-Path $sdkRoot "platforms\android-34\android.jar"
$proj = "C:\Users\viper\Projects\sentinel"
$work = Join-Path $proj "build\work"
$manifest = Join-Path $proj "app\src\main\AndroidManifest.xml"
$resOut = Join-Path $work "res"
$flatFiles = Get-ChildItem $resOut -Filter "*.flat" | ForEach-Object { $_.FullName }

# Test 1: current flags
$obj1 = Join-Path $work "test1"
New-Item -ItemType Directory -Path $obj1 -Force | Out-Null
Write-Output "=== TEST 1: --java only ==="
& (Join-Path $bt "aapt2.exe") link -o (Join-Path $work "t1.apk") -I $androidJar --manifest $manifest --java $obj1 --min-sdk-version 24 --target-sdk-version 34 $flatFiles
Write-Output ("exit: " + $LASTEXITCODE + "  R.java: " + (Test-Path (Join-Path $obj1 "R.java")))

# Test 2: with --rename-manifest-package
$obj2 = Join-Path $work "test2"
New-Item -ItemType Directory -Path $obj2 -Force | Out-Null
Write-Output "=== TEST 2: --java + --rename-manifest-package ==="
& (Join-Path $bt "aapt2.exe") link -o (Join-Path $work "t2.apk") -I $androidJar --manifest $manifest --java $obj2 --rename-manifest-package com.cadmus.wifisentinel --min-sdk-version 24 --target-sdk-version 34 $flatFiles
Write-Output ("exit: " + $LASTEXITCODE + "  R.java: " + (Join-Path $obj2 "R.java") | Test-Path)
Get-ChildItem $obj2 -Recurse -ErrorAction SilentlyContinue | ForEach-Object { Write-Output $_.Name }

# Test 3: check aapt2 link help for R.java flag
Write-Output "=== aapt2 link help (grep java/R) ==="
& (Join-Path $bt "aapt2.exe") link --help 2>&1 | Select-String -Pattern "java|R\.java|package" | Select-Object -First 15
