# fix_and_build.ps1 - fix SDK structure, install packages, build APK
$ErrorActionPreference = "Continue"
$proj = "C:\Users\viper\Projects\sentinel"
$sdkRoot = "$env:LOCALAPPDATA\Android\Sdk"
$btVer = "35.0.0"

Write-Output "=== FIX SDK STRUCTURE ==="
$ct = Join-Path $sdkRoot "cmdline-tools"
$inner = Join-Path $ct "cmdline-tools"
$latest = Join-Path $ct "latest"
if ((Test-Path $inner) -and (-not (Test-Path $latest))) {
    Move-Item $inner $latest
    Write-Output "Moved cmdline-tools\cmdline-tools -> cmdline-tools\latest"
}
$latestBin = Join-Path $latest "bin\sdkmanager.bat"
Write-Output ("sdkmanager present: " + (Test-Path $latestBin))
if (-not (Test-Path $latestBin)) { Write-Output "ERROR: no sdkmanager"; exit 1 }

Write-Output ""
Write-Output "=== INSTALL BUILD-TOOLS + PLATFORM ==="
$yes = Join-Path $env:TEMP "sdk_yes.txt"
Set-Content $yes ("y`n" * 40) -Encoding ASCII
$proc = Start-Process -FilePath $latestBin -ArgumentList "--sdk_root=$sdkRoot", "build-tools;$btVer", "platforms;android-34" -NoNewWindow -PassThru -RedirectStandardInput $yes
$proc.WaitForExit()
Write-Output ("sdkmanager exit: " + $proc.ExitCode)

$androidJar = Join-Path $sdkRoot "platforms\android-34\android.jar"
if (-not (Test-Path $androidJar)) { Write-Output "ERROR: android.jar missing"; exit 2 }
Write-Output "android.jar OK"

Write-Output ""
Write-Output "=== BUILD APK ==="
$bt = Join-Path $sdkRoot "build-tools\$btVer"
$work = Join-Path $proj "build\work"
if (Test-Path $work) { Remove-Item $work -Recurse -Force }
New-Item -ItemType Directory -Path $work -Force | Out-Null
$obj = Join-Path $work "obj"
New-Item -ItemType Directory -Path $obj -Force | Out-Null

$srcDir = Join-Path $proj "app\src\main\java"
$resDir = Join-Path $proj "app\src\main\res"
$assetsDir = Join-Path $proj "app\src\main\assets"
$manifest = Join-Path $proj "app\src\main\AndroidManifest.xml"

Write-Output "--- aapt2 compile ---"
$resOut = Join-Path $work "res"
New-Item -ItemType Directory -Path $resOut -Force | Out-Null
& (Join-Path $bt "aapt2.exe") compile --dir $resDir -o $resOut
if ($LASTEXITCODE -ne 0) { Write-Output "AAPT2 COMPILE FAILED"; exit 3 }

Write-Output "--- aapt2 link ---"
$apkUnlinked = Join-Path $work "app_unlinked.apk"
$flatFiles = Get-ChildItem (Join-Path $resOut "*.flat") | ForEach-Object { $_.FullName }
Write-Output ("flat file count: " + $flatFiles.Count)
& (Join-Path $bt "aapt2.exe") link -o $apkUnlinked -I $androidJar --manifest $manifest --java $obj --auto-add-overlay --rename-manifest-package com.cadmus.wifisentinel --min-sdk-version 24 --target-sdk-version 34 $flatFiles
if ($LASTEXITCODE -ne 0) { Write-Output "AAPT2 LINK FAILED"; exit 4 }
$Rjava = Get-ChildItem $obj -Filter "R.java" -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $Rjava) {
    Write-Output "ERROR: aapt2 did not generate R.java"
    & (Join-Path $bt "aapt2.exe") version
    exit 4
}
Write-Output ("R.java generated: " + $Rjava.FullName)

Write-Output "--- javac ---"
$javaFiles = (Get-ChildItem $srcDir -Filter "*.java" -Recurse | ForEach-Object { $_.FullName }) + (Get-ChildItem $obj -Filter "*.java" -Recurse | ForEach-Object { $_.FullName })
Write-Output ("compiling " + $javaFiles.Count + " java files")
& (Join-Path $env:JAVA_HOME "bin\javac.exe") --release 17 -classpath $androidJar -d $obj $javaFiles
if ($LASTEXITCODE -ne 0) { Write-Output "JAVAC FAILED"; exit 5 }

Write-Output "--- d8 ---"
$dexDir = Join-Path $work "dex"
New-Item -ItemType Directory -Path $dexDir -Force | Out-Null
$classFiles = Get-ChildItem $obj -Filter "*.class" -Recurse | ForEach-Object { $_.FullName }
$d8 = Join-Path $bt "d8.bat"
if (-not (Test-Path $d8)) { $d8 = Join-Path $bt "d8.exe" }
# d8 --output must be an EXISTING directory.
if (-not (Test-Path $dexDir)) { New-Item -ItemType Directory -Path $dexDir -Force | Out-Null }
& $d8 --lib $androidJar --min-api 24 --output $dexDir $classFiles
if ($LASTEXITCODE -ne 0) { Write-Output "D8 FAILED"; exit 6 }
if (-not (Test-Path (Join-Path $dexDir "classes.dex"))) { Write-Output "D8: classes.dex not produced"; exit 6 }
Write-Output "d8 OK: classes.dex produced"

Write-Output "--- assemble APK ---"
$apkUnsigned = Join-Path $work "app_unsigned.apk"
Copy-Item $apkUnlinked $apkUnsigned -Force
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zipObj = [System.IO.Compression.ZipFile]::Open($apkUnsigned, 'Update')
function Add-ZipEntry($zip, $srcPath, $entryName) {
    $entry = $zip.CreateEntry($entryName, [System.IO.Compression.CompressionLevel]::Optimal)
    $es = $entry.Open()
    $fs = [System.IO.File]::OpenRead($srcPath)
    $fs.CopyTo($es)
    $fs.Close()
    $es.Close()
}
Add-ZipEntry $zipObj (Join-Path $dexDir "classes.dex") "classes.dex"
Get-ChildItem $assetsDir -File | ForEach-Object {
    Add-ZipEntry $zipObj $_.FullName ("assets\" + $_.Name)
}
$zipObj.Dispose()
Write-Output "APK assembled with classes.dex + assets"

Write-Output "--- align + sign ---"
$apkAligned = Join-Path $work "app_aligned.apk"
& (Join-Path $bt "zipalign.exe") -f 4 $apkUnsigned $apkAligned
$ks = Join-Path $proj "build\wifisentinel.keystore"
if (-not (Test-Path $ks)) {
    & (Join-Path $env:JAVA_HOME "bin\keytool.exe") -genkeypair -keystore $ks -alias wifisentinel -keypass android -storepass android -dname "CN=Wi-Fi Sentinel, OU=Dev, O=Cadmus, L=Local, ST=Local, C=GB" -keyalg RSA -keysize 2048 -validity 10000
}
$outDir = Join-Path $proj "build\out"
New-Item -ItemType Directory -Path $outDir -Force | Out-Null
$apkOut = Join-Path $outDir "wifisentinel.apk"
& (Join-Path $bt "apksigner.bat") sign --ks $ks --ks-key-alias wifisentinel --ks-pass "pass:android" --key-pass "pass:android" --out $apkOut $apkAligned
if ($LASTEXITCODE -ne 0) { Write-Output "APKSIGNER FAILED"; exit 7 }

Write-Output ""
Write-Output "=== BUILD COMPLETE ==="
Write-Output ("APK: " + $apkOut)
Write-Output ("Size: " + (Get-Item $apkOut).Length + " bytes")
