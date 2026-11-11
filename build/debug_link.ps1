$sdkRoot = "$env:LOCALAPPDATA\Android\Sdk"
$bt = Join-Path $sdkRoot "build-tools\34.0.0"
$androidJar = Join-Path $sdkRoot "platforms\android-34\android.jar"
$proj = "C:\Users\viper\Projects\sentinel"
$work = Join-Path $proj "build\work"
$manifest = Join-Path $proj "app\src\main\AndroidManifest.xml"
$resOut = Join-Path $work "res"
$obj = Join-Path $work "obj"

Write-Output "--- flat files in resOut ---"
$flats = Get-ChildItem $resOut -Filter "*.flat" -ErrorAction SilentlyContinue
Write-Output ("count: " + $flats.Count)
$flats | ForEach-Object { Write-Output $_.Name }

Write-Output "--- obj dir before link ---"
Get-ChildItem $obj -Recurse -ErrorAction SilentlyContinue | Select-Object FullName | Format-Table -AutoSize

Write-Output "--- run aapt2 link with -v ---"
$flatArgs = $flats | ForEach-Object { $_.FullName }
$apkUnlinked = Join-Path $work "app_unlinked2.apk"
& (Join-Path $bt "aapt2.exe") link -v -o $apkUnlinked -I $androidJar --manifest $manifest --java $obj --auto-add-overlay --min-sdk-version 24 --target-sdk-version 34 $flatArgs
Write-Output "link exit: $LASTEXITCODE"

Write-Output "--- obj dir after link ---"
Get-ChildItem $obj -Recurse -ErrorAction SilentlyContinue | Select-Object FullName | Format-Table -AutoSize
Write-Output "--- R.java exists? ---"
Test-Path (Join-Path $obj "R.java")
