# build_apk.ps1
# Builds the Wi-Fi Sentinel APK using a direct (Gradle-free) build:
#   aapt2 -> javac -> d8 -> zip -> apksigner
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File build_apk.ps1 -SdkRoot <path> -OutDir <path>
param(
    [string]$SdkRoot = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$BuildTools = "35.0.0",
    [string]$OutDir = ".",
    [string]$KeyStore = "",
    [string]$KeyAlias = "wifisentinel",
    [string]$KeyPass = "android"
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$bt = Join-Path $SdkRoot "build-tools\$BuildTools"
$platform = Join-Path $SdkRoot "platforms\android-34"
$androidJar = Join-Path $platform "android.jar"

if (-not (Test-Path $androidJar)) {
    throw "android.jar not found at $androidJar. Run bootstrap_sdk.ps1 first."
}

$work = Join-Path $root "build\work"
if (Test-Path $work) { Remove-Item $work -Recurse -Force }
New-Item -ItemType Directory -Path $work -Force | Out-Null
$obj = Join-Path $work "obj"
New-Item -ItemType Directory -Path $obj -Force | Out-Null

$srcDir = Join-Path $root "app\src\main\java"
$resDir = Join-Path $root "app\src\main\res"
$assetsDir = Join-Path $root "app\src\main\assets"
$manifest = Join-Path $root "app\src\main\AndroidManifest.xml"

Write-Host "=== [1/6] aapt2 compile ==="
$resOut = Join-Path $work "res"
New-Item -ItemType Directory -Path $resOut -Force | Out-Null
& (Join-Path $bt "aapt2.exe") compile --dir $resDir -o (Join-Path $resOut "compiled.zip")
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

Write-Host "=== [2/6] aapt2 link ==="
$apkUnlinked = Join-Path $work "app_unlinked.apk"
$RJava = Join-Path $obj "R.java"
& (Join-Path $bt "aapt2.exe") link -o $apkUnlinked `
    -I $androidJar `
    --manifest $manifest `
    --java $obj `
    --auto-add-overlay `
    --min-sdk-version 24 `
    --target-sdk-version 34 `
    (Join-Path $resOut "*.flat")
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

Write-Host "=== [3/6] javac ==="
$javaFiles = Get-ChildItem -Path $srcDir -Filter "*.java" -Recurse | ForEach-Object { $_.FullName }
$javaFiles += (Join-Path $obj "R.java")
$javacArgs = @(
    "-source", "11", "-target", "11",
    "-classpath", $androidJar,
    "-d", $obj
) + $javaFiles
& (Join-Path $env:JAVA_HOME "bin\javac.exe") $javacArgs
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Write-Host "=== [4/6] d8 (desugar to DEX) ==="
$classesDir = $obj
$dexDir = Join-Path $work "dex"
New-Item -ItemType Directory -Path $dexDir -Force | Out-Null
$classFiles = Get-ChildItem -Path $classesDir -Filter "*.class" -Recurse | ForEach-Object { $_.FullName }
& (Join-Path $bt "d8.exe") `
    "--lib", $androidJar `
    "--min-api", "24" `
    "--output", $dexDir `
    $classFiles
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }
$classesDex = Join-Path $dexDir "classes.dex"
if (-not (Test-Path $classesDex)) { throw "classes.dex not produced" }

Write-Host "=== [5/6] Assemble APK ==="
$apkUnsigned = Join-Path $work "app_unsigned.apk"
Copy-Item $apkUnlinked $apkUnsigned -Force

# Add classes.dex to the APK.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open($apkUnsigned, 'Update')
[void]$zip.CreateEntryFromFile($classesDex, "classes.dex")
$zip.Dispose()

# Add assets (oui.txt) if present.
if (Test-Path $assetsDir) {
    $zip = [System.IO.Compression.ZipFile]::Open($apkUnsigned, 'Update')
    Get-ChildItem $assetsDir -File | ForEach-Object {
        $entryName = "assets\" + $_.Name
        [void]$zip.CreateEntryFromFile($_.FullName, $entryName)
    }
    $zip.Dispose()
}

# Recompress with aapt2 for proper alignment + resources.
& (Join-Path $bt "aapt2.exe") compile --help > $null 2>&1  # ensure aapt2 on PATH

Write-Host "=== [6/6] Align + Sign ==="
$apkAligned = Join-Path $work "app_aligned.apk"
# zipalign
$zipalign = Join-Path $bt "zipalign.exe"
if (Test-Path $zipalign) {
    & $zipalign -f 4 $apkUnsigned $apkAligned
} else {
    Copy-Item $apkUnsigned $apkAligned -Force
}

# Generate a keystore if none provided.
if (-not $KeyStore -or -not (Test-Path $KeyStore)) {
    $KeyStore = Join-Path $root "build\wifisentinel.keystore"
    if (-not (Test-Path $KeyStore)) {
        Write-Host "Generating debug keystore..."
        & (Join-Path $env:JAVA_HOME "bin\keytool.exe") -genkeypair `
            -keystore $KeyStore -alias $KeyAlias -keypass $KeyPass -storepass $KeyPass `
            -dname "CN=Wi-Fi Sentinel, OU=Dev, O=Cadmus, L=Local, ST=Local, C=GB" `
            -keyalg RSA -keysize 2048 -validity 10000
    }
}

$apkSigned = Join-Path $OutDir "wifisentinel.apk"
$apksigner = Join-Path $bt "apksigner.bat"
if (Test-Path $apksigner) {
    & $apksigner sign `
        --ks $KeyStore --ks-key-alias $KeyAlias --ks-pass "pass:$KeyPass" --key-pass "pass:$KeyPass" `
        --out $apkSigned $apkAligned
} else {
    # Fallback: jarsigner
    & (Join-Path $env:JAVA_HOME "bin\jarsigner.exe") -keystore $KeyStore -storepass $KeyPass $apkAligned $KeyAlias
    Copy-Item $apkAligned $apkSigned -Force
}

if ($LASTEXITCODE -ne 0) { throw "signing failed" }

Write-Host ""
Write-Host "=== BUILD COMPLETE ==="
Write-Host "APK: $apkSigned"
Write-Host "Size: $((Get-Item $apkSigned).Length) bytes"
