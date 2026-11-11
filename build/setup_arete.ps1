# setup_arete.ps1 - run on Cadmus Arete: extract source, bootstrap SDK, build APK
$ErrorActionPreference = "Continue"
$proj = "C:\Users\viper\Projects\sentinel"
$sdkRoot = "$env:LOCALAPPDATA\Android\Sdk"
$btVer = "34.0.0"

Write-Output "=== STEP 1: Extract source ==="
if (Test-Path $proj) { Remove-Item $proj -Recurse -Force }
New-Item -ItemType Directory -Path $proj -Force | Out-Null
$tar = "C:\Users\viper\Projects\sentinel-src.tar.gz"
if (-not (Test-Path $tar)) { Write-Output "ERROR: tarball missing"; exit 1 }
tar -xzf $tar -C $proj
Write-Output ("Extracted files: " + (Get-ChildItem $proj -Recurse -File | Measure-Object).Count)

Write-Output ""
Write-Output "=== STEP 2: Bootstrap SDK ==="
$toolsDir = Join-Path $sdkRoot "cmdline-tools"
$latestBin = Join-Path $toolsDir "latest\bin\sdkmanager.bat"
if (-not (Test-Path $latestBin)) {
    Write-Output "Downloading cmdline-tools..."
    $zip = Join-Path $env:TEMP "cmdtools.zip"
    Invoke-WebRequest -Uri "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip" -OutFile $zip -UseBasicParsing
    New-Item -ItemType Directory -Path $toolsDir -Force | Out-Null
    Expand-Archive -Path $zip -DestinationPath $toolsDir -Force
    Remove-Item $zip -Force
    # Ensure latest\bin exists
    if (-not (Test-Path $latestBin)) {
        $top = Get-ChildItem $toolsDir -Directory | Select-Object -First 1
        if ($top) {
            $inner = Join-Path $top.FullName "latest"
            if (Test-Path $inner) {
                if (Test-Path (Join-Path $toolsDir "latest")) { Remove-Item (Join-Path $toolsDir "latest") -Recurse -Force }
                Move-Item $inner (Join-Path $toolsDir "latest")
            }
        }
    }
    Write-Output ("sdkmanager present: " + (Test-Path $latestBin))
}

Write-Output "Installing build-tools + platform..."
$yes = Join-Path $env:TEMP "sdk_yes.txt"
Set-Content $yes ("y`n" * 30) -Encoding ASCII
$proc = Start-Process -FilePath $latestBin -ArgumentList "--sdk_root=$sdkRoot", "build-tools;$btVer", "platforms;android-34" -NoNewWindow -PassThru -RedirectStandardInput $yes
$proc.WaitForExit()
Write-Output ("sdkmanager exit: " + $proc.ExitCode)

Write-Output ""
Write-Output "=== STEP 3: Build APK ==="
$bt = Join-Path $sdkRoot "build-tools\$btVer"
$androidJar = Join-Path $sdkRoot "platforms\android-34\android.jar"
if (-not (Test-Path $androidJar)) { Write-Output "ERROR: android.jar missing"; exit 2 }

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
& (Join-Path $bt "aapt2.exe") compile --dir $resDir -o (Join-Path $resOut "compiled.zip")
if ($LASTEXITCODE -ne 0) { Write-Output "AAPT2 COMPILE FAILED"; exit 3 }

Write-Output "--- aapt2 link ---"
$apkUnlinked = Join-Path $work "app_unlinked.apk"
& (Join-Path $bt "aapt2.exe") link -o $apkUnlinked -I $androidJar --manifest $manifest --java $obj --auto-add-overlay --min-sdk-version 24 --target-sdk-version 34 (Join-Path $resOut "*.flat")
if ($LASTEXITCODE -ne 0) { Write-Output "AAPT2 LINK FAILED"; exit 4 }

Write-Output "--- javac ---"
$javaFiles = (Get-ChildItem $srcDir -Filter "*.java" -Recurse | ForEach-Object { $_.FullName }) + (Join-Path $obj "R.java")
& (Join-Path $env:JAVA_HOME "bin\javac.exe") -source 11 -target 11 -classpath $androidJar -d $obj $javaFiles
if ($LASTEXITCODE -ne 0) { Write-Output "JAVAC FAILED"; exit 5 }

Write-Output "--- d8 ---"
$dexDir = Join-Path $work "dex"
New-Item -ItemType Directory -Path $dexDir -Force | Out-Null
$classFiles = Get-ChildItem $obj -Filter "*.class" -Recurse | ForEach-Object { $_.FullName }
& (Join-Path $bt "d8.exe") --lib $androidJar --min-api 24 --output $dexDir $classFiles
if ($LASTEXITCODE -ne 0) { Write-Output "D8 FAILED"; exit 6 }

Write-Output "--- assemble APK ---"
$apkUnsigned = Join-Path $work "app_unsigned.apk"
Copy-Item $apkUnlinked $apkUnsigned -Force
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zipObj = [System.IO.Compression.ZipFile]::Open($apkUnsigned, 'Update')
[void]$zipObj.CreateEntryFromFile((Join-Path $dexDir "classes.dex"), "classes.dex")
Get-ChildItem $assetsDir -File | ForEach-Object {
    [void]$zipObj.CreateEntryFromFile($_.FullName, "assets\" + $_.Name)
}
$zipObj.Dispose()

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
